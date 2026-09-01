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
import vn.haohan.lunar.mechanics.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.mechanics.boss.warden.WardenSpawner;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenTrailCaptureSystem;
import vn.haohan.lunar.mechanics.LunarClaymoreMechanic;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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
    private LunarClaymoreMechanic lunarClaymoreMechanic;

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
        lunarClaymoreMechanic = new LunarClaymoreMechanic(this);

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

        // Register commands dynamically for Paper plugins
        Bukkit.getCommandMap().register("haohan", new BukkitCommand("spawnwarden") {
            {
                setDescription("Triệu hồi Boss The Lunar Warden");
                setPermission("haohan.admin");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cChỉ có người chơi mới dùng được lệnh này!");
                    return true;
                }
                WardenSpawner.spawnWarden(HaoHanLunarPlugin.this, lunarWardenMechanic, player.getLocation(), player);
                return true;
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
            } catch (Exception e) {
                getLogger().warning("Error in tick loop: " + e.getMessage());
            }
        }, 1L, 1L);

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

    public LunarWardenMechanic getLunarWardenMechanic() {
        return lunarWardenMechanic;
    }

    public LunarClaymoreMechanic getLunarClaymoreMechanic() {
        return lunarClaymoreMechanic;
    }
}
