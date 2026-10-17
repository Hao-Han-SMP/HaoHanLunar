package vn.haohan.lunar.features;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.engine.core.service.IService;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.util.MoonUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mechanic for detecting players gazing at the Moon and awarding achievements:
 * 1. "Ngắm nhìn chú cuội": Standing still looking at the moon for 10s (200 ticks).
 * 2. "Cái đó là gì vậy?": Using spyglass looking at the moon for 0.75s (15 ticks).
 */
public class MoonGazingMechanic implements Listener, IService {

    public static final NamespacedKey ADV_CUOI = NamespacedKey.fromString("haohan:lunar/moon_cuoi");
    public static final NamespacedKey ADV_SPYGLASS = NamespacedKey.fromString("haohan:lunar/moon_what_is_that");

    private static final int TICK_INTERVAL = 5; // Run every 5 ticks (0.25s)
    private static final int CUOI_REQUIRED_TICKS = 200; // 10 seconds
    private static final int SPYGLASS_REQUIRED_TICKS = 15; // 0.75 seconds

    private static final double NAKED_EYE_MAX_ANGLE = 14.0; // degrees
    private static final double SPYGLASS_MAX_ANGLE = 8.0;   // degrees

    private final HaoHanLunarPlugin plugin;
    private final BukkitTask task;

    private final Map<UUID, Integer> cuoiTicksMap = new HashMap<>();
    private final Map<UUID, Integer> spyglassTicksMap = new HashMap<>();
    private final Map<UUID, Location> lastLocationMap = new HashMap<>();

    public MoonGazingMechanic(HaoHanLunarPlugin plugin) {
        this(plugin, true);
    }

    public MoonGazingMechanic(HaoHanLunarPlugin plugin, boolean scheduleTask) {
        this.plugin = plugin;
        if (scheduleTask) {
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
        } else {
            this.task = null;
        }
    }

    @Override
    public String name() {
        return "MoonGazing";
    }

    @Override
    public int priority() {
        return 36;
    }

    @Override
    public void disable(org.bukkit.plugin.Plugin plugin) {
        cleanup();
    }

    public void cleanup() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        cuoiTicksMap.clear();
        spyglassTicksMap.clear();
        lastLocationMap.clear();
    }

    @Override
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player, TICK_INTERVAL);
        }
    }

    /**
     * Inspects a player's conditions and increments tracking ticks.
     * Package-private for testing.
     */
    void checkPlayer(Player player, int ticksPassed) {
        if (!player.isOnline()) {
            return;
        }

        World world = player.getWorld();
        // 1. Only in normal Overworld environment and not Lunar dimension
        if (world.getEnvironment() != World.Environment.NORMAL || HaoHanLunarPlugin.isLunarWorld(world)) {
            resetPlayer(player.getUniqueId());
            return;
        }

        // 2. Night time and clear sky
        if (!MoonUtil.isNightTime(world.getTime()) || world.hasStorm()) {
            resetPlayer(player.getUniqueId());
            return;
        }

        Location currentLoc = player.getLocation();
        // 3. Clear line of sight to sky (highest block Y must not be above player's head)
        int highestY = world.getHighestBlockYAt(currentLoc);
        if (highestY > player.getEyeLocation().getY()) {
            resetPlayer(player.getUniqueId());
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean isLookingNakedEye = MoonUtil.isLookingAtMoon(player, NAKED_EYE_MAX_ANGLE);
        boolean isLookingSpyglass = MoonUtil.isLookingAtMoon(player, SPYGLASS_MAX_ANGLE);

        // --- Logic 1: Ngắm nhìn chú cuội (Đứng yên 10s) ---
        Location lastLoc = lastLocationMap.get(uuid);
        boolean stoodStill = false;
        if (lastLoc != null && lastLoc.getWorld().equals(world)) {
            // Check position delta (ignoring pitch/yaw rotation)
            double distanceSq = Math.pow(lastLoc.getX() - currentLoc.getX(), 2)
                    + Math.pow(lastLoc.getY() - currentLoc.getY(), 2)
                    + Math.pow(lastLoc.getZ() - currentLoc.getZ(), 2);
            stoodStill = distanceSq <= 0.04; // Movement < 0.2 block
        }
        lastLocationMap.put(uuid, currentLoc.clone());

        if (isLookingNakedEye && stoodStill) {
            int currentCuoi = cuoiTicksMap.getOrDefault(uuid, 0) + ticksPassed;
            cuoiTicksMap.put(uuid, currentCuoi);
            if (currentCuoi >= CUOI_REQUIRED_TICKS) {
                grantAdvancement(player, ADV_CUOI);
            }
        } else if (isLookingNakedEye) {
            // Just started or moved slightly while looking
            cuoiTicksMap.put(uuid, ticksPassed);
        } else {
            cuoiTicksMap.remove(uuid);
        }

        // --- Logic 2: Cái đó là gì vậy? (Dùng spyglass 0.75s) ---
        boolean usingSpyglass = isUsingSpyglass(player);
        if (usingSpyglass && isLookingSpyglass) {
            int currentSpyglass = spyglassTicksMap.getOrDefault(uuid, 0) + ticksPassed;
            spyglassTicksMap.put(uuid, currentSpyglass);
            if (currentSpyglass >= SPYGLASS_REQUIRED_TICKS) {
                grantAdvancement(player, ADV_SPYGLASS);
            }
        } else {
            spyglassTicksMap.remove(uuid);
        }
    }

    private boolean isUsingSpyglass(Player player) {
        if (!player.isHandRaised()) {
            return false;
        }
        EquipmentSlot slot = player.getHandRaised();
        if (slot == null) {
            return false;
        }
        ItemStack item = player.getInventory().getItem(slot);
        return item != null && item.getType() == Material.SPYGLASS;
    }

    protected void grantAdvancement(Player player, NamespacedKey key) {
        Advancement adv = Bukkit.getAdvancement(key);
        if (adv == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(adv);
        if (progress != null && !progress.isDone()) {
            for (String criteria : progress.getRemainingCriteria()) {
                progress.awardCriteria(criteria);
            }
        }
    }

    private void resetPlayer(UUID uuid) {
        cuoiTicksMap.remove(uuid);
        spyglassTicksMap.remove(uuid);
        lastLocationMap.remove(uuid);
    }

    public int getCuoiTicks(UUID uuid) {
        return cuoiTicksMap.getOrDefault(uuid, 0);
    }

    public int getSpyglassTicks(UUID uuid) {
        return spyglassTicksMap.getOrDefault(uuid, 0);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        resetPlayer(event.getPlayer().getUniqueId());
    }
}
