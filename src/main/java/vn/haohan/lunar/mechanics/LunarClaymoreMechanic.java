package vn.haohan.lunar.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LunarClaymoreMechanic implements Listener {

    private static final int CMD_IDLE = 6001;
    private static final int[] FRAMES_SLASH_RIGHT = {6011, 6012, 6013, 6014, 6015};
    private static final int[] FRAMES_SLASH_LEFT = {6021, 6022, 6023, 6024, 6025};

    private final HaoHanLunarPlugin plugin;
    private final Map<UUID, BukkitTask> activeAnimationTasks = new HashMap<>();
    private final Map<UUID, Boolean> playerComboState = new HashMap<>();
    private final Map<UUID, Long> lastSlashTime = new HashMap<>();

    public LunarClaymoreMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isLunarClaymore(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            if (cmd == CMD_IDLE || (cmd >= 6011 && cmd <= 6015) || (cmd >= 6021 && cmd <= 6025)) {
                return true;
            }
        }
        if (meta.hasItemModel()) {
            NamespacedKey key = meta.getItemModel();
            if (key != null && (key.getKey().startsWith("claymore") || key.getKey().startsWith("slash_"))) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        triggerSlashAnimation(player, item, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isLunarClaymore(item)) return;

        Entity target = event.getEntity();
        triggerSlashAnimation(player, item, target);
    }

    private void triggerSlashAnimation(Player player, ItemStack item, Entity primaryTarget) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (activeAnimationTasks.containsKey(uuid)) {
            return;
        }

        long lastTime = lastSlashTime.getOrDefault(uuid, 0L);
        boolean lastWasRight = playerComboState.getOrDefault(uuid, false);
        boolean isRightSlash = (now - lastTime < 1250L) ? !lastWasRight : true;

        playerComboState.put(uuid, isRightSlash);
        lastSlashTime.put(uuid, now);

        int[] frames = isRightSlash ? FRAMES_SLASH_RIGHT : FRAMES_SLASH_LEFT;
        Location loc = player.getLocation();
        World world = loc.getWorld();

        if (world != null) {
            world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, isRightSlash ? 0.9f : 1.1f);
            world.playSound(loc, Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.6f, 1.8f);
            WardenAudio.playCustomSound(loc, "haohan:weapon.claymore_swing", 1.0f, isRightSlash ? 1.0f : 1.15f);
        }

        BukkitTask animTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cleanup(uuid);
                    return;
                }

                ItemStack currentHand = player.getInventory().getItemInMainHand();
                if (!isLunarClaymore(currentHand)) {
                    cleanup(uuid);
                    return;
                }

                if (step < frames.length) {
                    int frameCmd = frames[step];
                    setCustomModelData(currentHand, frameCmd);

                    if (step == 2 && world != null) {
                        spawnLunarSlashParticles(player, isRightSlash);
                        performLunarCleave(player, primaryTarget);
                    }
                    step++;
                } else {
                    setCustomModelData(currentHand, CMD_IDLE);
                    cleanup(uuid);
                }
            }
        }, 0L, 1L);

        activeAnimationTasks.put(uuid, animTask);
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

    private void spawnLunarSlashParticles(Player player, boolean isRightSlash) {
        Location eyeLoc = player.getEyeLocation();
        Vector dir = eyeLoc.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up = dir.clone().crossProduct(right).multiply(-1).normalize();

        World world = eyeLoc.getWorld();
        if (world == null) return;

        Location center = eyeLoc.clone().add(dir.multiply(1.8));
        int points = 12;
        double startAngle = isRightSlash ? -Math.PI / 3 : Math.PI / 3;
        double endAngle = isRightSlash ? Math.PI / 3 : -Math.PI / 3;

        for (int i = 0; i <= points; i++) {
            double progress = (double) i / points;
            double angle = startAngle + (endAngle - startAngle) * progress;
            double radius = 1.6;

            Vector offset = right.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius * 0.8));

            Location pLoc = center.clone().add(offset);
            world.spawnParticle(Particle.SWEEP_ATTACK, pLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SCULK_SOUL, pLoc, 1, 0.05, 0.05, 0.05, 0.01);
            world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(90, 215, 245), 1.2f));
        }
    }

    private void setCustomModelData(ItemStack item, int cmd) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            if (cmd == CMD_IDLE) {
                meta.setItemModel(NamespacedKey.fromString("haohan:claymore"));
            } else if (cmd >= 6021 && cmd <= 6025) {
                meta.setItemModel(NamespacedKey.fromString("haohan:slash_left_" + (cmd - 6020)));
            } else if (cmd >= 6011 && cmd <= 6015) {
                meta.setItemModel(NamespacedKey.fromString("haohan:slash_right_" + (cmd - 6010)));
            }
            item.setItemMeta(meta);
        }
    }

    private void cleanup(UUID uuid) {
        BukkitTask task = activeAnimationTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (activeAnimationTasks.containsKey(uuid)) {
            cleanup(uuid);
            ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
            if (isLunarClaymore(prev)) {
                setCustomModelData(prev, CMD_IDLE);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isLunarClaymore(dropped)) {
            setCustomModelData(dropped, CMD_IDLE);
            cleanup(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }
}
