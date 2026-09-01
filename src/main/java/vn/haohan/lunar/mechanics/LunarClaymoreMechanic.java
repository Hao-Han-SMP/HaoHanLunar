package vn.haohan.lunar.mechanics;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.visual.BlockWaveRenderer;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles custom mechanics, mace-like ground slam with block ripple wave,
 * Mace enchantments (Density, Breach, Wind Burst), and localized crescent blade wave
 * for the Lunar Claymore (Thanh Kiếm Nguyệt Thạch).
 */
public class LunarClaymoreMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private static final int CMD_IDLE = 6001;
    private static final long CRESCENT_COOLDOWN_MS = 3500L; // 3.5s internal cooldown

    private static final Particle.DustOptions LUNAR_CYAN = new Particle.DustOptions(Color.fromRGB(90, 225, 255), 1.6f);
    private static final Particle.DustOptions LUNAR_WHITE = new Particle.DustOptions(Color.fromRGB(240, 250, 255), 1.4f);

    private final Map<UUID, Long> lastSlashTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerComboState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> crescentCooldowns = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public LunarClaymoreMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isLunarClaymore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        try {
            var itemService = HaoHanItemCore.get().getItemService();
            if (itemService != null && itemService.isItem(item, "haohan:lunar_claymore")) {
                return true;
            }
        } catch (Throwable ignored) {}

        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd == CMD_IDLE) {
                return true;
            }
        }
        if (meta.hasItemModel()) {
            NamespacedKey key = meta.getItemModel();
            if (key != null && key.getKey().startsWith("claymore")) {
                return true;
            }
        }
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name.contains("Lunar Claymore") || name.contains("Nguyệt Thạch")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMaceEnchantment(Enchantment ench) {
        if (ench == null) return false;
        String key = ench.getKey().getKey().toLowerCase();
        return key.equals("density") || key.equals("breach") || key.equals("wind_burst");
    }

    public static boolean hasAnyMaceEnchant(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            return bookMeta.getStoredEnchants().keySet().stream().anyMatch(LunarClaymoreMechanic::isMaceEnchantment);
        }
        return meta.getEnchants().keySet().stream().anyMatch(LunarClaymoreMechanic::isMaceEnchantment);
    }

    public static int getMaceEnchantLevel(ItemStack item, String enchKey) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            if (entry.getKey().getKey().getKey().equalsIgnoreCase(enchKey)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private static Map<Enchantment, Integer> getStoredOrItemEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Map.of();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            return bookMeta.getStoredEnchants();
        }
        return meta.getEnchants();
    }

    private static int getEnchantCostWeight(Enchantment ench) {
        if (ench == null) return 1;
        String key = ench.getKey().getKey().toLowerCase();
        return switch (key) {
            case "wind_burst" -> 4;
            case "breach", "density" -> 2;
            default -> 1;
        };
    }

    /**
     * Anvil support: allows Mace enchantments on Lunar Claymore and prevents normal swords from getting them.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();

        if (first == null || first.getType() == Material.AIR) return;

        boolean isLunar = isLunarClaymore(first);

        // 1. Strictly block Mace enchantments from applying to or staying on normal Netherite swords / other weapons
        if (!isLunar) {
            ItemStack currentRes = event.getResult();
            if (currentRes != null && hasAnyMaceEnchant(currentRes)) {
                event.setResult(null);
            }
            if (second != null && hasAnyMaceEnchant(second)) {
                event.setResult(null);
            }
            return;
        }

        // 2. First item is Lunar Claymore!
        if (second == null || second.getType() == Material.AIR) return;

        Map<Enchantment, Integer> incomingEnchants = getStoredOrItemEnchants(second);
        if (incomingEnchants.isEmpty()) return;

        boolean hasMace = incomingEnchants.keySet().stream().anyMatch(LunarClaymoreMechanic::isMaceEnchantment);

        ItemStack base = (event.getResult() != null && event.getResult().getType() != Material.AIR)
                         ? event.getResult().clone()
                         : first.clone();
        ItemMeta meta = base.getItemMeta();
        if (meta == null) return;

        Map<Enchantment, Integer> currentEnchants = new HashMap<>(meta.getEnchants());
        int extraCost = 0;
        boolean modified = false;

        for (Map.Entry<Enchantment, Integer> entry : incomingEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int inLevel = entry.getValue();

            boolean isMace = isMaceEnchantment(ench);
            boolean canApply = isMace || ench.canEnchantItem(first);

            if (!canApply) continue;

            int curLevel = currentEnchants.getOrDefault(ench, 0);
            int finalLevel;

            if (curLevel == inLevel) {
                finalLevel = Math.min(ench.getMaxLevel(), curLevel + 1);
            } else {
                finalLevel = Math.max(curLevel, inLevel);
            }

            if (finalLevel > curLevel || curLevel == 0) {
                currentEnchants.put(ench, finalLevel);
                meta.addEnchant(ench, finalLevel, true);
                modified = true;
                extraCost += finalLevel * getEnchantCostWeight(ench);
            }
        }

        if (modified || hasMace) {
            String renameText = null;
            try {
                renameText = event.getView().getRenameText();
            } catch (Throwable ignored) {}

            if (renameText != null && !renameText.isBlank()) {
                meta.displayName(Component.text(renameText));
            }

            base.setItemMeta(meta);
            event.setResult(base);

            int currentCost = 1;
            try {
                currentCost = event.getView().getRepairCost();
            } catch (Throwable ignored) {}

            final int finalCost = Math.max(1, currentCost + extraCost);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    event.getView().setRepairCost(finalCost);
                } catch (Throwable ignored) {}
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() instanceof AnvilInventory) {
            if (event.getSlotType() == InventoryType.SlotType.RESULT) {
                ItemStack result = event.getCurrentItem();
                if (result != null && hasAnyMaceEnchant(result) && !isLunarClaymore(result)) {
                    event.setCancelled(true);
                    event.setCurrentItem(null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        Action action = event.getAction();

        // 1. RIGHT CLICK: Play vanilla swing animation & Launch Crescent Blade Wave
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            player.swingHand(event.getHand()); // Trigger vanilla swing animation
            triggerCrescentBladeWave(player);
            return;
        }

        // 2. LEFT CLICK: Basic slash sound & 1-sweep particle visual
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            performSlashEffect(player, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        Entity target = event.getEntity();
        World world = target.getWorld();
        float fallDist = player.getFallDistance();

        // 3. MACE SMASH MECHANIC: If player hits an entity while falling from high ground
        if (fallDist > 1.2f || (player.getVelocity().getY() < -0.15 && !player.isOnGround())) {
            double bonusDamage = Math.min(Math.max(fallDist, 1.5) * 4.5, 55.0);

            // Density enchantment (minecraft:density): increases slam damage per block fallen
            int densityLvl = getMaceEnchantLevel(item, "density");
            if (densityLvl > 0) {
                double densityBonus = Math.max(fallDist, 1.0) * (densityLvl * 1.5);
                bonusDamage += densityBonus;
            }

            // Breach enchantment (minecraft:breach): penetrates target's armor
            int breachLvl = getMaceEnchantLevel(item, "breach");
            if (breachLvl > 0 && target instanceof LivingEntity livingTarget) {
                double armor = 0;
                var attr = livingTarget.getAttribute(Attribute.ARMOR);
                if (attr != null) armor = attr.getValue();
                if (armor > 0) {
                    double armorPenetration = 0.15 * breachLvl; // 15% - 60%
                    double breachBonus = Math.min(armor * armorPenetration * 1.2, 24.0);
                    bonusDamage += breachBonus;
                }
            }

            event.setDamage(event.getDamage() + bonusDamage);

            // Cancel fall damage
            player.setFallDistance(0.0f);

            // Wind Burst enchantment (minecraft:wind_burst): launches player into the air on smash hit
            int windBurstLvl = getMaceEnchantLevel(item, "wind_burst");
            if (windBurstLvl > 0) {
                Vector vel = player.getVelocity();
                vel.setY(0.70 + (windBurstLvl * 0.25));
                player.setVelocity(vel);

                Location impactLoc = target.getLocation().add(0, 0.5, 0);
                world.playSound(impactLoc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.PLAYERS, 1.6f, 1.0f);
                world.playSound(impactLoc, Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.3f, 1.2f);
                try {
                    world.spawnParticle(Particle.valueOf("GUST_EMITTER_LARGE"), impactLoc, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.valueOf("GUST"), impactLoc, 8, 0.5, 0.5, 0.5, 0.05);
                } catch (Throwable ignored) {
                    world.spawnParticle(Particle.EXPLOSION, impactLoc, 2, 0.2, 0.2, 0.2, 0);
                    world.spawnParticle(Particle.CLOUD, impactLoc, 12, 0.4, 0.4, 0.4, 0.1);
                }
            } else {
                // Default mace upward bounce recoil
                Vector vel = player.getVelocity();
                vel.setY(0.48);
                player.setVelocity(vel);
            }

            // Ground slam impact effect with block ripple waves at target location
            Location impactLoc = target.getLocation();
            performSwordSlamImpact(player, impactLoc, bonusDamage, target);
        } else {
            // Normal ground attack: Breach still gives armor penetration bonus
            int breachLvl = getMaceEnchantLevel(item, "breach");
            if (breachLvl > 0 && target instanceof LivingEntity livingTarget) {
                double armor = 0;
                var attr = livingTarget.getAttribute(Attribute.ARMOR);
                if (attr != null) armor = attr.getValue();
                if (armor > 0) {
                    double breachBonus = Math.min(armor * (0.15 * breachLvl) * 0.6, 12.0);
                    event.setDamage(event.getDamage() + breachBonus);
                }
            }
            performSlashEffect(player, target);
        }
    }

    private void performSlashEffect(Player player, Entity primaryTarget) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        long lastTime = lastSlashTime.getOrDefault(uuid, 0L);
        if (now - lastTime < 350L) {
            return;
        }

        boolean lastWasRight = playerComboState.getOrDefault(uuid, false);
        boolean isRightSlash = (now - lastTime < 1400L) ? !lastWasRight : true;

        playerComboState.put(uuid, isRightSlash);
        lastSlashTime.put(uuid, now);

        Location loc = player.getLocation();
        World world = loc.getWorld();

        if (world != null) {
            world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, isRightSlash ? 0.9f : 1.1f);
            world.playSound(loc, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.6f, 1.8f);
            WardenAudio.playCustomSound(loc, "haohan:weapon.claymore_swing", 1.0f, isRightSlash ? 1.0f : 1.15f);

            spawnSingleSweepParticle(player);
            performLunarCleave(player, primaryTarget);
        }
    }

    private void spawnSingleSweepParticle(Player player) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World world = eyeLoc.getWorld();
        if (world == null) return;

        Location center = eyeLoc.clone().add(dir.multiply(2.2));
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0, 0, 0, 0);
    }

    private void performLunarCleave(Player player, Entity primaryTarget) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();

        for (Entity entity : player.getNearbyEntities(3.5, 3.5, 3.5)) {
            if (!(entity instanceof LivingEntity target) || entity == player || entity == primaryTarget) {
                continue;
            }
            Vector toTarget = target.getLocation().add(0, 1, 0).toVector().subtract(eyeLoc.toVector()).normalize();
            if (dir.dot(toTarget) > 0.45) {
                target.damage(16.0, player);
                Vector knockback = dir.clone().setY(0.2).multiply(0.6);
                target.setVelocity(target.getVelocity().add(knockback));
            }
        }
    }

    /**
     * Sword Slam impact effect, Block Display ripple waves, and AOE shockwave.
     */
    private void performSwordSlamImpact(Player player, Location impactLoc, double slamDamage, Entity primaryTarget) {
        World world = impactLoc.getWorld();
        if (world == null) return;

        // Sounds: Heavy ground slam & shockwave
        world.playSound(impactLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 2.0f, 0.85f);
        world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.5f, 0.8f);
        world.playSound(impactLoc, Sound.BLOCK_ROOTED_DIRT_BREAK, SoundCategory.PLAYERS, 1.8f, 0.6f);
        WardenAudio.playCustomSound(impactLoc, "haohan:boss.ground_slam_heavy", 2.0f, 0.95f);

        // Explosion + Shattered Rock particles
        world.spawnParticle(Particle.EXPLOSION, impactLoc.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0);
        world.spawnParticle(Particle.BLOCK, impactLoc.clone().add(0, 0.2, 0), 75, 1.4, 0.4, 1.4, Material.STONE.createBlockData());
        world.spawnParticle(Particle.SWEEP_ATTACK, impactLoc.clone().add(0, 0.5, 0), 3, 0.8, 0.1, 0.8, 0.0);

        // Trigger Block Ripple Wave around impact point (bề mặt block bị đập nảy lên theo gợn sóng)
        triggerGroundBlockRipple(impactLoc);

        // Circular Shockwave expansion ring
        int points = 24;
        double radius = 4.2;
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location pLoc = impactLoc.clone().add(x, 0.3, z);
            world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, LUNAR_CYAN);
            world.spawnParticle(Particle.ELECTRIC_SPARK, pLoc, 1, 0.05, 0.05, 0.05, 0.05);
        }

        // AOE Shockwave Damage & Knockback to surrounding entities
        double aoeRadius = 5.0;
        double aoeDmg = Math.max(10.0, slamDamage * 0.65);

        for (Entity nearby : player.getNearbyEntities(aoeRadius, 3.0, aoeRadius)) {
            if (!(nearby instanceof LivingEntity living) || nearby == player || nearby == primaryTarget) {
                continue;
            }
            living.damage(aoeDmg, player);
            Vector knockback = nearby.getLocation().toVector().subtract(impactLoc.toVector()).setY(0).normalize().multiply(0.85).setY(0.42);
            nearby.setVelocity(nearby.getVelocity().add(knockback));
        }
    }

    /**
     * Spawns ground block ripple wave popping up from the ground in concentric rings with smooth easing.
     */
    private void triggerGroundBlockRipple(Location slamCenter) {
        BlockWaveRenderer.spawnConcentricWave(
                plugin,
                slamCenter,
                4.8,
                1.30,
                0.70,
                13,
                0.28,
                random
        );
    }

    /**
     * Right-click ability: Launches a short-range localized Crescent Blade Wave (Kiếm Khí Bán Nguyệt).
     */
    private void triggerCrescentBladeWave(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastUsed = crescentCooldowns.getOrDefault(uuid, 0L);

        // Internal per-player cooldown (No Netherite sword visual cooldown applied to regular swords)
        if (now - lastUsed < CRESCENT_COOLDOWN_MS) {
            double remainingSec = Math.ceil((CRESCENT_COOLDOWN_MS - (now - lastUsed)) / 100.0) / 10.0;
            player.sendActionBar(Component.text("§c⏳ Kiếm Khí đang hồi chiêu (" + remainingSec + "s)..."));
            return;
        }

        crescentCooldowns.put(uuid, now);
        player.sendActionBar(Component.text("§b✦ Kiếm Khí Nguyệt Thạch!"));

        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().setY(0).normalize();
        if (dir.lengthSquared() < 0.01) {
            dir = player.getLocation().getDirection().setY(0).normalize();
        }

        World world = player.getWorld();
        Location startLoc = player.getLocation().add(0, 0.5, 0);

        // Sound effects
        world.playSound(startLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4f, 0.8f);
        world.playSound(startLoc, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 0.8f, 1.5f);
        WardenAudio.playCustomSound(startLoc, "haohan:boss.arcslash", 1.4f, 1.2f);

        // Shorter, punchier blade wave
        new PlayerCrescentBladeWaveTask(player, startLoc, dir).runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Projectile task for short-range Crescent Blade Wave (~10 blocks).
     */
    private static class PlayerCrescentBladeWaveTask extends BukkitRunnable {
        private final Player player;
        private final Location current;
        private final Vector dir;
        private final Vector cross;
        private final Set<UUID> hitTargets = new HashSet<>();
        private int step = 0;
        private static final int MAX_STEPS = 8; // Reduced range: 8 steps (~10 blocks)

        private static final double ARC_RADIUS = 2.4; // Tighter crescent arc
        private static final double MAX_ANGLE = Math.toRadians(65.0);
        private static final int ARC_POINTS = 9;

        public PlayerCrescentBladeWaveTask(Player player, Location origin, Vector direction) {
            this.player = player;
            this.current = origin.clone();
            this.dir = direction.clone().setY(0).normalize();
            this.cross = new Vector(-this.dir.getZ(), 0, this.dir.getX()).normalize();
        }

        @Override
        public void run() {
            if (!player.isOnline() || current.getWorld() == null || step >= MAX_STEPS) {
                cancel();
                return;
            }

            step++;
            current.add(dir.clone().multiply(1.25));

            World world = current.getWorld();
            Location center = current.clone();
            Location circleCenter = center.clone().subtract(dir.clone().multiply(ARC_RADIUS));

            List<Location> arcPoints = new ArrayList<>(ARC_POINTS);

            for (int i = 0; i < ARC_POINTS; i++) {
                double progress = (double) i / (double) (ARC_POINTS - 1);
                double angle = -MAX_ANGLE + (progress * 2.0 * MAX_ANGLE);

                double cos = Math.cos(angle);
                double sin = Math.sin(angle);

                Vector offset = dir.clone().multiply(ARC_RADIUS * cos)
                        .add(cross.clone().multiply(ARC_RADIUS * sin));

                Location pt = circleCenter.clone().add(offset).add(0, 0.4, 0);
                arcPoints.add(pt);

                // Sharp crescent sweep particles
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, pt, 1, 0, 0, 0, 0);
                }

                // Glowing lunar energy dust
                world.spawnParticle(Particle.DUST, pt, 1, 0.04, 0.06, 0.04, 0.0, (i % 2 == 0) ? LUNAR_WHITE : LUNAR_CYAN);

                // Electric sparks at edges and peak
                if (i == 0 || i == ARC_POINTS - 1 || i == ARC_POINTS / 2) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, pt, 1, 0.04, 0.04, 0.04, 0.04);
                }
            }

            // Sound along wave path
            if (step % 2 == 0) {
                world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
            }

            // Deal damage and knockback to enemies hit by the wave
            for (Entity entity : world.getNearbyEntities(center, 3.2, 2.5, 3.2)) {
                if (!(entity instanceof LivingEntity target) || entity == player || target.isDead()) {
                    continue;
                }
                if (hitTargets.contains(target.getUniqueId())) {
                    continue;
                }

                Location tLoc = target.getLocation();
                boolean isHit = false;
                for (Location pt : arcPoints) {
                    if (tLoc.distanceSquared(pt) <= 1.8 * 1.8) {
                        isHit = true;
                        break;
                    }
                }

                if (isHit) {
                    hitTargets.add(target.getUniqueId());
                    target.damage(16.0, player);
                    Vector knockback = dir.clone().multiply(0.75).setY(0.35);
                    target.setVelocity(target.getVelocity().add(knockback));
                    world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);
                    world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 1.2f);
                }
            }
        }
    }
}
