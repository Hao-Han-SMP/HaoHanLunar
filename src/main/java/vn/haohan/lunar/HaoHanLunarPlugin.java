package vn.haohan.lunar;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.lunar.charger.BatteryChargerMechanic;
import vn.haohan.lunar.command.HaoHanCommand;
import vn.haohan.lunar.features.*;
import vn.haohan.lunar.features.beacon.BeaconShieldMechanic;
import vn.haohan.lunar.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.features.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.features.boss.warden.visual.WardenTrailCaptureSystem;
import vn.haohan.lunar.features.weapon.claymore.SmoothSlashTask;
import vn.haohan.lunar.robot.LunarRobotMechanic;
import vn.haohan.engine.core.service.Services;
import vn.haohan.lunar.service.ItemCoreService;
import vn.haohan.engine.core.service.MobCoreService;
import vn.haohan.engine.core.service.PinService;
import vn.haohan.lunar.service.PlayerDataService;
import vn.haohan.engine.api.system.data.PlayerDataManager;
import vn.haohan.lunar.item.LunarItems;


/**
 * Main Paper plugin entry point for HaoHanLunar.
 * Coordinates plugin lifecycle, subsystem initialization, command registration,
 * and the primary 1-tick repeating update loop.
 */
public final class HaoHanLunarPlugin extends JavaPlugin {

    public static final NamespacedKey LUNAR_WORLD_KEY = NamespacedKey.fromString("haohan:lunar");

    private static HaoHanLunarPlugin instance;

    private PlayerDataManager lunarDataManager;
    private GravityMechanic gravityMechanic;
    private OxygenMechanic oxygenMechanic;
    private MiningMechanic miningMechanic;
    private VisualMechanic visualMechanic;
    private BeaconShieldMechanic beaconShieldMechanic;
    private LunarSurfaceSpreadMechanic lunarSurfaceSpreadMechanic;
    private TelescopeMechanic telescopeMechanic;
    private LunarWardenMechanic lunarWardenMechanic;
    private LunarClaymoreMechanic lunarClaymoreMechanic;
    private LunarRobotMechanic lunarRobotMechanic;
    private ModelEngineDeathListener modelEngineDeathListener;
    private BatteryChargerMechanic batteryChargerMechanic;
    private MoonGazingMechanic moonGazingMechanic;

    /**
     * Returns the singleton plugin instance.
     *
     * @return active plugin instance
     */
    public static HaoHanLunarPlugin getInstance() {
        return instance;
    }

    /**
     * Checks if the given world is the Lunar dimension.
     *
     * @param world world to check
     * @return true if world matches the Lunar dimension key
     */
    public static boolean isLunarWorld(World world) {
        if (world == null) return false;
        try {
            if (world.getKey() != null) {
                if (LUNAR_WORLD_KEY.equals(world.getKey())) return true;
                String keyStr = world.getKey().toString();
                if (keyStr.equalsIgnoreCase("haohan:lunar") || keyStr.endsWith(":haohan_lunar") || keyStr.endsWith(":lunar")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            String name = world.getName();
            if (name != null) {
                return name.equalsIgnoreCase("haohan:lunar") || name.equalsIgnoreCase("haohan_lunar") || name.equalsIgnoreCase("lunar");
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Retrieves the loaded Lunar dimension world, or null if not loaded.
     *
     * @return lunar world instance or null
     */
    public static World getLunarWorld() {
        World world = Bukkit.getWorld(LUNAR_WORLD_KEY);
        if (world != null) return world;
        world = Bukkit.getWorld("haohan:lunar");
        if (world != null) return world;
        world = Bukkit.getWorld("haohan_lunar");
        if (world != null) return world;
        return Bukkit.getWorld("lunar");
    }

    /**
     * Retrieves the loaded Lunar dimension world, or attempts to load/create it if not loaded.
     *
     * @return lunar world instance or null if failed
     */
    public static World getOrLoadLunarWorld() {
        World world = getLunarWorld();
        if (world != null) {
            return world;
        }
        try {
            WorldCreator creator = WorldCreator.ofKey(LUNAR_WORLD_KEY);
            org.bukkit.plugin.Plugin worldGenPlugin = Bukkit.getPluginManager().getPlugin("HaoHan-WorldGen");
            if (worldGenPlugin != null && worldGenPlugin.isEnabled()) {
                try {
                    creator.generator(worldGenPlugin.getDefaultWorldGenerator("haohan:lunar", ""));
                } catch (Throwable ignored) {}
            }
            creator.environment(World.Environment.NORMAL);
            world = Bukkit.createWorld(creator);
            if (world != null) {
                world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
                if (instance != null) {
                    instance.getLogger().info("Successfully loaded Lunar dimension world (" + LUNAR_WORLD_KEY + ")");
                }
            }
        } catch (Exception e) {
            if (instance != null) {
                instance.getLogger().warning("Failed to load Lunar dimension world: " + e.getMessage());
            }
        }
        return world;
    }


    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Eagerly pre-load critical mechanic classes and records
        try {
            Class.forName("vn.haohan.lunar.util.MoonUtil");
            Class.forName("vn.haohan.lunar.features.OxygenMechanic$ChunkKey");
        } catch (Throwable ignored) {}

        // Initialize managers
        lunarDataManager = new PlayerDataManager(this);

        // Register custom items with HaoHanItemCore API
        try {
            LunarItems.register();
            getLogger().info("Successfully registered custom items with HaoHanItemCore API.");
        } catch (Exception e) {
            getLogger().severe("Failed to register custom items with HaoHanItemCore! Is it loaded? " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize mechanics
        gravityMechanic = new GravityMechanic(this);
        oxygenMechanic = new OxygenMechanic(this);
        miningMechanic = new MiningMechanic(this);
        visualMechanic = new VisualMechanic(this);
        beaconShieldMechanic = new BeaconShieldMechanic(this);
        lunarSurfaceSpreadMechanic = new LunarSurfaceSpreadMechanic(this);
        telescopeMechanic = new TelescopeMechanic(this);
        lunarWardenMechanic = new LunarWardenMechanic(this);
        lunarClaymoreMechanic = new LunarClaymoreMechanic(this);
        lunarRobotMechanic = new LunarRobotMechanic(this);
        modelEngineDeathListener = new ModelEngineDeathListener(this);
        batteryChargerMechanic = new BatteryChargerMechanic(this);
        moonGazingMechanic = new MoonGazingMechanic(this);

        // Register event listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(gravityMechanic, this);
        pm.registerEvents(oxygenMechanic, this);
        pm.registerEvents(miningMechanic, this);
        pm.registerEvents(visualMechanic, this);
        pm.registerEvents(beaconShieldMechanic, this);
        pm.registerEvents(lunarSurfaceSpreadMechanic, this);
        pm.registerEvents(telescopeMechanic, this);
        pm.registerEvents(lunarClaymoreMechanic, this);
        pm.registerEvents(lunarRobotMechanic, this);
        pm.registerEvents(modelEngineDeathListener, this);
        pm.registerEvents(batteryChargerMechanic, this);
        pm.registerEvents(moonGazingMechanic, this);

        // Register robot commands
        Bukkit.getCommandMap().register("haohan", new BukkitCommand("spawnrobot") {
            {
                setDescription("Triệu hồi Robot 4 Chân hoang dã");
                setPermission("haohan.admin");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return lunarRobotMechanic.onCommand(sender, this, commandLabel, args);
            }
        });

        Bukkit.getCommandMap().register("haohan", new BukkitCommand("robottablet") {
            {
                setDescription("Nhận Tablet điều khiển Robot 4 Chân");
                setPermission("haohan.admin");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return lunarRobotMechanic.onCommand(sender, this, commandLabel, args);
            }
        });


        // Start main repeating task (runs every tick)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                gravityMechanic.tick();
                oxygenMechanic.tick();
                visualMechanic.tick();
                beaconShieldMechanic.tick();
                lunarSurfaceSpreadMechanic.tick();
                WardenTrailCaptureSystem.renderTrails();
                lunarRobotMechanic.tick();
                batteryChargerMechanic.tick();
            } catch (Exception e) {
                getLogger().warning("Error in tick loop: " + e.getMessage());
            }
        }, 1L, 1L);

        // Initialize Lunar Services and commands
        Services.register(new MobCoreService());
        Services.register(new ItemCoreService());
        Services.register(new PlayerDataService());
        Services.register(new PinService());
        Services.init(this);

        // Register main command dispatcher
        new HaoHanCommand().register(this);

        // Scan and restore loaded in-world robots after restart
        lunarRobotMechanic.scanLoadedEntities();

        // Prevent Ender Dragon, Endermen, and unwanted natural mob spawning in Lunar (The End base)
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
            public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
                if (!isLunarWorld(event.getLocation().getWorld())) return;

                org.bukkit.entity.EntityType type = event.getEntityType();
                if (type == org.bukkit.entity.EntityType.ENDER_DRAGON || type == org.bukkit.entity.EntityType.ENDERMAN) {
                    event.setCancelled(true);
                    return;
                }

                org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
                if (reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
                        || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CHUNK_GEN
                        || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT
                        || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL
                        || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                        || reason == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID) {
                    event.setCancelled(true);
                }
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
            public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
                if (!isLunarWorld(event.getLocation().getWorld())) return;

                org.bukkit.entity.EntityType type = event.getEntityType();
                if (type == org.bukkit.entity.EntityType.ENDER_DRAGON
                        || type == org.bukkit.entity.EntityType.ENDERMAN
                        || type == org.bukkit.entity.EntityType.END_CRYSTAL) {
                    event.setCancelled(true);
                    event.getEntity().remove();
                }
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
            public void onPlayerChangeWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
                org.bukkit.entity.Player p = event.getPlayer();
                if (isLunarWorld(p.getWorld())) {
                    suppressDragonBattle(p.getWorld());
                }
            }

            @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
            public void onPlayerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
                if (event.getTo() != null && isLunarWorld(event.getTo().getWorld())) {
                    suppressDragonBattle(event.getTo().getWorld());
                }
            }
        }, this);

        // Ensure Lunar dimension world is loaded
        World lunarWorld = getOrLoadLunarWorld();
        if (lunarWorld != null) {
            lunarWorld.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            lunarWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            lunarWorld.setTime(18000L);
            suppressDragonBattle(lunarWorld);
        }

        getLogger().info("HaoHanLunar plugin successfully enabled and hooks registered!");
    }

    @Override
    public void onDisable() {
        // Clear active smooth slash visual tasks
        SmoothSlashTask.cleanupAll();

        // Clear active skyboxes
        if (visualMechanic != null) {
            visualMechanic.cleanup();
        }

        // Clear and cleanup all active bosses, models and displays
        if (lunarWardenMechanic != null) {
            lunarWardenMechanic.clearAllWardens();
        }
        WardenEntityManager.purgeAllTempEntities();

        // Save in-memory player state
        if (lunarDataManager != null) {
            lunarDataManager.saveAll();
        }
        if (beaconShieldMechanic != null) {
            beaconShieldMechanic.removeAll();
        }
        if (lunarSurfaceSpreadMechanic != null) {
            lunarSurfaceSpreadMechanic.removeAll();
        }
        if (telescopeMechanic != null) {
            telescopeMechanic.removeAllMarkers();
        }
        if (lunarRobotMechanic != null) {
            lunarRobotMechanic.cleanupAll();
        }
        if (batteryChargerMechanic != null) {
            batteryChargerMechanic.cleanupOnDisable();
        }
        if (moonGazingMechanic != null) {
            moonGazingMechanic.cleanup();
        }

        // Clean up low gravity and mining attributes modifiers from players
        if (gravityMechanic != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                gravityMechanic.removeLunarAttributes(player);
                oxygenMechanic.resetPlayerOxygen(player);
            }
        }

        Services.disable(this);
        getLogger().info("HaoHanLunar plugin successfully disabled.");
    }

    public PlayerDataManager getLunarDataManager() {
        return lunarDataManager;
    }

    public GravityMechanic getGravityMechanic() {
        return gravityMechanic;
    }

    public OxygenMechanic getOxygenMechanic() {
        return oxygenMechanic;
    }

    public MiningMechanic getMiningMechanic() {
        return miningMechanic;
    }

    public VisualMechanic getVisualMechanic() {
        return visualMechanic;
    }

    public BeaconShieldMechanic getBeaconShieldMechanic() {
        return beaconShieldMechanic;
    }

    public TelescopeMechanic getTelescopeMechanic() {
        return telescopeMechanic;
    }

    public LunarWardenMechanic getLunarWardenMechanic() {
        return lunarWardenMechanic;
    }

    public LunarClaymoreMechanic getLunarClaymoreMechanic() {
        return lunarClaymoreMechanic;
    }

    public MoonGazingMechanic getMoonGazingMechanic() {
        return moonGazingMechanic;
    }

    public LunarRobotMechanic getLunarRobotMechanic() {
        return lunarRobotMechanic;
    }

    public ModelEngineDeathListener getModelEngineDeathListener() {
        return modelEngineDeathListener;
    }

    public BatteryChargerMechanic getBatteryChargerMechanic() {
        return batteryChargerMechanic;
    }

    public static void suppressDragonBattle(World world) {
        if (world == null || !isLunarWorld(world)) return;
        try {
            org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null) {
                battle.setPreviouslyKilled(true);
                battle.setRespawnPhase(org.bukkit.boss.DragonBattle.RespawnPhase.NONE);
                if (battle.getBossBar() != null) {
                    battle.getBossBar().setVisible(false);
                    battle.getBossBar().removeAll();
                }
                if (battle.getEnderDragon() != null) {
                    battle.getEnderDragon().remove();
                }
            }
        } catch (Throwable ignored) {}

        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            org.bukkit.entity.EntityType type = entity.getType();
            if (type == org.bukkit.entity.EntityType.ENDER_DRAGON
                    || type == org.bukkit.entity.EntityType.ENDERMAN
                    || type == org.bukkit.entity.EntityType.END_CRYSTAL) {
                entity.remove();
            }
        }
    }
}
