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
import vn.haohan.lunar.mechanics.boss.warden.showcase.WardenShowcaseHandler;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenTrailCaptureSystem;
import vn.haohan.lunar.mechanics.LunarClaymoreMechanic;
import vn.haohan.lunar.mechanics.weapon.claymore.SmoothSlashTask;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

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
        // 1. /spawnwarden [showcase|clear]
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

                if (args.length > 0) {
                    if (args[0].equalsIgnoreCase("clear")) {
                        int cleared = lunarWardenMechanic.clearAllWardens();
                        player.sendMessage("§a[Lunar Warden] Đã xóa thành công §e" + cleared + " §aboss và toàn bộ thực thể hiển thị!");
                        return true;
                    }
                    if (args[0].equalsIgnoreCase("showcase") || args[0].equalsIgnoreCase("dummy")) {
                        double spacing = 15.0;
                        if (args.length > 1) {
                            try {
                                spacing = Math.max(3.0, Double.parseDouble(args[1]));
                            } catch (NumberFormatException ignored) {}
                        }
                        List<IronGolem> dummies = WardenShowcaseHandler.spawnShowcaseLine(
                                HaoHanLunarPlugin.this, lunarWardenMechanic, player.getLocation(), spacing, player);
                        player.sendMessage("§a[Lunar Warden] Đã triệu hồi thành công hàng §e" + dummies.size()
                                + " §aBoss Showcase liên tục thi triển chiêu thức vào không khí!");
                        return true;
                    }
                }

                WardenSpawner.spawnWarden(HaoHanLunarPlugin.this, lunarWardenMechanic, player.getLocation(), player);
                player.sendMessage("§a[Lunar Warden] Đã triệu hồi Boss The Lunar Warden!");
                return true;
            }
        });

        // 2. /wardenshowcase [spacing] (aliases: /spawnwardenshowcase, /wardendummy, /showcasewarden, /wardenline)
        Bukkit.getCommandMap().register("haohan", new BukkitCommand("wardenshowcase") {
            {
                setDescription("Triệu hồi hàng Boss biểu diễn tất cả các chiêu thức The Lunar Warden liên tục tại chỗ");
                setAliases(List.of("spawnwardenshowcase", "wardendummy", "showcasewarden", "wardenline"));
                setPermission("haohan.admin");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cChỉ có người chơi mới dùng được lệnh này!");
                    return true;
                }

                double spacing = 15.0;
                if (args.length > 0) {
                    try {
                        spacing = Math.max(3.0, Double.parseDouble(args[0]));
                    } catch (NumberFormatException ignored) {}
                }

                List<IronGolem> dummies = WardenShowcaseHandler.spawnShowcaseLine(
                        HaoHanLunarPlugin.this, lunarWardenMechanic, player.getLocation(), spacing, player);
                player.sendMessage("§a[Lunar Warden] Đã triệu hồi hàng §e" + dummies.size()
                        + " §aBoss Showcase (Khoảng cách: §e" + spacing + "m§a)! Dùng §e/clearwarden §ađể xóa.");
                return true;
            }
        });

        // 3. /clearwarden (aliases: /wardenclear, /cleardummy, /wardencleardummy, /killwarden)
        Bukkit.getCommandMap().register("haohan", new BukkitCommand("clearwarden") {
            {
                setDescription("Xóa toàn bộ Boss The Lunar Warden và các Boss Showcase");
                setAliases(List.of("wardenclear", "cleardummy", "wardencleardummy", "killwarden"));
                setPermission("haohan.admin");
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                int cleared = lunarWardenMechanic.clearAllWardens();
                sender.sendMessage("§a[Lunar Warden] Đã xóa thành công §e" + cleared + " §aboss và toàn bộ hiệu ứng / mô hình liên quan!");
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
        // Clear active smooth slash visual tasks
        SmoothSlashTask.cleanupAll();

        // Clear and cleanup all active bosses, models and displays
        if (lunarWardenMechanic != null) {
            lunarWardenMechanic.clearAllWardens();
        }

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
