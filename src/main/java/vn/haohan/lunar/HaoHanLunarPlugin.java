package vn.haohan.lunar;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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
        return world != null && LUNAR_WORLD_KEY.equals(world.getKey());
    }

    /**
     * Retrieves the loaded Lunar dimension world, or null if not loaded.
     *
     * @return lunar world instance or null
     */
    public static World getLunarWorld() {
        return Bukkit.getWorld(LUNAR_WORLD_KEY);
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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
                miningMechanic.tick();
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

    public LunarRobotMechanic getLunarRobotMechanic() {
        return lunarRobotMechanic;
    }

    public ModelEngineDeathListener getModelEngineDeathListener() {
        return modelEngineDeathListener;
    }

    public BatteryChargerMechanic getBatteryChargerMechanic() {
        return batteryChargerMechanic;
    }
}
