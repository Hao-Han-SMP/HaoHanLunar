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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.visual.BlockWaveRenderer;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.weapon.claymore.SmoothSlashTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles custom mechanics, mace-like ground slam with block ripple wave,
 * Mace enchantments (Density, Breach, Wind Burst), smooth slash animation and melee cleave,
 * defensive guard block (Block_Sword), and anti-loss inventory protection for the Lunar Claymore (Thanh Kiếm Nguyệt Thạch).
 */
public class LunarClaymoreMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private static final int CMD_IDLE = 6001;
    private static final int CMD_SLASHING = 6002;

    private static final Particle.DustOptions LUNAR_CYAN = new Particle.DustOptions(Color.fromRGB(90, 225, 255), 1.6f);

    // ThreadLocal flag to allow custom programmatic damage to bypass the vanilla attack cancellation
    private static final ThreadLocal<Boolean> IS_APPLYING_CUSTOM_DAMAGE = ThreadLocal.withInitial(() -> false);

    private final Map<UUID, Long> lastSlashTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerComboStep = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> consecutiveSlashesCount = new ConcurrentHashMap<>();
    private final Map<UUID, SmoothSlashTask.SlashType> lastSlashType = new ConcurrentHashMap<>();
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
            if (cmd == CMD_IDLE || cmd == CMD_SLASHING) {
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
            return name.contains("Lunar Claymore") || name.contains("Nguyệt Thạch");
        }
        return false;
    }

    /**
     * Safely applies custom damage from the Lunar Claymore without getting cancelled
     * by the onEntityDamage listener.
     */
    public static void applyCustomDamage(Damageable target, Player damager, double damage) {
        if (target == null || damager == null || target.isDead()) return;
        if (target instanceof LivingEntity living) {
            living.setNoDamageTicks(0);
        }
        try {
            IS_APPLYING_CUSTOM_DAMAGE.set(true);
            target.damage(damage, damager);
        } finally {
            IS_APPLYING_CUSTOM_DAMAGE.set(false);
        }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();

        if (first == null || first.getType() == Material.AIR) return;

        boolean isLunar = isLunarClaymore(first);

        if (!isLunar) {
            ItemStack currentRes = event.getResult();
            if (hasAnyMaceEnchant(currentRes)) {
                event.setResult(null);
            }
            if (hasAnyMaceEnchant(second)) {
                event.setResult(null);
            }
            return;
        }

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
                if (hasAnyMaceEnchant(result) && !isLunarClaymore(result)) {
                    event.setCancelled(true);
                    event.setCurrentItem(null);
                }
            }
        }
    }

    // ==========================================
    // INVENTORY SAFETY LISTENERS DURING SLASH
    // ==========================================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (SmoothSlashTask.isSlashing(player)) {
            SmoothSlashTask.stopSlashing(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickDuringSlash(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && SmoothSlashTask.isSlashing(player)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDragDuringSlash(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && SmoothSlashTask.isSlashing(player)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (SmoothSlashTask.isSlashing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        if (SmoothSlashTask.isSlashing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpenDuringSlash(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && SmoothSlashTask.isSlashing(player)) {
            SmoothSlashTask.stopSlashing(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPickupItemDuringSlash(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && SmoothSlashTask.isSlashing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SmoothSlashTask.stopSlashing(player);
        lastSlashType.remove(player.getUniqueId());
        playerComboStep.remove(player.getUniqueId());
        consecutiveSlashesCount.remove(player.getUniqueId());
        lastSlashTime.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        SmoothSlashTask.stopSlashing(player);
        lastSlashType.remove(player.getUniqueId());
        playerComboStep.remove(player.getUniqueId());
        consecutiveSlashesCount.remove(player.getUniqueId());
        lastSlashTime.remove(player.getUniqueId());
    }

    // ==========================================
    // WEAPON MECHANIC LISTENERS
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        Action action = event.getAction();

        // 1. RIGHT CLICK: Defensive Block Guard Mechanic ("Block_Sword")
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (SmoothSlashTask.isPlayerBlocking(player)) {
                return;
            }

            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            long lastTime = lastSlashTime.getOrDefault(uuid, 0L);

            if (now - lastTime < 400L) { // 0.4s cooldown
                return;
            }
            lastSlashTime.put(uuid, now);
            lastSlashType.put(uuid, SmoothSlashTask.SlashType.BLOCK);

            SmoothSlashTask.play(
                    plugin,
                    player,
                    item,
                    SmoothSlashTask.SlashType.BLOCK,
                    null,
                    null, // Đòn đỡ - không gây sát thương lên quái
                    executedType -> playSlashSound(player, executedType)
            );
            return;
        }

        // 2. LEFT CLICK: Basic slash sound & visual swing
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
            }
            performSlashEffect(player, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // If this event was triggered by our own custom damage application, let it through!
        if (IS_APPLYING_CUSTOM_DAMAGE.get()) {
            return;
        }

        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        Entity target = event.getEntity();
        World world = target.getWorld();
        float fallDist = player.getFallDistance();

        // 3. MACE SMASH MECHANIC: If player hits an entity while falling from high ground
        if (fallDist > 1.2f || (player.getVelocity().getY() < -0.15 && !player.isOnGround())) {
            double bonusDamage = Math.min(Math.max(fallDist, 1.5) * 4.5, 55.0);

            // Density enchantment (minecraft:density)
            int densityLvl = getMaceEnchantLevel(item, "density");
            if (densityLvl > 0) {
                double densityBonus = Math.max(fallDist, 1.0) * (densityLvl * 1.5);
                bonusDamage += densityBonus;
            }

            // Breach enchantment (minecraft:breach)
            int breachLvl = getMaceEnchantLevel(item, "breach");
            if (breachLvl > 0 && target instanceof LivingEntity livingTarget) {
                double armor = 0;
                var attr = livingTarget.getAttribute(Attribute.ARMOR);
                if (attr != null) armor = attr.getValue();
                if (armor > 0) {
                    double armorPenetration = 0.15 * breachLvl;
                    double breachBonus = Math.min(armor * armorPenetration * 1.2, 24.0);
                    bonusDamage += breachBonus;
                }
            }

            event.setDamage(event.getDamage() + bonusDamage);
            player.setFallDistance(0.0f);

            // Wind Burst enchantment (minecraft:wind_burst)
            int windBurstLvl = getMaceEnchantLevel(item, "wind_burst");
            Vector vel = player.getVelocity();
            if (windBurstLvl > 0) {
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
                vel.setY(0.48);
                player.setVelocity(vel);
            }

            Location impactLoc = target.getLocation();
            performSwordSlamImpact(player, impactLoc, bonusDamage, target);
        } else {
            // Normal ground attack: Cancel vanilla damage and vanilla sweep attack so custom attack takes full control!
            event.setDamage(0.0);
            event.setCancelled(true);
            performSlashEffect(player, target);
        }
    }

    /**
     * Intercepts incoming damage while the player is guarding with Block_Sword.
     * Blocks frontal attacks completely with sound & spark effects.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDefendWhileBlocking(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!SmoothSlashTask.isPlayerBlocking(player)) return;

        Entity damager = event.getDamager();
        Location playerLoc = player.getLocation();
        Vector playerDir = playerLoc.getDirection().setY(0).normalize();
        Vector toDamager = damager.getLocation().toVector().subtract(playerLoc.toVector()).setY(0);

        if (toDamager.lengthSquared() > 0.001) {
            toDamager.normalize();
            if (playerDir.dot(toDamager) < -0.2) {
                // Attacker is behind player; cannot block
                return;
            }
        }

        // Successfully blocked damage!
        event.setCancelled(true);

        World world = player.getWorld();
        Location blockLoc = player.getEyeLocation().add(playerDir.multiply(0.8)).subtract(0, 0.2, 0);

        world.playSound(blockLoc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.4f, 0.9f);
        world.playSound(blockLoc, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 1.6f);
        WardenAudio.playCustomSound(blockLoc, "haohan:weapon.claymore_swing", 0.8f, 1.4f);

        world.spawnParticle(Particle.CRIT, blockLoc, 10, 0.2, 0.2, 0.2, 0.2);
        world.spawnParticle(Particle.ELECTRIC_SPARK, blockLoc, 6, 0.15, 0.15, 0.15, 0.1);

        // Repel the attacker slightly
        Vector repel = toDamager.clone().multiply(0.4).setY(0.1);
        damager.setVelocity(damager.getVelocity().add(repel));
    }

    /**
     * Executes the fluid combo sequence:
     * Seamlessly chains into the active ModelEngine model or buffers input for rhythmic fluidity.
     */
    private void performSlashEffect(Player player, Entity primaryTarget) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        long lastTime = lastSlashTime.getOrDefault(uuid, 0L);
        // Minimum anti-spam interval (160ms = ~3 ticks) to allow instant buffering and snappy responsiveness
        if (now - lastTime < 160L) {
            return;
        }

        int combo = playerComboStep.getOrDefault(uuid, 0);
        int consecutiveSlashes = consecutiveSlashesCount.getOrDefault(uuid, 0);

        if (now - lastTime > 1200L) {
            combo = 0; // Reset combo counter if player paused longer than 1.2s
            consecutiveSlashes = 0;
            lastSlashType.remove(uuid);
        }

        SmoothSlashTask existing = SmoothSlashTask.getActiveTask(uuid);
        SmoothSlashTask.SlashType previous = null;
        String currentAnimName = null;
        if (existing != null && existing.isValid()) {
            previous = existing.getQueuedOrCurrentSlashType();
            currentAnimName = existing.getAnimationName();
        }
        if (previous == null) {
            previous = lastSlashType.get(uuid);
        }

        // Danh sách các đòn chém thường (loại trừ DOWNWARD và BLOCK):
        List<SmoothSlashTask.SlashType> normalCandidates = new ArrayList<>();
        for (SmoothSlashTask.SlashType candidate : SmoothSlashTask.SlashType.values()) {
            if (candidate == SmoothSlashTask.SlashType.DOWNWARD || candidate == SmoothSlashTask.SlashType.BLOCK || candidate == previous) {
                continue;
            }
            if (existing != null && existing.getActiveModel() != null && currentAnimName != null) {
                String candidateAnim = SmoothSlashTask.resolveAnimationNameCached(candidate, existing.getActiveModel());
                if (candidateAnim != null && candidateAnim.equalsIgnoreCase(currentAnimName)) {
                    continue; // Bỏ qua nếu ánh xạ tới đúng cùng một animation trong ModelEngine
                }
            }
            normalCandidates.add(candidate);
        }

        // Fallback dự phòng nếu bộ animation của model bị giới hạn
        if (normalCandidates.isEmpty()) {
            for (SmoothSlashTask.SlashType candidate : SmoothSlashTask.SlashType.values()) {
                if (candidate != SmoothSlashTask.SlashType.DOWNWARD && candidate != SmoothSlashTask.SlashType.BLOCK && candidate != previous) {
                    normalCandidates.add(candidate);
                }
            }
        }
        if (normalCandidates.isEmpty()) {
            normalCandidates.add(previous == SmoothSlashTask.SlashType.HORIZONTAL_LEFT
                    ? SmoothSlashTask.SlashType.HORIZONTAL_RIGHT
                    : SmoothSlashTask.SlashType.HORIZONTAL_LEFT);
        }

        SmoothSlashTask.SlashType type;
        // Đòn chặt (DOWNWARD) chỉ được xuất hiện sau ít nhất 5 lần chém liên tục
        // Và tỷ lệ xuất hiện giảm xuống còn khoảng 18% cơ hội
        boolean canChop = (consecutiveSlashes >= 5) && (previous != SmoothSlashTask.SlashType.DOWNWARD);
        if (canChop && random.nextFloat() < 0.18f) {
            type = SmoothSlashTask.SlashType.DOWNWARD;
            consecutiveSlashes = 0; // Reset để bắt đầu tích lũy lại ít nhất 5 lần chém mới có đòn chặt tiếp theo
        } else {
            type = normalCandidates.get(random.nextInt(normalCandidates.size()));
            consecutiveSlashes++;
        }

        consecutiveSlashesCount.put(uuid, consecutiveSlashes);
        lastSlashType.put(uuid, type);
        playerComboStep.put(uuid, combo + 1);
        lastSlashTime.put(uuid, now);

        SmoothSlashTask.play(
                plugin,
                player,
                player.getInventory().getItemInMainHand(),
                type,
                primaryTarget,
                (executedType, hitTarget) -> executeLunarAttack(player, hitTarget, executedType),
                executedType -> playSlashSound(player, executedType)
        );
    }

    private void playSlashSound(Player player, SmoothSlashTask.SlashType type) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        switch (type) {
            case BLOCK -> {
                world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_IRON, SoundCategory.PLAYERS, 1.0f, 1.0f);
                world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.9f, 1.2f);
            }
            case DOWNWARD -> {
                world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2f, 0.75f);
                world.playSound(loc, Sound.ITEM_MACE_SMASH_AIR, SoundCategory.PLAYERS, 1.1f, 1.1f);
                WardenAudio.playCustomSound(loc, "haohan:weapon.claymore_swing", 1.1f, 0.85f);
            }
            case STABBING_ATTACK -> {
                world.playSound(loc, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.1f, 1.6f);
                world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.9f, 1.3f);
                WardenAudio.playCustomSound(loc, "haohan:weapon.claymore_swing", 1.0f, 1.3f);
            }
            default -> {
                world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 1.0f);
                world.playSound(loc, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.6f, 1.8f);
                WardenAudio.playCustomSound(loc, "haohan:weapon.claymore_swing", 1.0f, 1.1f);
            }
        }
    }

    /**
     * Routes attack hit detection according to attack type:
     * - Left/Right slashes: Wide AoE cleave, slightly shorter range (3.2m), standard damage (16.0)
     * - Downward strike: High single-target damage (26.0), no AoE
     * - Stabbing attack: Furthest range (5.0m), standard single-target damage (16.0), no AoE
     * - Block: Defensive stance, no damage
     */
    private void executeLunarAttack(Player player, Entity primaryTarget, SmoothSlashTask.SlashType type) {
        switch (type) {
            case BLOCK -> {}
            case DOWNWARD -> executeDownwardStrike(player, primaryTarget);
            case STABBING_ATTACK -> executeStabbingAttack(player, primaryTarget);
            default -> executeSlashCleave(player, primaryTarget);
        }
    }

    /**
     * Left/Right Slashes: Wide AoE sweep cleave with slightly reduced range (~3.2m).
     */
    private void executeSlashCleave(Player player, Entity primaryTarget) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World world = player.getWorld();

        double slashRange = 3.2;
        Location sweepLoc = eyeLoc.clone().add(dir.clone().multiply(1.6)).subtract(0, 0.3, 0);
        world.playSound(sweepLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.3f, 0.95f);

        for (Entity entity : player.getNearbyEntities(slashRange, 2.5, slashRange)) {
            if (!(entity instanceof LivingEntity target) || entity == player || target.isDead()) {
                continue;
            }
            Vector toTarget = target.getLocation().add(0, 1, 0).toVector().subtract(eyeLoc.toVector());
            double dist = toTarget.length();
            if (dist > slashRange) {
                continue;
            }
            toTarget.normalize();
            if (dir.dot(toTarget) > 0.35) {
                applyCustomDamage(target, player, 16.0);
                Vector knockback = dir.clone().setY(0.25).multiply(0.60);
                target.setVelocity(target.getVelocity().add(knockback));
            }
        }
    }

    /**
     * Downward Strike: Heavy overhead chop dealing high damage (26.0) to a SINGLE target (NO AoE).
     */
    private void executeDownwardStrike(Player player, Entity primaryTarget) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World world = player.getWorld();

        LivingEntity targetToHit = null;

        if (primaryTarget instanceof LivingEntity living && living.isValid() && !living.isDead() && living != player) {
            if (eyeLoc.distance(living.getEyeLocation()) <= 3.4) {
                targetToHit = living;
            }
        }

        if (targetToHit == null) {
            RayTraceResult result = world.rayTraceEntities(
                    eyeLoc,
                    dir,
                    3.3,
                    0.50,
                    e -> e instanceof LivingEntity && e != player && !e.isDead() && !e.isInvulnerable()
            );
            if (result != null && result.getHitEntity() instanceof LivingEntity hit) {
                targetToHit = hit;
            } else {
                double bestDot = 0.70;
                for (Entity entity : player.getNearbyEntities(3.3, 2.5, 3.3)) {
                    if (!(entity instanceof LivingEntity candidate) || entity == player || candidate.isDead()) {
                        continue;
                    }
                    Vector toTarget = candidate.getEyeLocation().toVector().subtract(eyeLoc.toVector()).normalize();
                    double dot = dir.dot(toTarget);
                    if (dot > bestDot) {
                        bestDot = dot;
                        targetToHit = candidate;
                    }
                }
            }
        }

        Location strikeLoc = eyeLoc.clone().add(dir.clone().multiply(1.8));
        world.playSound(strikeLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.4f, 0.8f);
        world.playSound(strikeLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.0f, 1.3f);

        if (targetToHit != null) {
            applyCustomDamage(targetToHit, player, 26.0);

            Vector knockback = dir.clone().multiply(0.35).setY(-0.25);
            targetToHit.setVelocity(targetToHit.getVelocity().add(knockback));
        }
    }

    /**
     * Stabbing Attack: Forward piercing thrust with the furthest range (5.0m),
     * normal damage (16.0), and single target only (NO AoE).
     */
    private void executeStabbingAttack(Player player, Entity primaryTarget) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        World world = player.getWorld();

        LivingEntity targetToHit = null;

        RayTraceResult result = world.rayTraceEntities(
                eyeLoc,
                dir,
                5.0,
                0.45,
                e -> e instanceof LivingEntity && e != player && !e.isDead() && !e.isInvulnerable()
        );
        if (result != null && result.getHitEntity() instanceof LivingEntity hit) {
            targetToHit = hit;
        } else if (primaryTarget instanceof LivingEntity living && living.isValid() && !living.isDead() && living != player) {
            if (eyeLoc.distance(living.getLocation()) <= 5.0) {
                Vector toTarget = living.getEyeLocation().toVector().subtract(eyeLoc.toVector()).normalize();
                if (dir.dot(toTarget) > 0.60) {
                    targetToHit = living;
                }
            }
        }

        Location thrustTip = eyeLoc.clone().add(dir.clone().multiply(2.5));
        world.playSound(thrustTip, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.2f, 1.5f);
        world.playSound(thrustTip, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.1f, 1.2f);

        if (targetToHit != null) {
            applyCustomDamage(targetToHit, player, 16.0);

            Vector knockback = dir.clone().multiply(0.70).setY(0.15);
            targetToHit.setVelocity(targetToHit.getVelocity().add(knockback));
        }
    }

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

        // Trigger Block Ripple Wave
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

        double aoeRadius = 5.0;
        double aoeDmg = Math.max(10.0, slamDamage * 0.65);

        for (Entity nearby : player.getNearbyEntities(aoeRadius, 3.0, aoeRadius)) {
            if (!(nearby instanceof LivingEntity living) || nearby == player || nearby == primaryTarget) {
                continue;
            }
            applyCustomDamage(living, player, aoeDmg);
            Vector diff = nearby.getLocation().toVector().subtract(impactLoc.toVector()).setY(0);
            Vector knockback;
            if (diff.lengthSquared() > 0) {
                knockback = diff.normalize().multiply(0.85).setY(0.42);
            } else {
                knockback = new Vector(0, 0.42, 0);
            }
            Vector currVel = nearby.getVelocity();
            if (Double.isFinite(knockback.getX()) && Double.isFinite(knockback.getY()) && Double.isFinite(knockback.getZ())
                && Double.isFinite(currVel.getX()) && Double.isFinite(currVel.getY()) && Double.isFinite(currVel.getZ())) {
                nearby.setVelocity(currVel.add(knockback));
            }
        }
    }

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
}
