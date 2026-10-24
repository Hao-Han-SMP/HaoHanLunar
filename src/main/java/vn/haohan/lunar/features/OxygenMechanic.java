package vn.haohan.lunar.features;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.item.OxygenTankBehavior;
import vn.haohan.engine.core.service.IService;
import vn.haohan.engine.api.system.data.PlayerLunarData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OxygenMechanic implements Listener, IService {

    public static final int LUNAR_MAX_AIR = 600;
    public static final int VANILLA_DEFAULT_AIR = 300;
    private static final int BATCH_DURABILITY_TICKS = 10;
    private static final int BREATH_SOUND_INTERVAL_TICKS = 90;

    private final HaoHanLunarPlugin plugin;
    private final Map<ChunkKey, SafeZoneCacheEntry> safeZoneCache = new ConcurrentHashMap<>();
    private volatile long safeZoneCacheTick;
    private static final long SAFE_ZONE_CACHE_TTL_TICKS = 20L;
    private static final long SAFE_ZONE_CACHE_CLEANUP_INTERVAL = 600L;

    public OxygenMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "Oxygen";
    }

    @Override
    public int priority() {
        return 55;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(org.bukkit.plugin.Plugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetPlayerOxygen(player);
        }
        clearSafeZoneCache();
    }

    @Override
    public void tick() {
        safeZoneCacheTick++;
        if (safeZoneCacheTick % SAFE_ZONE_CACHE_CLEANUP_INTERVAL == 0 || safeZoneCache.size() > 500) {
            cleanupSafeZoneCache();
        }

        World lunarWorld = HaoHanLunarPlugin.getLunarWorld();
        if (lunarWorld == null) {
            return;
        }

        for (Player player : lunarWorld.getPlayers()) {
            tickPlayerOxygen(player);
        }
    }

    private void tickPlayerOxygen(Player player) {
        if (player.getMaximumAir() != LUNAR_MAX_AIR) {
            player.setMaximumAir(LUNAR_MAX_AIR);
        }

        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            PlayerLunarData data = plugin.getLunarDataManager().get(player);
            data.setOxygen(LUNAR_MAX_AIR);
            data.setOxygenDmg(0);
            player.setRemainingAir(LUNAR_MAX_AIR);
            return;
        }

        PlayerLunarData data = plugin.getLunarDataManager().get(player);
        if (!player.getScoreboardTags().contains("hh_lunar_oxygen")) {
            data.setOxygen(LUNAR_MAX_AIR);
            data.setOxygenDmg(0);
            player.addScoreboardTag("hh_lunar_oxygen");
        }

        Location loc = player.getLocation();
        SafeZoneResult safeZone = getSafeZoneResult(loc);
        boolean inRestBase = safeZone.inRestBase();
        boolean inSpaceStation = safeZone.inSpaceStation();
        boolean inBeaconShield = plugin.getBeaconShieldMechanic() != null
                && plugin.getBeaconShieldMechanic().isInShield(loc);
        boolean inSafeZone = inRestBase || inSpaceStation || inBeaconShield;

        if (inSafeZone) {
            // Safe zone: Deactivate any active tank and flush its accumulated damage
            if (data.isTankActive()) {
                deactivateAndFlushTank(player, data, false);
            }

            // Refill logic
            if (inRestBase) {
                data.setSsRegen(0);
                data.setOxygenDmg(0);
                data.setRbRegen(data.getRbRegen() + 1);
                if (data.getRbRegen() >= 60) {
                    data.setOxygen(data.getOxygen() + 100);
                    data.setRbRegen(0);
                }
            } else if (inSpaceStation) {
                data.setRbRegen(0);
                data.setOxygenDmg(0);
                data.setSsRegen(data.getSsRegen() + 1);
                if (data.getSsRegen() >= 40) {
                    data.setOxygen(data.getOxygen() + 150);
                    data.setSsRegen(0);
                }
            } else {
                data.setRbRegen(0);
                data.setSsRegen(0);
                data.setOxygenDmg(0);
                data.setOxygen(Math.min(LUNAR_MAX_AIR, data.getOxygen() + 1));
            }

            // Charge held oxygen tank in safe structures
            boolean isCharging = chargeHeldOxygenTank(player, data);
            if (isCharging) {
                syncVanillaAir(player, data);
                return;
            }
        } else {
            // Outside safe zone: reset safe structures variables
            data.setRbRegen(0);
            data.setSsRegen(0);
            data.setTankCharge(0);

            // Oxygen consumption: Check active tank first
            if (data.isTankActive() && data.getActiveTankUuid() != null) {
                tickActiveOxygenTank(player, data);
            } else {
                // Base oxygen decay (when no active tank)
                data.setOxygen(data.getOxygen() - 1);

                // Suffocation damage logic
                if (data.getOxygen() <= 0) {
                    data.setOxygenDmg(data.getOxygenDmg() + 1);
                    if (data.getOxygenDmg() >= 20) {
                        data.setOxygenDmg(0);
                        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                            player.damage(2.0, DamageSource.builder(DamageType.DROWN).build());
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        }
                    }
                } else {
                    data.setOxygenDmg(0);
                }
            }
        }

        // Synchronize vanilla oxygen bar (remaining air)
        syncVanillaAir(player, data);
    }

    private void tickActiveOxygenTank(Player player, PlayerLunarData data) {
        // Astronaut breathing sound loop
        data.setBreathSoundTick(data.getBreathSoundTick() + 1);
        if (data.getBreathSoundTick() >= BREATH_SOUND_INTERVAL_TICKS) {
            data.setBreathSoundTick(0);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.8f, 0.95f);
        }

        // Locate tank item: fast O(1) slot cache with inventory scan fallback
        int slot = data.getActiveTankSlot();
        ItemStack item = null;
        if (slot >= 0 && slot < player.getInventory().getSize()) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (isMatchingTank(candidate, data.getActiveTankUuid())) {
                item = candidate;
            }
        }

        if (item == null) {
            // Fallback scan across inventory
            slot = findTankSlot(player, data.getActiveTankUuid());
            if (slot != -1) {
                item = player.getInventory().getItem(slot);
                data.setActiveTankSlot(slot);
            }
        }

        // If tank item is no longer in inventory (dropped or moved into chest)
        if (item == null || !(item.getItemMeta() instanceof Damageable damageable)) {
            data.setTankActive(false);
            data.setActiveTankUuid(null);
            data.setActiveTankSlot(-1);
            data.setTankDamageAccumulator(0);
            player.removeScoreboardTag("hh_o2tank_active");
            return;
        }

        // Get tank capacity
        String customId = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().getId(item);
        var def = customId != null ? vn.haohan.itemcore.api.HaoHanItemCore.get().getItemRegistry().get(customId) : null;
        int capacity = 0;
        if (def != null && def.getProperties() != null) {
            capacity = ((Number) def.getProperties().getOrDefault("oxygen_tank_capacity", 0)).intValue();
        }
        if (capacity <= 0) {
            capacity = switch (data.getTankTier()) {
                case 1 -> 1500;
                case 2 -> 3000;
                case 3 -> 6800;
                default -> 1500;
            };
        }

        // Increment tick damage accumulator
        int accum = data.getTankDamageAccumulator() + 1;
        int currentDamage = damageable.getDamage();
        int totalProjectedDamage = currentDamage + accum;

        // Check if tank completely broke
        if (totalProjectedDamage >= capacity) {
            // Tank breaks!
            data.setTankDamageAccumulator(0);
            data.setTankActive(false);
            data.setActiveTankUuid(null);
            data.setActiveTankSlot(-1);
            player.removeScoreboardTag("hh_o2tank_active");

            // Consume/break item
            int amount = item.getAmount();
            if (amount <= 1) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(amount - 1);
                damageable.setDamage(0);
                item.setItemMeta(damageable);
                player.getInventory().setItem(slot, item);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.2f, 1.0f);
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("💥", NamedTextColor.RED),
                Component.text("Bình oxy đã cạn kiệt và bị vỡ!", NamedTextColor.RED)
            ));
            return;
        }

        // Batch update durability to disk/NBT every BATCH_DURABILITY_TICKS
        if (accum >= BATCH_DURABILITY_TICKS) {
            damageable.setDamage(currentDamage + accum);
            item.setItemMeta(damageable);
            data.setTankDamageAccumulator(0);
        } else {
            data.setTankDamageAccumulator(accum);
        }

        // While active tank supplies oxygen, maintain full player oxygen
        data.setOxygen(LUNAR_MAX_AIR);
        data.setOxygenDmg(0);
    }

    private boolean isMatchingTank(ItemStack item, String targetUuid) {
        if (item == null || targetUuid == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, OxygenTankBehavior.TANK_UUID_KEY);
        String uuid = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return targetUuid.equals(uuid);
    }

    private int findTankSlot(Player player, String targetUuid) {
        if (targetUuid == null) return -1;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMatchingTank(contents[i], targetUuid)) {
                return i;
            }
        }
        return -1;
    }

    private void deactivateAndFlushTank(Player player, PlayerLunarData data, boolean playSound) {
        if (!data.isTankActive()) return;

        int accum = data.getTankDamageAccumulator();
        String uuid = data.getActiveTankUuid();
        if (accum > 0 && uuid != null) {
            int slot = data.getActiveTankSlot();
            ItemStack item = (slot >= 0 && slot < player.getInventory().getSize()) ? player.getInventory().getItem(slot) : null;
            if (item == null || !isMatchingTank(item, uuid)) {
                slot = findTankSlot(player, uuid);
                item = (slot != -1) ? player.getInventory().getItem(slot) : null;
            }
            if (item != null && item.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(damageable.getDamage() + accum);
                item.setItemMeta(damageable);
            }
        }

        data.setTankActive(false);
        data.setActiveTankUuid(null);
        data.setActiveTankSlot(-1);
        data.setTankDamageAccumulator(0);
        player.removeScoreboardTag("hh_o2tank_active");

        if (playSound) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void syncVanillaAir(Player player, PlayerLunarData data) {
        int oxygen = data.getOxygen();
        if (player.getRemainingAir() != oxygen) {
            player.setRemainingAir(oxygen);
        }
    }

    private boolean chargeHeldOxygenTank(Player player, PlayerLunarData data) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String customId = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemService().getId(hand);
        if (customId == null) {
            data.setTankCharge(0);
            return false;
        }

        var def = vn.haohan.itemcore.api.HaoHanItemCore.get().getItemRegistry().get(customId);
        if (def == null) {
            data.setTankCharge(0);
            return false;
        }

        Map<String, Object> properties = def.getProperties();
        if (properties == null || !Boolean.TRUE.equals(properties.get("oxygen_tank"))) {
            data.setTankCharge(0);
            return false;
        }

        int capacity = ((Number) properties.getOrDefault("oxygen_tank_capacity", 0)).intValue();
        int tier = ((Number) properties.getOrDefault("oxygen_tank_tier", 0)).intValue();
        int maxTicks = ((Number) properties.getOrDefault("oxygen_tank_charge_ticks", 0)).intValue();

        if (tier == 0 || capacity == 0 || maxTicks == 0 || !(hand.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() == 0) {
            data.setTankCharge(0);
            return false;
        }

        int damage = damageable.getDamage();
        data.setTankCharge(data.getTankCharge() + 1);

        // 1. Calculate base percentage from initial damage
        double startCap = capacity - damage;
        double basePct = (startCap * 100.0) / capacity;

        // 2. Add progress percentage
        double progressPct = (data.getTankCharge() * 100.0) / maxTicks;
        double addedPct = progressPct * (100.0 - basePct) / 100.0;

        int displayPct = (int) Math.min(100, Math.ceil(basePct + addedPct));

        String sizeName = switch (tier) {
            case 1 -> "nhỏ";
            case 2 -> "vừa";
            case 3 -> "lớn";
            default -> "";
        };

        player.sendActionBar(Component.text("⚡ Đang nạp bình oxy " + sizeName + "... ", NamedTextColor.YELLOW)
            .append(Component.text(displayPct + "%", NamedTextColor.GREEN)));

        if (data.getTankCharge() >= maxTicks) {
            damageable.setDamage(0);
            hand.setItemMeta(damageable);
            data.setTankCharge(0);

            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text("\uD83D\uDD0B Bình oxy đã được nạp xong!", NamedTextColor.GREEN)
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 2.0f, 1.73f);
        }

        return true;
    }

    private void displayOxygen(Player player, PlayerLunarData data) {
        int oxygen = data.getOxygen();
        int fullBubbles = (int) Math.ceil(oxygen / 60.0);
        if (oxygen <= 0) fullBubbles = 0;
        int emptyBubbles = 10 - fullBubbles;

        var message = Component.text();
        if (fullBubbles > 0) {
            String fullText = "● ".repeat(fullBubbles);
            if (emptyBubbles == 0) {
                fullText = fullText.trim();
            }
            message.append(Component.text(fullText, NamedTextColor.BLUE));
        }
        if (emptyBubbles > 0) {
            String emptyText = "○ ".repeat(emptyBubbles).trim();
            message.append(Component.text(emptyText, NamedTextColor.DARK_GRAY));
        }

        // Sound effect on bubble loss
        if (oxygen == 540 || oxygen == 480 || oxygen == 420 || oxygen == 360 
            || oxygen == 300 || oxygen == 240 || oxygen == 180 || oxygen == 120 
            || oxygen == 60 || oxygen == 1) {
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.MASTER, 1.5f, 1.0f);
        }

        // Active tank percentage
        if (data.isTankActive()) {
            int capacity = 0;
            if (data.getTankTier() == 1) capacity = 1500;
            else if (data.getTankTier() == 2) capacity = 3000;
            else if (data.getTankTier() == 3) capacity = 6800;

            if (capacity > 0) {
                int pct = (data.getTankO2() * 100) / capacity;
                NamedTextColor color = NamedTextColor.GREEN;
                if (pct <= 25) color = NamedTextColor.RED;
                else if (pct <= 50) color = NamedTextColor.YELLOW;

                message.append(Component.text("  \uD83D\uDD0B " + pct + "%", color));
            }
        }

        player.sendActionBar(message.build());
    }

    public void resetPlayerOxygen(Player player) {
        PlayerLunarData data = plugin.getLunarDataManager().get(player);
        if (data.isTankActive()) {
            deactivateAndFlushTank(player, data, false);
        }
        data.setOxygen(LUNAR_MAX_AIR);
        data.setOxygenDmg(0);
        data.setRbRegen(0);
        data.setSsRegen(0);
        data.setTankO2(0);
        data.setTankTier(0);
        data.setTankCharge(0);
        data.setBreathSoundTick(0);
        player.removeScoreboardTag("hh_lunar_oxygen");
        player.removeScoreboardTag("hh_o2tank_active");

        // Restore vanilla defaults
        player.setMaximumAir(VANILLA_DEFAULT_AIR);
        player.setRemainingAir(VANILLA_DEFAULT_AIR);
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            resetPlayerOxygen(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            plugin.getLunarDataManager().saveAndRemove(player);
        } else {
            plugin.getLunarDataManager().remove(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (event.getRespawnLocation().getWorld() != null
                && !HaoHanLunarPlugin.isLunarWorld(event.getRespawnLocation().getWorld())) {
            resetPlayerOxygen(player);
        }
    }

    public boolean isInSafeZone(Location loc) {
        SafeZoneResult result = getSafeZoneResult(loc);
        return result.inRestBase() || result.inSpaceStation();
    }

    public boolean isInRestBase(Location loc) {
        return getSafeZoneResult(loc).inRestBase();
    }

    public boolean isInSpaceStation(Location loc) {
        return getSafeZoneResult(loc).inSpaceStation();
    }

    public void clearSafeZoneCache() {
        safeZoneCache.clear();
    }

    private void cleanupSafeZoneCache() {
        long now = safeZoneCacheTick;
        safeZoneCache.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() <= now);
        if (safeZoneCache.size() > 500) {
            // A bounded fallback for exploration bursts; the next access repopulates entries lazily.
            safeZoneCache.clear();
        }
    }

    @SuppressWarnings("removal")
    private SafeZoneResult getSafeZoneResult(Location loc) {
        if (loc == null || loc.getWorld() == null) return SafeZoneResult.OUTSIDE;
        World world = loc.getWorld();
        if (!HaoHanLunarPlugin.isLunarWorld(world)) return SafeZoneResult.OUTSIDE;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        ChunkKey cacheKey = new ChunkKey(world.getUID(), chunkX, chunkZ);
        SafeZoneCacheEntry cached = safeZoneCache.get(cacheKey);
        if (cached != null && cached.expiresAtTick() > safeZoneCacheTick) {
            return evaluateSafeZone(loc, cached.boxes());
        }

        // Bukkit world/structure APIs are main-thread only. Async MythicMobs or
        // placeholder evaluation must fail closed on a cache miss.
        if (!Bukkit.isPrimaryThread()) {
            return SafeZoneResult.OUTSIDE;
        }

        List<SafeZoneBox> boxes = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var structures = world.getStructures(chunkX + dx, chunkZ + dz);
                for (var gen : structures) {
                    String key = gen.getStructure().getKey().toString();
                    if (key.equals("haohan:rest_base") || key.equals("haohan:space_station")) {
                        boxes.add(new SafeZoneBox(key, gen.getBoundingBox()));
                    }
                }
            }
        }
        safeZoneCache.put(cacheKey, new SafeZoneCacheEntry(List.copyOf(boxes), safeZoneCacheTick + SAFE_ZONE_CACHE_TTL_TICKS));
        return evaluateSafeZone(loc, boxes);
    }

    private SafeZoneResult evaluateSafeZone(Location location, List<SafeZoneBox> boxes) {
        boolean inRestBase = false;
        boolean inSpaceStation = false;
        for (SafeZoneBox box : boxes) {
            if (box.bounds().contains(location.toVector())) {
                inRestBase |= box.key().equals("haohan:rest_base");
                inSpaceStation |= box.key().equals("haohan:space_station");
            }
        }
        return new SafeZoneResult(inRestBase, inSpaceStation);
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}
    private record SafeZoneCacheEntry(List<SafeZoneBox> boxes, long expiresAtTick) {}
    private record SafeZoneBox(String key, BoundingBox bounds) {}
    public record SafeZoneResult(boolean inRestBase, boolean inSpaceStation) {
        public static final SafeZoneResult OUTSIDE = new SafeZoneResult(false, false);
    }
}
