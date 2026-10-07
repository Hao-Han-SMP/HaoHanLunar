package vn.haohan.lunar.robot;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;
import vn.haohan.lunar.robot.ui.LunarRobotDashboardUi;
import vn.haohan.lunar.robot.ui.LunarRobotInventoryUi;
import vn.haohan.lunar.robot.ui.LunarRobotPuzzleUi;
import com.ticxo.modelengine.api.ModelEngineAPI;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LunarRobotMechanic implements Listener {
    private final HaoHanLunarPlugin plugin;
    private DisplayUiService uiService;

    // Active in-world robots: Entity UUID -> LunarRobotEntity
    private final Map<UUID, LunarRobotEntity> activeRobots = new ConcurrentHashMap<>();

    // Owner UUID -> Robot UUID (Strict rule: 1 Player = Max 1 Robot)
    private final Map<UUID, UUID> playerRobotMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractMap = new ConcurrentHashMap<>();
    private final Map<UUID, LunarRobotPuzzleUi> activePuzzles = new ConcurrentHashMap<>();
    private final Map<UUID, LunarRobotDashboardUi> activeDashboards = new ConcurrentHashMap<>();

    private record DeathNotice(String robotName, String cause, UUID robotUuid) {}
    private final Map<UUID, DeathNotice> pendingDeathNotices = new ConcurrentHashMap<>();

    private int spawnCheckTicks = 0;

    public LunarRobotMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        initUiService();
    }

    private void initUiService() {
        try {
            if (Bukkit.getServer() != null && Bukkit.getServicesManager() != null) {
                var reg = Bukkit.getServicesManager().getRegistration(DisplayUiService.class);
                if (reg != null) {
                    this.uiService = reg.getProvider();
                }
            }
        } catch (Throwable ignored) {}
    }

    public DisplayUiService getUiService() {
        if (uiService == null) {
            initUiService();
        }
        return uiService;
    }

    public void tick() {
        // 1. Tick active robots
        Iterator<Map.Entry<UUID, LunarRobotEntity>> it = activeRobots.entrySet().iterator();
        while (it.hasNext()) {
            LunarRobotEntity robot = it.next().getValue();
            if (!robot.getEntity().isValid() || robot.getEntity().isDead()) {
                robot.cleanup();
                it.remove();
                continue;
            }
            robot.tick();
        }

        // 2. Space station population check (every 10 seconds / 200 ticks)
        spawnCheckTicks++;
        if (spawnCheckTicks >= 200) {
            spawnCheckTicks = 0;
            checkSpaceStationSpawns();
        }

        // 3. Tick active hologram UIs: check distance to robot & connection validity
        if (!activePuzzles.isEmpty()) {
            activePuzzles.entrySet().removeIf(entry -> {
                LunarRobotPuzzleUi puzzle = entry.getValue();
                Player p = puzzle.getPlayer();
                LunarRobotEntity r = puzzle.getRobot();
                if (p == null || !p.isOnline()) {
                    puzzle.close();
                    return true;
                }
                if (r == null || !r.getEntity().isValid() || r.getEntity().isDead()) {
                    puzzle.close();
                    p.sendMessage("§c§l[Robot] §cKết nối thất bại! Robot mục tiêu đã bị tiêu diệt.");
                    return true;
                }
                if (p.getWorld() != r.getEntity().getWorld()
                        || p.getLocation().distanceSquared(r.getEntity().getLocation()) > 144.0) {
                    puzzle.close();
                    p.sendMessage("§6§l[Robot] §cBạn đã di chuyển quá xa vị trí robot, kết nối gián đoạn!");
                    return true;
                }
                puzzle.tick();
                return false;
            });
        }

        if (!activeDashboards.isEmpty()) {
            activeDashboards.entrySet().removeIf(entry -> {
                LunarRobotDashboardUi dashboard = entry.getValue();
                Player p = dashboard.getPlayer();
                LunarRobotEntity r = dashboard.getRobot();
                if (p == null || !p.isOnline()) {
                    dashboard.close();
                    return true;
                }
                if (r == null || r.getEntity() == null || r.getEntity().isDead()) {
                    dashboard.close();
                    if (p.isOnline()) {
                        p.sendMessage("§c§l[Robot] §cMất kết nối hoàn toàn! Robot của bạn đã bị phá hủy.");
                        unlinkPlayerRobot(p, r != null && r.getEntity() != null ? r.getEntity().getUniqueId() : null);
                        p.updateInventory();
                    }
                    return true;
                }
                dashboard.tick();
                return false;
            });
        }
    }

    public LunarRobotEntity getOrRegisterRobot(LivingEntity entity) {
        if (!isRobotAlive(entity)) return null;

        LunarRobotEntity existing = activeRobots.get(entity.getUniqueId());
        if (existing != null && existing.getEntity().isValid()) {
            return existing;
        }
        if (existing != null) {
            existing.cleanup();
            activeRobots.remove(entity.getUniqueId());
        }

        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING)) {
            pdc.set(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING, entity.getUniqueId().toString());
            pdc.set(new NamespacedKey(plugin, "is_lunar_robot"), PersistentDataType.BYTE, (byte) 1);
        }
        entity.setSilent(true);
        LunarRobotData data = LunarRobotData.loadFrom(entity, plugin);
        LunarRobotEntity robot = new LunarRobotEntity(plugin, entity, data);
        activeRobots.put(entity.getUniqueId(), robot);
        if (data.isTamed() && data.getOwnerUuid() != null) {
            playerRobotMap.put(data.getOwnerUuid(), entity.getUniqueId());
            Player owner = Bukkit.getPlayer(data.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                saveRobotLocationToPlayer(owner, robot);
                ItemStack main = owner.getInventory().getItemInMainHand();
                if (isTabletItem(main)) syncTabletItem(main, robot);
                ItemStack off = owner.getInventory().getItemInOffHand();
                if (isTabletItem(off)) syncTabletItem(off, robot);
            }
        }
        return robot;
    }

    public UUID getPlayerRobotUuid(Player player) {
        if (player == null) return null;
        UUID inMap = playerRobotMap.get(player.getUniqueId());
        if (inMap != null) return inMap;
        String saved = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "owned_robot_id"), PersistentDataType.STRING);
        if (saved != null && !saved.isBlank()) {
            try {
                UUID id = UUID.fromString(saved);
                playerRobotMap.put(player.getUniqueId(), id);
                return id;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public LunarRobotEntity getPlayerRobot(Player player) {
        if (player == null) return null;
        UUID robotId = getPlayerRobotUuid(player);
        if (robotId == null) return null;
        return resolveRobotEntity(player, robotId);
    }

    public LunarRobotEntity getPlayerRobot(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            return getPlayerRobot(player);
        }
        UUID robotId = playerRobotMap.get(playerUuid);
        if (robotId == null) return null;
        return resolveRobotEntity(null, robotId);
    }

    public LunarRobotEntity resolveRobotEntity(Player player, UUID robotId) {
        // 1. If robotId is provided, try direct lookup in active robots
        if (robotId != null) {
            LunarRobotEntity existing = activeRobots.get(robotId);
            if (existing != null && existing.getEntity().isValid()) {
                return existing;
            }
            if (existing != null) {
                existing.cleanup();
                activeRobots.remove(robotId);
            }

            // Check loaded Bukkit entity directly
            Entity ent = Bukkit.getEntity(robotId);
            if (ent instanceof LivingEntity living && isRobotAlive(living)) {
                return getOrRegisterRobot(living);
            }

            // Check if robotId matches the persistent robot_id stored in LunarRobotData
            for (LunarRobotEntity r : activeRobots.values()) {
                if (r.getEntity().isValid() && robotId.equals(r.getData().getRobotId())) {
                    return r;
                }
            }
        }

        // 2. If player is provided, resolve by player ownership (Strict rule: 1 Player = Max 1 Robot)
        if (player != null) {
            UUID ownerUuid = player.getUniqueId();

            // 2a. Check playerRobotMap in memory
            UUID mappedId = playerRobotMap.get(ownerUuid);
            if (mappedId != null && !mappedId.equals(robotId)) {
                LunarRobotEntity mappedRobot = activeRobots.get(mappedId);
                if (mappedRobot != null && mappedRobot.getEntity().isValid() && ownerUuid.equals(mappedRobot.getData().getOwnerUuid())) {
                    return mappedRobot;
                }
                Entity ent = Bukkit.getEntity(mappedId);
                if (ent instanceof LivingEntity living && isRobotAlive(living)) {
                    LunarRobotEntity reg = getOrRegisterRobot(living);
                    if (reg != null && ownerUuid.equals(reg.getData().getOwnerUuid())) {
                        return reg;
                    }
                }
            }

            // 2b. Check in-memory active robots for one owned by this player
            for (LunarRobotEntity r : activeRobots.values()) {
                if (r.getEntity().isValid() && ownerUuid.equals(r.getData().getOwnerUuid())) {
                    playerRobotMap.put(ownerUuid, r.getEntity().getUniqueId());
                    saveRobotLocationToPlayer(player, r);
                    return r;
                }
            }

            // 2c. Check nearby entities around player (e.g. radius 48 blocks) - handles carried & placed robots
            if (player.isOnline()) {
                for (Entity nearby : player.getNearbyEntities(48, 48, 48)) {
                    if (isRobotAlive(nearby) && nearby instanceof LivingEntity living) {
                        LunarRobotEntity candidate = getOrRegisterRobot(living);
                        if (candidate != null && ownerUuid.equals(candidate.getData().getOwnerUuid())) {
                            playerRobotMap.put(ownerUuid, candidate.getEntity().getUniqueId());
                            saveRobotLocationToPlayer(player, candidate);
                            return candidate;
                        }
                    }
                }
            }

            // 2d. Check loaded living entities in the player's world
            if (player.isOnline() && player.getWorld() != null) {
                for (LivingEntity living : player.getWorld().getLivingEntities()) {
                    if (isRobotAlive(living)) {
                        LunarRobotEntity candidate = getOrRegisterRobot(living);
                        if (candidate != null && ownerUuid.equals(candidate.getData().getOwnerUuid())) {
                            playerRobotMap.put(ownerUuid, candidate.getEntity().getUniqueId());
                            saveRobotLocationToPlayer(player, candidate);
                            return candidate;
                        }
                    }
                }
            }
        }

        // 3. Try to load chunk from saved player coordinates
        String worldName = null;
        double x = 0, y = 0, z = 0;
        boolean hasLoc = false;

        if (player != null) {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            worldName = pdc.get(new NamespacedKey(plugin, "robot_loc_world"), PersistentDataType.STRING);
            Double px = pdc.get(new NamespacedKey(plugin, "robot_loc_x"), PersistentDataType.DOUBLE);
            Double py = pdc.get(new NamespacedKey(plugin, "robot_loc_y"), PersistentDataType.DOUBLE);
            Double pz = pdc.get(new NamespacedKey(plugin, "robot_loc_z"), PersistentDataType.DOUBLE);
            if (worldName != null && px != null && py != null && pz != null) {
                x = px;
                y = py;
                z = pz;
                hasLoc = true;
            }
        }

        if (hasLoc && worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                int cx = (int) Math.floor(x) >> 4;
                int cz = (int) Math.floor(z) >> 4;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        world.loadChunk(cx + dx, cz + dz);
                    }
                }
                if (robotId != null) {
                    Entity loaded = Bukkit.getEntity(robotId);
                    if (loaded instanceof LivingEntity living && isRobotAlive(living)) {
                        return getOrRegisterRobot(living);
                    }
                }
                if (player != null) {
                    UUID ownerUuid = player.getUniqueId();
                    for (Chunk chunk : world.getLoadedChunks()) {
                        for (Entity e : chunk.getEntities()) {
                            if (e instanceof LivingEntity living && isRobotAlive(living)) {
                                LunarRobotEntity candidate = getOrRegisterRobot(living);
                                if (candidate != null && ownerUuid.equals(candidate.getData().getOwnerUuid())) {
                                    playerRobotMap.put(ownerUuid, candidate.getEntity().getUniqueId());
                                    saveRobotLocationToPlayer(player, candidate);
                                    return candidate;
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    public void saveRobotLocationToPlayer(Player player, LunarRobotEntity robot) {
        if (player == null || robot == null || !robot.getEntity().isValid()) return;
        Location loc = robot.getEntity().getLocation();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "owned_robot_id"), PersistentDataType.STRING, robot.getEntity().getUniqueId().toString());
        if (loc.getWorld() != null) {
            pdc.set(new NamespacedKey(plugin, "robot_loc_world"), PersistentDataType.STRING, loc.getWorld().getName());
            pdc.set(new NamespacedKey(plugin, "robot_loc_x"), PersistentDataType.DOUBLE, loc.getX());
            pdc.set(new NamespacedKey(plugin, "robot_loc_y"), PersistentDataType.DOUBLE, loc.getY());
            pdc.set(new NamespacedKey(plugin, "robot_loc_z"), PersistentDataType.DOUBLE, loc.getZ());
        }
    }

    public boolean isRobotEntity(Entity entity) {
        if (entity == null) return false;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (pdc.has(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING) ||
                pdc.has(new NamespacedKey(plugin, "is_lunar_robot"), PersistentDataType.BYTE)) {
            return true;
        }

        // Check ModelEngine ModeledEntity
        try {
            var me = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (me != null && (me.getModel("robot_dog").isPresent() || me.getModel("lunar_robot").isPresent())) {
                return true;
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public boolean isRobotAlive(Entity entity) {
        if (!isRobotEntity(entity)) return false;
        if (!(entity instanceof LivingEntity living)) return false;
        if (!living.isValid() || living.isDead() || living.getHealth() <= 0) return false;
        if (plugin.getModelEngineDeathListener() != null && plugin.getModelEngineDeathListener().isDying(living.getUniqueId())) {
            return false;
        }
        return true;
    }

    public LunarRobotEntity spawnWildRobot(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;

        Wolf wolf = world.spawn(loc, Wolf.class, w -> {
            w.setTamed(false);
            w.setOwner(null);
            w.setAdult();
            w.setAgeLock(true);
            w.setSilent(true);
            w.setRemoveWhenFarAway(false);
            w.setPersistent(true);

            PersistentDataContainer pdc = w.getPersistentDataContainer();
            UUID robotId = UUID.randomUUID();
            pdc.set(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING, robotId.toString());
            pdc.set(new NamespacedKey(plugin, "is_lunar_robot"), PersistentDataType.BYTE, (byte) 1);
        });

        LunarRobotData data = new LunarRobotData(wolf.getUniqueId());
        data.setBatteryType("small");
        data.setEnergy(5000);
        data.saveTo(wolf, plugin);

        LunarRobotEntity robot = new LunarRobotEntity(plugin, wolf, data);
        activeRobots.put(wolf.getUniqueId(), robot);
        return robot;
    }

    private void checkSpaceStationSpawns() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            if (loc.getWorld() != null && plugin.getOxygenMechanic() != null && plugin.getOxygenMechanic().isInSpaceStation(loc)) {
                long count = loc.getWorld().getNearbyEntities(loc, 48, 48, 48).stream()
                        .filter(this::isRobotAlive)
                        .count();
                if (count < 3) {
                    Location spawnLoc = loc.clone().add((Math.random() - 0.5) * 16, 0, (Math.random() - 0.5) * 16);
                    Block block = spawnLoc.getBlock();
                    if (block.isPassable() && block.getRelative(0, -1, 0).getType().isSolid()) {
                        spawnWildRobot(spawnLoc);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        LunarRobotDashboardUi active = activeDashboards.get(player.getUniqueId());
        if (active != null && active.getState() != LunarRobotDashboardUi.UiState.CLOSING) {
            event.setCancelled(true);
            active.handleClickAtCursor(player);
            return;
        }
        if (!(event.getRightClicked() instanceof LivingEntity living)) return;
        if (!isRobotAlive(living)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean hasTablet = isTabletItem(mainHand) || isTabletItem(offHand);

        event.setCancelled(true);
        handleRobotInteract(player, living, hasTablet);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onModelEngineInteract(com.ticxo.modelengine.api.events.BaseEntityInteractEvent event) {
        if (event.getAction() != com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action.INTERACT &&
                event.getAction() != com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action.INTERACT_ON) {
            return;
        }
        if (event.getSlot() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        LunarRobotDashboardUi active = activeDashboards.get(player.getUniqueId());
        if (active != null && active.getState() != LunarRobotDashboardUi.UiState.CLOSING) {
            active.handleClickAtCursor(player);
            return;
        }
        Object original = event.getBaseEntity().getOriginal();
        if (!(original instanceof LivingEntity living)) return;
        if (!isRobotAlive(living)) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean hasTablet = isTabletItem(mainHand) || isTabletItem(offHand);

        handleRobotInteract(player, living, hasTablet);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerArmSwing(org.bukkit.event.player.PlayerAnimationEvent event) {
        if (event.getAnimationType() != org.bukkit.event.player.PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        LunarRobotDashboardUi dashboard = activeDashboards.get(player.getUniqueId());
        if (dashboard != null && dashboard.getState() == LunarRobotDashboardUi.UiState.DASHBOARD) {
            dashboard.handleClickAtCursor(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        LunarRobotDashboardUi activeDashboard = activeDashboards.get(player.getUniqueId());
        if (activeDashboard != null && activeDashboard.getState() != LunarRobotDashboardUi.UiState.CLOSING) {
            event.setCancelled(true);
            activeDashboard.handleClickAtCursor(player);
            return;
        }
        if (activePuzzles.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean mainIsTablet = isTabletItem(mainHand);
        boolean offIsTablet = isTabletItem(offHand);
        if (!mainIsTablet && !offIsTablet) return;

        ItemStack tabletItem = mainIsTablet ? mainHand : offHand;

        if (event.getAction().isRightClick()) {
            event.setCancelled(true);

            long now = System.currentTimeMillis();
            Long last = lastInteractMap.get(player.getUniqueId());
            if (last != null && (now - last) < 250) {
                return;
            }
            lastInteractMap.put(player.getUniqueId(), now);

            // 1. Raycast for aiming directly at a robot
            var ray = player.getWorld().rayTraceEntities(
                    player.getEyeLocation(),
                    player.getEyeLocation().getDirection(),
                    8.0,
                    1.2,
                    e -> e != player && isRobotAlive(e)
            );
            if (ray != null && ray.getHitEntity() instanceof LivingEntity targetRobot) {
                LunarRobotEntity hitRobot = getOrRegisterRobot(targetRobot);
                if (hitRobot != null) {
                    if (hitRobot.getData().isTamed()) {
                        if (!player.getUniqueId().equals(hitRobot.getData().getOwnerUuid())) {
                            player.sendMessage("§c§l[Robot Đã Khóa] §7Robot này đã thuộc về người chơi §f" + (hitRobot.getData().getOwnerName() != null ? hitRobot.getData().getOwnerName() : "khác") + "§7! Bạn không có quyền truy cập.");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                            return;
                        }
                        syncTabletItem(tabletItem, hitRobot);
                        openRobotDashboard(player, hitRobot);
                        return;
                    } else {
                        handleRobotInteract(player, targetRobot, true);
                        return;
                    }
                }
            }

            // 2. Resolve robot from Tablet PDC or Player
            UUID targetRobotUuid = getLinkedRobotUuid(tabletItem);
            if (targetRobotUuid == null) {
                targetRobotUuid = getPlayerRobotUuid(player);
            }

            LunarRobotEntity robot = null;
            if (targetRobotUuid != null) {
                robot = resolveRobotEntity(player, targetRobotUuid);
            }
            if (robot == null) {
                robot = resolveRobotEntity(player, null);
            }

            if (robot != null && robot.getEntity().isValid()) {
                // Strict Ownership Check
                if (!player.getUniqueId().equals(robot.getData().getOwnerUuid())) {
                    player.sendMessage("§c§l[Robot Đã Khóa] §7Robot này đã thuộc về người chơi §f" + (robot.getData().getOwnerName() != null ? robot.getData().getOwnerName() : "khác") + "§7! Bạn không có quyền truy cập.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }
                syncTabletItem(tabletItem, robot);
                openRobotDashboard(player, robot);
                return;
            }

            // 3. Check for nearby wild robot
            for (Entity nearby : player.getNearbyEntities(6, 6, 6)) {
                if (isRobotAlive(nearby) && nearby instanceof LivingEntity living) {
                    LunarRobotEntity wild = getOrRegisterRobot(living);
                    if (wild != null && !wild.getData().isTamed()) {
                        handleRobotInteract(player, living, true);
                        return;
                    }
                }
            }

            // 4. Robot signal lost or not found -> Unlink immediately, notify only once
            if (targetRobotUuid != null) {
                unlinkPlayerRobot(player, targetRobotUuid);
                resetTabletItem(tabletItem);
                player.updateInventory();
                if (activeRobots.containsKey(targetRobotUuid)) {
                    LunarRobotEntity oldRobot = activeRobots.remove(targetRobotUuid);
                    if (oldRobot != null) {
                        oldRobot.getData().setOwnerUuid(null);
                        oldRobot.getData().setOwnerName(null);
                        oldRobot.cleanup();
                    }
                }
                player.sendMessage("§c§l[Tablet Robot] §cKhông thể kết nối với Robot (Mất tín hiệu)! Đã tự động hủy liên kết.");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                return;
            }

            player.sendMessage("§e§l[Tablet Robot] §7Tablet chưa được liên kết với Robot nào! Hãy chuột phải vào Robot hoang dã để kết nối.");
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.2f);
        }
    }

    public void handleRobotInteract(Player player, LivingEntity living, boolean hasTablet) {
        if (!isRobotAlive(living)) return;
        long now = System.currentTimeMillis();
        Long last = lastInteractMap.get(player.getUniqueId());
        if (last != null && (now - last) < 250) {
            return;
        }
        lastInteractMap.put(player.getUniqueId(), now);

        LunarRobotEntity robot = getOrRegisterRobot(living);
        if (robot == null) return;
        LunarRobotData data = robot.getData();

        if (!data.isTamed()) {
            // Wild Robot
            if (!hasTablet) {
                player.sendMessage("§e§l[Robot Hoang Dã] §7Robot đang ở chế độ bảo vệ. Hãy sử dụng §bTablet §7để kết nối và giải mã!");
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.7f, 1.2f);
                return;
            }

            // Check 1 Player = Max 1 Robot rule
            if (getPlayerRobotUuid(player) != null) {
                player.sendMessage("§c§l[Tablet Robot] §cBạn đã sở hữu một robot khác rồi! Hãy dùng Tablet hủy liên kết robot cũ trước khi thuần hóa con mới.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }

            // Open Minigame Puzzle via HaoHanDisplayUI
            openPuzzleMinigame(player, robot);
        } else {
            // Tamed Robot - Strict Ownership Protection
            if (!player.getUniqueId().equals(data.getOwnerUuid())) {
                player.sendMessage("§c§l[Robot Đã Khóa] §7Robot này đã thuộc về người chơi §f" + (data.getOwnerName() != null ? data.getOwnerName() : "khác") + "§7. Bạn không thể can thiệp hay thuần phục lại!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }

            // Owner interactions:
            // Check if holding repair material (and not tablet) - chỉ dùng Phôi Sắt, hồi 2-8 HP
            if (!hasTablet) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                ItemStack repairItem = null;
                if (isRepairMaterial(mainHand)) {
                    repairItem = mainHand;
                } else if (isRepairMaterial(offHand)) {
                    repairItem = offHand;
                }

                if (repairItem != null) {
                    if (data.getHealth() >= data.getMaxHealth()) {
                        player.sendMessage("§a§l[Bảo Trì Robot] §7Độ hoàn thiện của Robot đang ở mức tối đa (§a100%§7).");
                        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.7f, 1.2f);
                        return;
                    }

                    int oldPct = (int) Math.round(data.getIntegrityPercentage());
                    double repairAmount = java.util.concurrent.ThreadLocalRandom.current().nextInt(2, 9); // 2 - 8 HP
                    double newHealth = Math.min(data.getMaxHealth(), data.getHealth() + repairAmount);
                    data.setHealth(newHealth);
                    living.setHealth(newHealth);
                    robot.updateCustomName();

                    if (player.getGameMode() != GameMode.CREATIVE) {
                        repairItem.subtract(1);
                    }

                    int newPct = (int) Math.round(data.getIntegrityPercentage());
                    living.getWorld().playSound(living.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.3f);
                    living.getWorld().spawnParticle(Particle.WAX_OFF, living.getLocation().add(0, 0.8, 0), 15, 0.4, 0.4, 0.4, 0.05);
                    player.sendMessage("§a§l[Bảo Trì Robot] §eĐã phục hồi §a+" + (int) repairAmount + " HP §eđộ hoàn thiện: §f" + oldPct + "% §e➔ §a" + newPct + "% §8(" + String.format("%.1f", newHealth) + "/" + String.format("%.1f", data.getMaxHealth()) + " HP)");
                    return;
                }

                // Check if holding Robot Battery - nạp hoặc thay pin trực tiếp
                ItemStack batteryHold = RobotBatteryUtil.isBatteryItem(mainHand) ? mainHand : (RobotBatteryUtil.isBatteryItem(offHand) ? offHand : null);
                if (batteryHold != null) {
                    String newType = RobotBatteryUtil.getBatteryType(batteryHold);
                    int newEnergy = RobotBatteryUtil.getBatteryEnergy(batteryHold);
                    String oldType = data.getBatteryType();
                    int oldEnergy = data.getEnergy();

                    // Return old battery if exists
                    if (oldType != null && !"none".equals(oldType)) {
                        ItemStack oldBattery = RobotBatteryUtil.createBattery(oldType, oldEnergy);
                        if (oldBattery != null) {
                            player.getInventory().addItem(oldBattery);
                        }
                    }

                    data.setBatteryType(newType);
                    data.setEnergy(newEnergy);
                    data.saveTo(living, plugin);
                    robot.updateCustomName();

                    if (player.getGameMode() != GameMode.CREATIVE) {
                        batteryHold.subtract(1);
                    }

                    living.getWorld().playSound(living.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 1.5f);
                    living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getLocation().add(0, 0.8, 0), 20, 0.5, 0.5, 0.5, 0.1);
                    player.sendMessage("§a§l[Robot] §eĐã nạp Pin §a(" + newType.toUpperCase() + ") §evào Robot! Năng lượng: §a" + String.format("%,d", newEnergy) + "§7/§f" + String.format("%,d", data.getMaxEnergy()) + " EU");
                    return;
                }
            }

            if (hasTablet) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (isTabletItem(mainHand)) syncTabletItem(mainHand, robot);
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (isTabletItem(offHand)) syncTabletItem(offHand, robot);
                openRobotDashboard(player, robot);
                return;
            }

            // If player right clicks robot without tablet while in SPEED or THRUST mode -> Mount
            if (data.getActiveTask() == RobotTask.SPEED || data.getActiveTask() == RobotTask.THRUST) {
                if (robot.isRider(player)) {
                    return;
                }
                robot.mountRider(player);
                return;
            }

            // Entity right-clicked without tablet: Dashboard is opened exclusively via Tablet
            player.sendMessage("§e§l[Robot] §7Sử dụng §bTablet Robot §7(chuột phải) để mở Bảng điều khiển từ xa.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        }
    }

    public LunarRobotEntity getPlayerMountedRobot(Player player) {
        if (player == null) return null;
        for (LunarRobotEntity robot : activeRobots.values()) {
            if (robot.isRider(player)) {
                return robot;
            }
        }
        return null;
    }



    public LunarRobotEntity getRobotByModel(com.ticxo.modelengine.api.model.ActiveModel model) {
        if (model == null) return null;
        for (LunarRobotEntity robot : activeRobots.values()) {
            if (robot.getActiveModel() == model) {
                return robot;
            }
        }
        return null;
    }

    public LunarRobotEntity getRobotByFallbackSeat(Entity seat) {
        if (seat == null) return null;
        for (LunarRobotEntity robot : activeRobots.values()) {
            if (robot.getFallbackSeat() == seat) {
                return robot;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onModelDismount(com.ticxo.modelengine.api.events.ModelDismountEvent event) {
        if (event.getPassenger() instanceof Player player) {
            LunarRobotEntity robot = getRobotByModel(event.getVehicle());
            if (robot != null) {
                robot.onRiderDismounted(player);
                player.sendMessage("§b§l[Robot] §eBạn đã rời khỏi Robot.");
                player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 0.8f, 1.4f);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            LunarRobotEntity robot = getRobotByFallbackSeat(event.getDismounted());
            if (robot != null) {
                robot.onRiderDismounted(player);
                player.sendMessage("§b§l[Robot] §eBạn đã rời khỏi Robot.");
                player.playSound(player.getLocation(), Sound.ENTITY_HORSE_SADDLE, 0.8f, 1.4f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // 1. Prevent base wolf from dealing default vanilla bite damage
        if (damager instanceof LivingEntity l && isRobotEntity(l)) {
            LunarRobotEntity robot = activeRobots.get(l.getUniqueId());
            if (robot != null && !robot.isCustomAttacking()) {
                event.setCancelled(true);
                return;
            }
        }

        // 2. Trigger robot combat when owner attacks any other entity (mob or player)
        Player owner = null;
        if (damager instanceof Player p) {
            owner = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            owner = p;
        }

        if (owner != null && victim instanceof LivingEntity target && !(victim instanceof ArmorStand) && !victim.equals(owner)) {
            LunarRobotEntity robot = getPlayerRobot(owner.getUniqueId());
            if (robot != null && robot.getEntity() != victim && robot.getData().getActiveTask() == RobotTask.COMBAT && robot.getData().getEnergy() > 0) {
                if (robot.getRider() == null && target.isValid() && !target.isDead() && (target.getHealth() - event.getFinalDamage() > 0)) {
                    robot.setCombatTarget(target);
                }
            }
            return;
        }

        // 3. Bodyguard trigger: if owner is attacked by another living entity, robot assists
        if (victim instanceof Player p && damager instanceof LivingEntity hostile && !(damager instanceof ArmorStand)) {
            LunarRobotEntity robot = getPlayerRobot(p.getUniqueId());
            if (robot != null && robot.getEntity() != hostile && robot.getData().getActiveTask() == RobotTask.COMBAT && robot.getData().getEnergy() > 0) {
                if (robot.getRider() == null && hostile.isValid() && !hostile.isDead() && robot.getCombatTarget() == null) {
                    robot.setCombatTarget(hostile);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!isRobotEntity(living)) return;

        LunarRobotEntity robot = activeRobots.get(living.getUniqueId());
        if (robot == null) return;

        // Triệt tiêu sát thương rơi (fall damage)
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }

        // Cập nhật máu vào data và cập nhật custom name tức thì
        double damageTaken = event.getFinalDamage();
        double newHealth = Math.max(0.0, living.getHealth() - damageTaken);
        robot.getData().setHealth(newHealth);
        robot.getData().addDamageTaken(damageTaken);
        robot.updateCustomName();

        // Robotic Hurt SFX: Metallic impact and electric spark
        living.getWorld().playSound(living.getLocation(), Sound.BLOCK_COPPER_GRATE_HIT, 0.9f, 1.4f);
        living.getWorld().playSound(living.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f);
        living.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, living.getLocation().add(0, 0.5, 0), 6, 0.2, 0.2, 0.2, 0.1);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isRobotEntity(event.getEntity())) return;
        LivingEntity living = event.getEntity();
        LunarRobotEntity robot = activeRobots.remove(living.getUniqueId());
        if (robot != null) {
            robot.cleanup();
        }

        // Robotic Destruction SFX
        living.getWorld().playSound(living.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        living.getWorld().playSound(living.getLocation(), Sound.BLOCK_COPPER_BREAK, 1.0f, 1.2f);
        living.getWorld().playSound(living.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);

        // Close any active puzzle minigames for this robot immediately
        for (var entry : activePuzzles.entrySet()) {
            LunarRobotPuzzleUi puzzle = entry.getValue();
            if (puzzle.getRobot() != null && puzzle.getRobot().getEntity().getUniqueId().equals(living.getUniqueId())) {
                puzzle.close();
                Player p = puzzle.getPlayer();
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c§l[Robot] §cKết nối thất bại! Robot mục tiêu đã bị tiêu diệt.");
                }
                activePuzzles.remove(entry.getKey());
            }
        }

        // Close any active dashboards for this robot immediately
        for (var entry : activeDashboards.entrySet()) {
            LunarRobotDashboardUi dashboard = entry.getValue();
            if (dashboard.getRobot() != null && dashboard.getRobot().getEntity().getUniqueId().equals(living.getUniqueId())) {
                dashboard.close();
                Player p = dashboard.getPlayer();
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c§l[Robot] §cMất kết nối hoàn toàn! Robot của bạn đã bị phá hủy.");
                }
                activeDashboards.remove(entry.getKey());
            }
        }

        LunarRobotData data = robot != null ? robot.getData() : LunarRobotData.loadFrom(living, plugin);
        if (data != null && data.isTamed() && data.getOwnerUuid() != null) {
            UUID ownerUuid = data.getOwnerUuid();
            String robotName = data.getName() != null ? data.getName() : "Robot 4 Chân";
            String cause = getDeathCauseDescription(living);

            // 1. Unlink in memory map
            playerRobotMap.remove(ownerUuid);

            // 2. Clear owner data on entity
            data.setOwnerUuid(null);
            data.setOwnerName(null);
            data.saveTo(living, plugin);

            // 3. Notify owner & unlink on Player if online, or queue notice if offline
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                unlinkPlayerRobot(owner, living.getUniqueId());
                sendRobotDestroyedNotification(owner, robotName, cause);
            } else {
                pendingDeathNotices.put(ownerUuid, new DeathNotice(robotName, cause, living.getUniqueId()));
            }
        }

        // Drop installed battery and modules on robot destruction
        if (data != null) {
            if (data.getBatteryType() != null && !"none".equals(data.getBatteryType())) {
                ItemStack batDrop = RobotBatteryUtil.createBattery(data.getBatteryType(), data.getEnergy());
                if (batDrop != null) {
                    living.getWorld().dropItemNaturally(living.getLocation(), batDrop);
                }
            }
            if (data.getModule1Id() != null) {
                ItemStack m1 = HaoHanItemCore.get().getItemFactory().create(data.getModule1Id(), 1);
                if (m1 != null) {
                    applyEfficiency(m1, data.getModule1Efficiency());
                    living.getWorld().dropItemNaturally(living.getLocation(), m1);
                }
            }
            if (data.getModule2Id() != null) {
                ItemStack m2 = HaoHanItemCore.get().getItemFactory().create(data.getModule2Id(), 1);
                if (m2 != null) {
                    applyEfficiency(m2, data.getModule2Efficiency());
                    living.getWorld().dropItemNaturally(living.getLocation(), m2);
                }
            }
        }


    }

    // --- Maintenance Crafting: Module + Diamond / Netherite Ingot ---
    @EventHandler
    public void onCraftMaintenance(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        ItemStack moduleItem = null;
        ItemStack materialItem = null;
        int count = 0;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;
            count++;
            if (isModuleItem(item)) {
                moduleItem = item;
            } else if (item.getType() == Material.DIAMOND || item.getType() == Material.NETHERITE_INGOT) {
                materialItem = item;
            }
        }

        if (count == 2 && moduleItem != null && materialItem != null) {
            double currentEff = getModuleEfficiency(moduleItem);
            double bonus = materialItem.getType() == Material.NETHERITE_INGOT ? 70.0 : 30.0;
            double newEff = Math.min(100.0, currentEff + bonus);

            ItemStack result = moduleItem.clone();
            result.setAmount(1);
            applyEfficiency(result, newEff);
            inv.setResult(result);
        }
    }

    public void tameRobot(Player player, LunarRobotEntity robot) {
        LunarRobotData data = robot.getData();

        // Strict Ownership Protection: Robot already tamed
        if (data.isTamed()) {
            if (!player.getUniqueId().equals(data.getOwnerUuid())) {
                player.sendMessage("§c§l[Robot Đã Khóa] §7Robot này đã thuộc về người chơi §f" + (data.getOwnerName() != null ? data.getOwnerName() : "khác") + "§7! Không thể thuần phục lại.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            return;
        }

        // Enforce 1 player = 1 robot
        UUID existing = getPlayerRobotUuid(player);
        if (existing != null && !existing.equals(robot.getEntity().getUniqueId())) {
            player.sendMessage("§c§l[Tablet Robot] §cBạn đã sở hữu một robot khác rồi! Hãy dùng Tablet hủy liên kết robot cũ trước khi thuần hóa con mới.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        data.setOwnerUuid(player.getUniqueId());
        data.setOwnerName(player.getName());
        data.setFollowOwner(true);

        if (robot.getEntity() instanceof Wolf wolf) {
            wolf.setOwner(player);
            wolf.setTamed(true);
            wolf.setSitting(false);
        }

        data.saveTo(robot.getEntity(), plugin);
        playerRobotMap.put(player.getUniqueId(), robot.getEntity().getUniqueId());
        saveRobotLocationToPlayer(player, robot);
        robot.updateCustomName();

        // Sync tablet in hand
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isTabletItem(main)) syncTabletItem(main, robot);
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isTabletItem(off)) syncTabletItem(off, robot);

        player.sendMessage("§a§l[Tablet Robot] §eThuần phục thành công! Robot 4 Chân đã liên kết với ID của bạn.");
    }

    public void unbindRobot(Player player, LunarRobotEntity robot) {
        LunarRobotData data = robot.getData();
        if (data.isTamed() && !player.getUniqueId().equals(data.getOwnerUuid())) {
            player.sendMessage("§c§l[Robot] §cBạn không phải chủ sở hữu của robot này!");
            return;
        }

        data.setOwnerUuid(null);
        data.setOwnerName(null);
        data.setActiveTask(RobotTask.IDLE);

        if (robot.getEntity() instanceof Wolf wolf) {
            wolf.setTamed(false);
            wolf.setOwner(null);
        }

        data.saveTo(robot.getEntity(), plugin);
        unlinkPlayerRobot(player, robot.getEntity().getUniqueId());
        robot.updateCustomName();

        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        player.sendMessage("§6§l[Robot] §eĐã hủy liên kết Robot 4 Chân. Robot đã trở về trạng thái hoang dã!");
    }

    public void unlinkPlayerRobot(Player player, UUID robotUuid) {
        if (player == null) return;
        playerRobotMap.remove(player.getUniqueId());

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(new NamespacedKey(plugin, "owned_robot_id"));
        pdc.remove(new NamespacedKey(plugin, "robot_loc_world"));
        pdc.remove(new NamespacedKey(plugin, "robot_loc_x"));
        pdc.remove(new NamespacedKey(plugin, "robot_loc_y"));
        pdc.remove(new NamespacedKey(plugin, "robot_loc_z"));

        // Reset any tablet in player inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isTabletItem(item)) {
                UUID linked = getLinkedRobotUuid(item);
                if (linked == null || robotUuid == null || linked.equals(robotUuid)) {
                    resetTabletItem(item);
                }
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && isTabletItem(off)) {
            UUID linked = getLinkedRobotUuid(off);
            if (linked == null || robotUuid == null || linked.equals(robotUuid)) {
                resetTabletItem(off);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && isTabletItem(cursor)) {
            UUID linked = getLinkedRobotUuid(cursor);
            if (linked == null || robotUuid == null || linked.equals(robotUuid)) {
                resetTabletItem(cursor);
            }
        }
        player.updateInventory();
    }

    public void sendRobotDestroyedNotification(Player owner, String robotName, String cause) {
        if (owner == null) return;
        owner.sendMessage("§c§l═════════════════════════════════════════");
        owner.sendMessage("§4§l[CẢNH BÁO TÍN HIỆU] §cRobot 4 Chân §f" + robotName + " §cđã bị phá hủy " + cause + "!");
        owner.sendMessage("§7Hệ thống đã tự động §eHỦY LIÊN KẾT §7Robot. Bạn có thể thuần phục một Robot mới.");
        owner.sendMessage("§c§l═════════════════════════════════════════");
        owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.7f);
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
        owner.sendTitle("§4§lTÍN HIỆU BỊ NGẮT", "§cRobot của bạn đã bị phá hủy!", 10, 60, 20);
    }

    private String getDeathCauseDescription(LivingEntity entity) {
        EntityDamageEvent lastDamage = entity.getLastDamageCause();
        if (lastDamage == null) {
            return "bởi một tác nhân bên ngoài";
        }
        if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            if (damager instanceof Player killer) {
                return "bởi người chơi §e" + killer.getName() + "§c";
            } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
                if (shooter instanceof Player killer) {
                    return "bởi người chơi §e" + killer.getName() + "§c";
                }
                return "bởi §e" + shooter.getName() + "§c";
            } else {
                return "bởi quái vật §e" + damager.getName() + "§c";
            }
        }
        return switch (lastDamage.getCause()) {
            case LAVA -> "do rơi vào dung nham";
            case FIRE, FIRE_TICK -> "do bị thiêu cháy";
            case VOID -> "do rơi vào hư không";
            case FALL -> "do va đập rơi từ trên cao";
            case SUFFOCATION -> "do bị kẹt ngạt thở";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "do một vụ nổ lớn";
            default -> "do tác động từ môi trường";
        };
    }

    public void openPuzzleMinigame(Player player, LunarRobotEntity robot) {
        if (robot.getData().isTamed()) {
            player.sendMessage("§c§l[Robot Đã Khóa] §7Robot này đã thuộc sở hữu của §f" + (robot.getData().getOwnerName() != null ? robot.getData().getOwnerName() : "người chơi khác") + "§7, không thể can thiệp!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        if (robot.isFrozen()) {
            player.sendMessage("§c§l[Robot] §eRobot này đang được người chơi khác giải mã!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        if (getUiService() == null) {
            player.sendMessage("§c[Lỗi] HaoHanDisplayUI chưa sẵn sàng!");
            return;
        }
        LunarRobotPuzzleUi old = activePuzzles.remove(player.getUniqueId());
        if (old != null) {
            old.close();
        }
        LunarRobotPuzzleUi puzzle = new LunarRobotPuzzleUi(plugin, getUiService(), this, player, robot);
        activePuzzles.put(player.getUniqueId(), puzzle);
        puzzle.open();
    }

    public void unregisterActivePuzzle(UUID playerUuid) {
        activePuzzles.remove(playerUuid);
    }

    public void openRobotDashboard(Player player, LunarRobotEntity robot) {
        if (getUiService() == null) {
            player.sendMessage("§c[Lỗi] HaoHanDisplayUI chưa sẵn sàng!");
            return;
        }
        LunarRobotDashboardUi existing = activeDashboards.get(player.getUniqueId());
        if (existing != null && existing.getState() != LunarRobotDashboardUi.UiState.CLOSING) {
            return;
        }
        LunarRobotDashboardUi old = activeDashboards.remove(player.getUniqueId());
        if (old != null) {
            old.close();
        }
        LunarRobotDashboardUi dashboard = new LunarRobotDashboardUi(plugin, getUiService(), this, player, robot);
        activeDashboards.put(player.getUniqueId(), dashboard);
        dashboard.open();
    }

    public void unregisterActiveDashboard(UUID playerUuid) {
        activeDashboards.remove(playerUuid);
    }

    public LunarRobotDashboardUi getActiveDashboard(UUID playerUuid) {
        return activeDashboards.get(playerUuid);
    }

    public LunarRobotDashboardUi openDebugDashboard(Player player, LunarRobotDashboardUi.Tab initialTab) {
        if (getUiService() == null) {
            player.sendMessage("§c[Lỗi] HaoHanDisplayUI chưa sẵn sàng!");
            return null;
        }

        LunarRobotEntity robot = resolveRobotEntity(player, null);
        LunarRobotDashboardUi old = activeDashboards.remove(player.getUniqueId());
        if (old != null) {
            old.closeImmediate();
        }

        LunarRobotDashboardUi dashboard = new LunarRobotDashboardUi(plugin, getUiService(), this, player, robot);
        activeDashboards.put(player.getUniqueId(), dashboard);
        dashboard.open();
        if (initialTab != null) {
            dashboard.setCurrentTabAndRefresh(initialTab);
        }
        return dashboard;
    }

    public void openRobotInventory(Player player, LunarRobotEntity robot) {
        LunarRobotInventoryUi inv = new LunarRobotInventoryUi(plugin, this, player, robot);
        plugin.getServer().getPluginManager().registerEvents(inv, plugin);
        inv.open();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddToWorld(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living && isRobotAlive(living)) {
            getOrRegisterRobot(living);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            LunarRobotEntity robot = activeRobots.remove(living.getUniqueId());
            if (robot != null) {
                robot.cleanup();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if there is a pending death notification from when player was offline
        DeathNotice notice = pendingDeathNotices.remove(player.getUniqueId());
        if (notice != null) {
            unlinkPlayerRobot(player, notice.robotUuid());
            sendRobotDestroyedNotification(player, notice.robotName(), notice.cause());
            return;
        }

        String saved = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "owned_robot_id"), PersistentDataType.STRING);
        if (saved != null && !saved.isBlank()) {
            try {
                UUID robotId = UUID.fromString(saved);
                playerRobotMap.put(player.getUniqueId(), robotId);
            } catch (Exception ignored) {}
        }

        // Check if robot should reunite with player on join
        handleOwnerWorldChange(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        closeActiveDashboard(player.getUniqueId());
        LunarRobotPuzzleUi puzzle = activePuzzles.remove(player.getUniqueId());
        if (puzzle != null) {
            puzzle.close();
        }
        handleOwnerWorldChange(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || from.getWorld() == null) return;

        boolean worldChanged = !from.getWorld().equals(to.getWorld());
        boolean farTeleport = !worldChanged && from.distanceSquared(to) > 400.0;
        if (worldChanged || farTeleport) {
            Player player = event.getPlayer();
            // Pre-save robot location before old world/chunk unloads
            UUID robotId = playerRobotMap.get(player.getUniqueId());
            if (robotId != null) {
                LunarRobotEntity robot = activeRobots.get(robotId);
                if (robot != null && robot.getEntity().isValid()) {
                    saveRobotLocationToPlayer(player, robot);
                }
            }
            if (worldChanged) {
                closeActiveDashboard(player.getUniqueId());
                LunarRobotPuzzleUi puzzle = activePuzzles.remove(player.getUniqueId());
                if (puzzle != null) {
                    puzzle.close();
                }
            }
            handleOwnerWorldChange(player);
        }
    }

    public void handleOwnerWorldChange(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!plugin.isEnabled()) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            UUID robotId = playerRobotMap.get(player.getUniqueId());
            if (robotId == null) {
                String saved = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "owned_robot_id"), PersistentDataType.STRING);
                if (saved != null && !saved.isBlank()) {
                    try {
                        robotId = UUID.fromString(saved);
                    } catch (Exception ignored) {}
                }
            }

            LunarRobotEntity robot = resolveRobotEntity(player, robotId);
            if (robot == null || robot.getEntity() == null || !robot.getEntity().isValid()) {
                return;
            }

            LunarRobotData data = robot.getData();
            if (!data.isTamed() || !player.getUniqueId().equals(data.getOwnerUuid())) {
                return;
            }
            if (!data.isFollowOwner() || data.isSitting()) {
                return; // Follow disabled or robot is sitting/staying
            }

            LivingEntity entity = robot.getEntity();
            if (!entity.getWorld().equals(player.getWorld())) {
                robot.teleportToOwner(player);
            } else {
                double distSq = entity.getLocation().distanceSquared(player.getLocation());
                if (distSq > 400.0) {
                    robot.teleportToOwner(player);
                }
            }
        }, 2L);
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();
        LunarRobotEntity mounted = getPlayerMountedRobot(player);
        if (mounted != null) {
            mounted.dismountRider();
        }
        lastInteractMap.remove(player.getUniqueId());
        LunarRobotDashboardUi dashboard = activeDashboards.remove(player.getUniqueId());
        if (dashboard != null) {
            dashboard.close();
        }
        LunarRobotPuzzleUi puzzle = activePuzzles.remove(player.getUniqueId());
        if (puzzle != null) {
            puzzle.close();
        }
        UUID robotId = playerRobotMap.get(player.getUniqueId());
        if (robotId != null) {
            LunarRobotEntity owned = activeRobots.get(robotId);
            if (owned != null && owned.getEntity().isValid()) {
                saveRobotLocationToPlayer(player, owned);
                owned.getData().saveTo(owned.getEntity(), plugin);
            }
        }
    }

    public void closeActiveDashboard(UUID playerUuid) {
        LunarRobotDashboardUi dashboard = activeDashboards.remove(playerUuid);
        if (dashboard != null) {
            dashboard.close();
        }
    }

    public boolean isHoldingTablet(Player player) {
        if (player == null || !player.isOnline()) return false;
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return isTabletItem(main) || isTabletItem(off);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!activeDashboards.containsKey(player.getUniqueId())) return;
        if (!plugin.isEnabled()) return;

        // If player drops the tablet itself and has no other tablet, close dashboard
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!hasTabletInInventory(player)) {
                closeActiveDashboard(player.getUniqueId());
            }
        });
    }

    private boolean hasTabletInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isTabletItem(item)) return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        closeActiveDashboard(player.getUniqueId());
        LunarRobotPuzzleUi puzzle = activePuzzles.remove(player.getUniqueId());
        if (puzzle != null) {
            puzzle.close();
        }
    }

    public void scanLoadedEntities() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LivingEntity living && isRobotAlive(living)) {
                    getOrRegisterRobot(living);
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Đã khôi phục " + count + " Robot 4 Chân vào bộ nhớ!");
        }
    }

    public void cleanupAll() {
        for (LunarRobotPuzzleUi puzzle : activePuzzles.values()) {
            puzzle.close();
        }
        activePuzzles.clear();
        for (LunarRobotDashboardUi dashboard : activeDashboards.values()) {
            dashboard.closeImmediate();
        }
        activeDashboards.clear();
        for (LunarRobotEntity robot : activeRobots.values()) {
            if (robot.getEntity().isValid()) {
                robot.getData().saveTo(robot.getEntity(), plugin);
                Player owner = robot.getOnlineOwner();
                if (owner != null) {
                    saveRobotLocationToPlayer(owner, robot);
                }
            }
            robot.cleanup();
        }
        activeRobots.clear();
        playerRobotMap.clear();
        lastInteractMap.clear();
    }

    public UUID getLinkedRobotUuid(ItemStack tablet) {
        if (tablet == null || !tablet.hasItemMeta()) return null;
        PersistentDataContainer pdc = tablet.getItemMeta().getPersistentDataContainer();
        String idStr = pdc.get(new NamespacedKey(plugin, "linked_robot_uuid"), PersistentDataType.STRING);
        if (idStr == null) {
            idStr = pdc.get(new NamespacedKey("haohan", "linked_robot_uuid"), PersistentDataType.STRING);
        }
        if (idStr != null) {
            try {
                return UUID.fromString(idStr);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public void syncTabletItem(ItemStack tablet, LunarRobotEntity robot) {
        if (tablet == null || tablet.getType() == Material.AIR || !tablet.hasItemMeta()) return;
        ItemMeta meta = tablet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "linked_robot_uuid"), PersistentDataType.STRING, robot.getEntity().getUniqueId().toString());
        pdc.set(new NamespacedKey("haohan", "linked_robot_uuid"), PersistentDataType.STRING, robot.getEntity().getUniqueId().toString());

        String name = robot.getData().getName();
        String shortId = robot.getEntity().getUniqueId().toString().substring(0, 8);
        int integrity = (int) Math.round(robot.getData().getIntegrityPercentage());
        String integrityColor = integrity > 50 ? "§a" : (integrity > 25 ? "§e" : "§c");

        List<String> lore = new ArrayList<>();
        lore.add("§7Thiết bị kết nối và điều khiển từ xa cho Robot 4 Chân.");
        lore.add("§a✔ Tín hiệu: §eĐÃ KẾT NỐI §8(§b" + name + "§8)");
        lore.add("§8▪ ID: §7#" + shortId);
        lore.add("§8▪ Độ hoàn thiện: " + integrityColor + integrity + "% §8(" + String.format("%.1f", robot.getData().getHealth()) + "/" + String.format("%.1f", robot.getData().getMaxHealth()) + " HP)");
        lore.add("§8▪ Năng lượng: §e" + robot.getData().getEnergy() + "/" + robot.getData().getMaxEnergy() + " EU");
        lore.add("§8▪ Chế độ: " + robot.getData().getActiveTask().getFormattedName());
        lore.add("§8▪ Chuột phải: Mở Bảng điều khiển từ xa.");
        lore.add("§8▪ Quản lý/Hủy liên kết: Thao tác trong Dashboard.");
        meta.setLore(lore);
        tablet.setItemMeta(meta);
    }

    private boolean isRepairMaterial(ItemStack item) {
        return item != null && item.getType() == Material.IRON_INGOT;
    }

    public void resetTabletItem(ItemStack tablet) {
        if (tablet == null || tablet.getType() == Material.AIR || !tablet.hasItemMeta()) return;
        ItemMeta meta = tablet.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(new NamespacedKey(plugin, "linked_robot_uuid"));
        pdc.remove(new NamespacedKey("haohan", "linked_robot_uuid"));

        List<String> lore = new ArrayList<>();
        lore.add("§7Thiết bị kết nối và điều khiển từ xa cho Robot 4 Chân.");
        lore.add("§c✖ Tín hiệu: §7Chưa kết nối");
        lore.add("§8▪ Chuột phải vào Robot hoang dã để giải mã kết nối.");
        lore.add("§8▪ Chuột phải khi đã liên kết để mở Dashboard quản lý.");
        meta.setLore(lore);
        tablet.setItemMeta(meta);
    }

    public boolean isTabletItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;

        try {
            if (HaoHanItemCore.get().getItemService().isItem(item, "haohan:robot_tablet")) {
                return true;
            }
        } catch (Throwable ignored) {}

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id1 = pdc.get(new NamespacedKey("haohanitemcore", "item_id"), PersistentDataType.STRING);
        if ("haohan:robot_tablet".equals(id1)) return true;

        String id2 = pdc.get(new NamespacedKey("haohan", "item_id"), PersistentDataType.STRING);
        if ("haohan:robot_tablet".equals(id2)) return true;

        String id3 = pdc.get(new NamespacedKey(plugin, "item_id"), PersistentDataType.STRING);
        if ("haohan:robot_tablet".equals(id3)) return true;

        if (pdc.has(new NamespacedKey(plugin, "linked_robot_uuid"), PersistentDataType.STRING)) return true;
        if (pdc.has(new NamespacedKey("haohan", "linked_robot_uuid"), PersistentDataType.STRING)) return true;

        if (meta.hasCustomModelData() && meta.getCustomModelData() == 6001) return true;

        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName().toLowerCase();
            if (name.contains("bảng điều khiển robot") || name.contains("tablet robot") || name.contains("tablet")) return true;
        }

        if (meta.displayName() != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName()).toLowerCase();
            if (plain.contains("bảng điều khiển robot") || plain.contains("tablet robot") || plain.contains("tablet")) return true;
        }

        return false;
    }

    private boolean isModuleItem(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            if (id != null && id.contains("haohan:robot_module_")) return true;
        } catch (Throwable ignored) {}
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id1 = pdc.get(new NamespacedKey("haohanitemcore", "item_id"), PersistentDataType.STRING);
        if (id1 != null && id1.contains("haohan:robot_module_")) return true;
        String id2 = pdc.get(new NamespacedKey("haohan", "item_id"), PersistentDataType.STRING);
        return id2 != null && id2.contains("haohan:robot_module_");
    }

    private double getModuleEfficiency(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 100.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Double eff = pdc.get(new NamespacedKey("haohanitemcore", "module_efficiency"), PersistentDataType.DOUBLE);
        if (eff == null) {
            eff = pdc.get(new NamespacedKey("haohan", "module_efficiency"), PersistentDataType.DOUBLE);
        }
        return eff != null ? eff : 100.0;
    }

    private void applyEfficiency(ItemStack item, double eff) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey("haohanitemcore", "module_efficiency"), PersistentDataType.DOUBLE, eff);
            pdc.set(new NamespacedKey("haohan", "module_efficiency"), PersistentDataType.DOUBLE, eff);
            List<String> lore = meta.getLore();
            if (lore != null) {
                lore.removeIf(l -> l.contains("Độ hiệu quả:"));
                lore.add("§7Độ hiệu quả: §a" + String.format("%.1f", eff) + "%");
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
    }

    public void handleSpawnRobotCommand(io.papermc.paper.command.brigadier.CommandSourceStack source) {
        org.bukkit.command.CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này!");
            return;
        }
        Location loc = player.getLocation();
        LunarRobotEntity robot = spawnWildRobot(loc);
        if (robot != null) {
            player.sendMessage("§a§l[Lunar Robot] §eĐã triệu hồi Robot 4 Chân tại vị trí của bạn!");
            player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        }
    }

    public void handleTabletCommand(io.papermc.paper.command.brigadier.CommandSourceStack source) {
        org.bukkit.command.CommandSender sender = source.getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này!");
            return;
        }
        ItemStack tablet = null;
        try {
            tablet = HaoHanItemCore.get().getItemFactory().create("haohan:robot_tablet");
        } catch (Throwable ignored) {}

        if (tablet == null) {
            tablet = new ItemStack(Material.CARROT_ON_A_STICK);
            ItemMeta meta = tablet.getItemMeta();
            meta.setDisplayName("§b§lBảng Điều Khiển Robot 4 Chân §7(Tablet)");
            meta.setCustomModelData(6001);
            meta.getPersistentDataContainer().set(new NamespacedKey("haohan", "item_id"), PersistentDataType.STRING, "haohan:robot_tablet");
            tablet.setItemMeta(meta);
        }
        player.getInventory().addItem(tablet);
        player.sendMessage("§a§l[Lunar Robot] §eĐã nhận Bảng Điều Khiển Tablet!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    }

    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("spawnrobot")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này!");
                return true;
            }
            Location loc = player.getLocation();
            LunarRobotEntity robot = spawnWildRobot(loc);
            if (robot != null) {
                player.sendMessage("§a§l[Lunar Robot] §eĐã triệu hồi Robot 4 Chân tại vị trí của bạn!");
                player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }
            return true;
        } else if (command.getName().equalsIgnoreCase("robottablet")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này!");
                return true;
            }
            ItemStack tablet = null;
            try {
                tablet = HaoHanItemCore.get().getItemFactory().create("haohan:robot_tablet");
            } catch (Throwable ignored) {}

            if (tablet == null) {
                tablet = new ItemStack(Material.CARROT_ON_A_STICK);
                ItemMeta meta = tablet.getItemMeta();
                meta.setDisplayName("§b§lBảng Điều Khiển Robot 4 Chân §7(Tablet)");
                meta.setCustomModelData(6001);
                meta.getPersistentDataContainer().set(new NamespacedKey("haohan", "item_id"), PersistentDataType.STRING, "haohan:robot_tablet");
                tablet.setItemMeta(meta);
            }
            player.getInventory().addItem(tablet);
            player.sendMessage("§a§l[Lunar Robot] §eĐã nhận Bảng Điều Khiển Tablet!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            return true;
        }
        return false;
    }
}

