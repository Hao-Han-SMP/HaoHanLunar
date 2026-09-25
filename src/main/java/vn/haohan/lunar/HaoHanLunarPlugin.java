package vn.haohan.lunar;

import vn.haohan.lunar.data.PlayerLunarDataManager;
import vn.haohan.lunar.item.LunarItems;
import vn.haohan.lunar.mechanics.GravityMechanic;
import vn.haohan.lunar.mechanics.MiningMechanic;
import vn.haohan.lunar.mechanics.OxygenMechanic;
import vn.haohan.lunar.mechanics.VisualMechanic;
import vn.haohan.lunar.mechanics.BeaconShieldMechanic;
import vn.haohan.lunar.mechanics.LunarSurfaceSpreadMechanic;
import vn.haohan.lunar.mechanics.TelescopeMechanic;
import vn.haohan.lunar.mechanics.LunarWardenMechanic;
import vn.haohan.lunar.mechanics.ModelEngineDeathListener;
import vn.haohan.lunar.robot.LunarRobotMechanic;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

public final class HaoHanLunarPlugin extends JavaPlugin {

    private static HaoHanLunarPlugin instance;

    private PlayerLunarDataManager lunarDataManager;
    private GravityMechanic gravityMechanic;
    private OxygenMechanic oxygenMechanic;
    private MiningMechanic miningMechanic;
    private VisualMechanic visualMechanic;
    private BeaconShieldMechanic beaconShieldMechanic;
    private LunarSurfaceSpreadMechanic lunarSurfaceSpreadMechanic;
    private TelescopeMechanic telescopeMechanic;
    private LunarWardenMechanic lunarWardenMechanic;
    private LunarRobotMechanic lunarRobotMechanic;
    private ModelEngineDeathListener modelEngineDeathListener;
    private vn.haohan.lunar.charger.BatteryChargerMechanic batteryChargerMechanic;

    public static HaoHanLunarPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize managers
        lunarDataManager = new PlayerLunarDataManager(this);

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
        lunarRobotMechanic = new LunarRobotMechanic(this);
        modelEngineDeathListener = new ModelEngineDeathListener(this);
        batteryChargerMechanic = new vn.haohan.lunar.charger.BatteryChargerMechanic(this);

        // Register event listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(gravityMechanic, this);
        pm.registerEvents(oxygenMechanic, this);
        pm.registerEvents(miningMechanic, this);
        pm.registerEvents(visualMechanic, this);
        pm.registerEvents(beaconShieldMechanic, this);
        pm.registerEvents(lunarSurfaceSpreadMechanic, this);
        pm.registerEvents(telescopeMechanic, this);
        pm.registerEvents(lunarRobotMechanic, this);
        pm.registerEvents(modelEngineDeathListener, this);
        pm.registerEvents(batteryChargerMechanic, this);

        // Start main repeating task (runs every tick)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                gravityMechanic.tick();
                oxygenMechanic.tick();
                miningMechanic.tick();
                visualMechanic.tick();
                beaconShieldMechanic.tick();
                lunarSurfaceSpreadMechanic.tick();
                lunarRobotMechanic.tick();
                batteryChargerMechanic.tick();
            } catch (Exception e) {
                getLogger().warning("Error in tick loop: " + e.getMessage());
            }
        }, 1L, 1L);

        // Register commands with Paper Command API (BasicCommand)
        registerCommand("spawnrobot", "Spawn a Quadruped Lunar Robot", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                lunarRobotMechanic.handleSpawnRobotCommand(source);
            }

            @Override
            public boolean canUse(org.bukkit.command.CommandSender sender) {
                return sender.isOp() || sender.hasPermission("haohan.lunar.admin");
            }
        });

        registerCommand("robottablet", "Give the Robot Tablet controller", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                lunarRobotMechanic.handleTabletCommand(source);
            }

            @Override
            public boolean canUse(org.bukkit.command.CommandSender sender) {
                return sender.isOp() || sender.hasPermission("haohan.lunar.admin");
            }
        });

        registerCommand("spawnwarden", "Spawn TheLunarWarden boss", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                var sender = source.getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Chỉ người chơi mới có thể dùng lệnh này!");
                    return;
                }
                lunarWardenMechanic.spawnWarden(player.getLocation());
                player.sendMessage("§aSpawned TheLunarWarden!");
            }

            @Override
            public boolean canUse(org.bukkit.command.CommandSender sender) {
                return sender.isOp() || sender.hasPermission("haohan.lunar.admin");
            }
        });

        // Scan and restore loaded in-world robots after restart
        lunarRobotMechanic.scanLoadedEntities();

        getLogger().info("HaoHanLunar plugin successfully enabled and hooks registered!");
    }

    @Override
    public void onDisable() {
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

        getLogger().info("HaoHanLunar plugin successfully disabled.");
    }

    public PlayerLunarDataManager getLunarDataManager() {
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

    public LunarRobotMechanic getLunarRobotMechanic() {
        return lunarRobotMechanic;
    }

    public ModelEngineDeathListener getModelEngineDeathListener() {
        return modelEngineDeathListener;
    }

    public vn.haohan.lunar.charger.BatteryChargerMechanic getBatteryChargerMechanic() {
        return batteryChargerMechanic;
    }
}
