package vn.haohan.lunar.robot;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class LunarRobotBossBarDimensionTest {

    @Test
    @DisplayName("BossBar instance should be preserved and reused across empty viewer transitions to prevent duplicate UUIDs on client")
    void testBossBarReusedAcrossEmptyViewers() {
        World worldA = mockWorld("world_overworld");
        Location locA = new Location(worldA, 0, 64, 0);

        UUID ownerId = UUID.randomUUID();
        AtomicInteger showCount = new AtomicInteger(0);
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> shownBars = new ArrayList<>();
        List<BossBar> hiddenBars = new ArrayList<>();

        Player owner = mockPlayer("Owner", locA, ownerId, showCount, hideCount, shownBars, hiddenBars);

        Wolf wolf = mockWolf(locA);
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setOwnerUuid(ownerId);
        data.setActiveTask(RobotTask.SPEED);

        Plugin plugin = mockPlugin();

        LunarRobotEntity testRobot = new LunarRobotEntity(plugin, wolf, data) {
            private Player currentOwner = owner;
            void setOwner(Player p) { this.currentOwner = p; }
            @Override
            public Player getOnlineOwner() { return currentOwner; }
        };

        // 1. Initial notification with owner in range -> BossBar is created and shown
        testRobot.setBossBarNotification("INIT", BarColor.WHITE, 100);
        BossBar initialBar = testRobot.getStatusBossBar();
        assertNotNull(initialBar, "Status BossBar should be created for in-range owner with notification");
        assertEquals(1, showCount.get(), "BossBar should be shown to owner once");
        assertSame(initialBar, shownBars.get(0));

        // 2. Owner changes dimension: owner is now in World B
        World worldB = mockWorld("world_nether");
        Location locB = new Location(worldB, 100, 64, 100);
        Player ownerInWorldB = mockPlayer("Owner", locB, ownerId, showCount, hideCount, shownBars, hiddenBars);

        // Owner moves to World B
        try {
            var method = testRobot.getClass().getDeclaredMethod("setOwner", Player.class);
            method.setAccessible(true);
            method.invoke(testRobot, ownerInWorldB);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testRobot.updateStatusBossBar();

        // Previous viewers should have had hideBossBar called
        assertTrue(hideCount.get() >= 1, "hideBossBar should be called when viewer is in another dimension");

        // KEY ASSERTION: The statusBossBar instance MUST NOT BE DESTROYED (MUST NOT BE NULL)
        assertNotNull(testRobot.getStatusBossBar(), "Status BossBar instance must be retained rather than set to null, avoiding new UUID churn");
        assertSame(initialBar, testRobot.getStatusBossBar(), "Status BossBar instance must be identical across viewer departures");

        // Owner returns to World A
        try {
            var method = testRobot.getClass().getDeclaredMethod("setOwner", Player.class);
            method.setAccessible(true);
            method.invoke(testRobot, owner);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testRobot.updateStatusBossBar();

        assertSame(initialBar, testRobot.getStatusBossBar(), "Status BossBar instance must still be identical when viewer returns");
    }

    @Test
    @DisplayName("Rider in a different dimension must be excluded from target viewers")
    void testCrossDimensionRiderExcluded() {
        World worldA = mockWorld("world_overworld");
        World worldB = mockWorld("world_nether");
        Location locA = new Location(worldA, 0, 64, 0);
        Location locB = new Location(worldB, 0, 64, 0);

        UUID riderId = UUID.randomUUID();
        Player riderInWorldB = mockPlayer("RiderInNether", locB, riderId, null, null, null, null);

        Wolf wolf = mockWolf(locA);
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setActiveTask(RobotTask.SPEED);

        Plugin plugin = mockPlugin();
        LunarRobotEntity robot = new LunarRobotEntity(plugin, wolf, data) {
            @Override
            public Player getRider() {
                return riderInWorldB;
            }
            @Override
            public Player getOnlineOwner() {
                return null;
            }
        };

        robot.updateStatusBossBar();
        // Since rider is in World B and owner is null, there are NO valid viewers in World A
        assertEquals(0, robot.getStatusBossBarViewersCount());
    }

    @Test
    @DisplayName("hideStatusBossBarFrom should cleanly hide the bar from a specified player and update tracked viewers")
    void testHideStatusBossBarFromPlayer() {
        World worldA = mockWorld("world_overworld");
        Location locA = new Location(worldA, 0, 64, 0);

        UUID ownerId = UUID.randomUUID();
        AtomicInteger showCount = new AtomicInteger(0);
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> shownBars = new ArrayList<>();
        List<BossBar> hiddenBars = new ArrayList<>();

        Player owner = mockPlayer("Owner", locA, ownerId, showCount, hideCount, shownBars, hiddenBars);

        Wolf wolf = mockWolf(locA);
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setOwnerUuid(ownerId);
        data.setActiveTask(RobotTask.SPEED);

        Plugin plugin = mockPlugin();
        LunarRobotEntity robot = new LunarRobotEntity(plugin, wolf, data) {
            @Override
            public Player getOnlineOwner() {
                return owner;
            }
        };

        robot.setBossBarNotification("INIT", BarColor.WHITE, 100);
        assertEquals(1, robot.getStatusBossBarViewersCount());

        robot.hideStatusBossBarFrom(owner);
        assertEquals(0, robot.getStatusBossBarViewersCount());
        assertTrue(hideCount.get() >= 1);
        assertSame(robot.getStatusBossBar(), hiddenBars.get(hiddenBars.size() - 1));
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) {
                        if (args == null || args.length == 0 || args[0] == null) return false;
                        if (proxy == args[0]) return true;
                        if (args[0] instanceof World other) {
                            return Objects.equals(name, other.getName());
                        }
                        return false;
                    }
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }

    private static Wolf mockWolf(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (Wolf) Proxy.newProxyInstance(Wolf.class.getClassLoader(),
                new Class<?>[]{Wolf.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return loc.getWorld();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getVelocity")) return new org.bukkit.util.Vector(0, 0, 0);
                    if (method.getName().equals("getPassengers")) return Collections.emptyList();
                    if (method.getName().equals("getAttribute")) return null;
                    if (method.getName().equals("getPersistentDataContainer")) {
                        return createMockPdc();
                    }
                    return null;
                });
    }

    private static org.bukkit.persistence.PersistentDataContainer createMockPdc() {
        Map<org.bukkit.NamespacedKey, Object> store = new HashMap<>();
        return (org.bukkit.persistence.PersistentDataContainer) Proxy.newProxyInstance(
                org.bukkit.persistence.PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{org.bukkit.persistence.PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("has")) {
                        return store.containsKey(args[0]);
                    }
                    if (method.getName().equals("get")) {
                        return store.get(args[0]);
                    }
                    if (method.getName().equals("set")) {
                        store.put((org.bukkit.NamespacedKey) args[0], args[2]);
                        return null;
                    }
                    return null;
                }
        );
    }

    private static Player mockPlayer(String name, Location loc, UUID uuid,
                                      AtomicInteger showCount, AtomicInteger hideCount,
                                      List<BossBar> shownBars, List<BossBar> hiddenBars) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return loc.getWorld();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("showBossBar")) {
                        if (showCount != null) showCount.incrementAndGet();
                        if (shownBars != null && args.length > 0 && args[0] instanceof BossBar b) shownBars.add(b);
                        return null;
                    }
                    if (method.getName().equals("hideBossBar")) {
                        if (hideCount != null) hideCount.incrementAndGet();
                        if (hiddenBars != null && args.length > 0 && args[0] instanceof BossBar b) hiddenBars.add(b);
                        return null;
                    }
                    if (method.getName().equals("getInventory")) {
                        return mockInventory();
                    }
                    if (method.getName().equals("equals")) {
                        if (args == null || args.length == 0 || args[0] == null) return false;
                        if (proxy == args[0]) return true;
                        if (args[0] instanceof Player other) {
                            return Objects.equals(uuid, other.getUniqueId());
                        }
                        return false;
                    }
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static org.bukkit.inventory.PlayerInventory mockInventory() {
        return (org.bukkit.inventory.PlayerInventory) Proxy.newProxyInstance(
                org.bukkit.inventory.PlayerInventory.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.PlayerInventory.class},
                (proxy, method, args) -> null
        );
    }

    private static Plugin mockPlugin() {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLogger")) {
                        return Logger.getLogger("test");
                    }
                    return null;
                });
    }
}
