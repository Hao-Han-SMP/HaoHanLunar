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

    @Test
    @DisplayName("Mechanic cleanupPlayerRobotBossBar must cleanly hide robot BossBar on world transitions")
    void testMechanicCleanupPlayerRobotBossBar() {
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

        robot.setBossBarNotification("SPEED", BarColor.WHITE, 100);
        assertEquals(1, robot.getStatusBossBarViewersCount());

        LunarRobotMechanic mechanic = new LunarRobotMechanic(null);
        mechanic.getActiveRobots().put(wolf.getUniqueId(), robot);
        mechanic.getPlayerRobotMap().put(ownerId, wolf.getUniqueId());

        mechanic.cleanupPlayerRobotBossBar(owner);

        assertEquals(0, robot.getStatusBossBarViewersCount());
        assertTrue(hideCount.get() >= 1);
    }

    @Test
    @DisplayName("When robot teleports across dimensions, mechanic must preserve the persistent BossBar instance across entity wrappers")
    void testRobotDimensionTeleportReusesPersistentBossBar() {
        World worldA = mockWorld("world_overworld");
        World worldB = mockWorld("world_nether");
        Location locA = new Location(worldA, 0, 64, 0);
        Location locB = new Location(worldB, 10, 64, 10);

        UUID ownerId = UUID.randomUUID();
        AtomicInteger showCount = new AtomicInteger(0);
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> shownBars = new ArrayList<>();
        List<BossBar> hiddenBars = new ArrayList<>();

        Player ownerInWorldA = mockPlayer("Owner", locA, ownerId, showCount, hideCount, shownBars, hiddenBars);
        Wolf wolfA = mockWolf(locA);
        UUID robotPersistentId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotPersistentId);
        data.setOwnerUuid(ownerId);
        data.setActiveTask(RobotTask.SPEED);

        LunarRobotMechanic mechanic = new LunarRobotMechanic(null);
        Plugin plugin = mockPluginWithMechanic(mechanic);

        // 1. Robot in World A creates/acquires BossBar for owner
        LunarRobotEntity robot1 = new LunarRobotEntity(plugin, wolfA, data) {
            @Override
            public Player getOnlineOwner() {
                return ownerInWorldA;
            }
        };
        mechanic.getActiveRobots().put(wolfA.getUniqueId(), robot1);
        mechanic.getPlayerRobotMap().put(ownerId, wolfA.getUniqueId());

        robot1.setBossBarNotification("SPEED_INIT", BarColor.WHITE, 100);
        BossBar bar1 = robot1.getStatusBossBar();
        assertNotNull(bar1, "BossBar must be initialized");
        assertSame(bar1, mechanic.getOrCreatePlayerStatusBossBar(ownerId), "Mechanic must hold the persistent BossBar for owner");

        // 2. Owner teleports to World B
        Player ownerInWorldB = mockPlayer("Owner", locB, ownerId, showCount, hideCount, shownBars, hiddenBars);
        mechanic.cleanupPlayerRobotBossBar(ownerInWorldB);
        assertTrue(hiddenBars.contains(bar1), "bar1 must be hidden when owner leaves dimension");

        // 3. Robot 1 is removed from World A (simulating entity world transfer removal)
        robot1.cleanup();

        // 4. Robot arrives in World B as new entity wrapper
        Wolf wolfB = mockWolf(locB);
        LunarRobotEntity robot2 = new LunarRobotEntity(plugin, wolfB, data) {
            @Override
            public Player getOnlineOwner() {
                return ownerInWorldB;
            }
        };
        mechanic.getActiveRobots().put(wolfB.getUniqueId(), robot2);
        mechanic.getPlayerRobotMap().put(ownerId, wolfB.getUniqueId());

        // Robot 2 updates status boss bar for owner in World B
        robot2.setBossBarNotification("SPEED_NETHER", BarColor.WHITE, 100);
        BossBar bar2 = robot2.getStatusBossBar();

        // CRUCIAL ASSERTION: bar2 MUST BE THE EXACT SAME INSTANCE as bar1!
        assertSame(bar1, bar2, "BossBar instance MUST be reused across dimension transitions, guaranteeing identical UUID on client");
    }

    @Test
    @DisplayName("Mechanic onPlayerRespawn must cleanly hide robot BossBar on player respawn")
    void testPlayerRespawnCleansUpRobotBossBar() {
        World worldNether = mockWorld("world_nether");
        Location locNether = new Location(worldNether, 50, 64, 50);

        UUID ownerId = UUID.randomUUID();
        AtomicInteger showCount = new AtomicInteger(0);
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> shownBars = new ArrayList<>();
        List<BossBar> hiddenBars = new ArrayList<>();

        Player owner = mockPlayer("Owner", locNether, ownerId, showCount, hideCount, shownBars, hiddenBars);
        Wolf wolf = mockWolf(locNether);
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setOwnerUuid(ownerId);
        data.setActiveTask(RobotTask.SPEED);

        LunarRobotMechanic mechanic = new LunarRobotMechanic(null);
        Plugin plugin = mockPluginWithMechanic(mechanic);

        LunarRobotEntity robot = new LunarRobotEntity(plugin, wolf, data) {
            @Override
            public Player getOnlineOwner() {
                return owner;
            }
        };
        mechanic.getActiveRobots().put(wolf.getUniqueId(), robot);
        mechanic.getPlayerRobotMap().put(ownerId, wolf.getUniqueId());

        robot.setBossBarNotification("SPEED", BarColor.WHITE, 100);
        BossBar bar = robot.getStatusBossBar();
        assertNotNull(bar);

        // Simulate respawn event
        org.bukkit.event.player.PlayerRespawnEvent respawnEvent = org.mockito.Mockito.mock(org.bukkit.event.player.PlayerRespawnEvent.class);
        org.mockito.Mockito.when(respawnEvent.getPlayer()).thenReturn(owner);

        mechanic.onPlayerRespawn(respawnEvent);

        assertTrue(hiddenBars.contains(bar), "BossBar must be hidden on PlayerRespawnEvent");
    }

    @Test
    @DisplayName("Multiple successive dimension changes (Overworld -> Nether -> End -> Overworld) must retain the exact same BossBar instance")
    void testMultipleSuccessiveDimensionTeleportsReusesSameInstance() {
        World overworld = mockWorld("world_overworld");
        World nether = mockWorld("world_nether");
        World end = mockWorld("world_the_end");

        UUID ownerId = UUID.randomUUID();
        AtomicInteger showCount = new AtomicInteger(0);
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> shownBars = new ArrayList<>();
        List<BossBar> hiddenBars = new ArrayList<>();

        Player owner = mockPlayer("Owner", new Location(overworld, 0, 64, 0), ownerId, showCount, hideCount, shownBars, hiddenBars);
        UUID robotPersistentId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotPersistentId);
        data.setOwnerUuid(ownerId);
        data.setActiveTask(RobotTask.SPEED);

        LunarRobotMechanic mechanic = new LunarRobotMechanic(null);
        vn.haohan.lunar.HaoHanLunarPlugin plugin = mockPluginWithMechanic(mechanic);

        // 1. Overworld
        Wolf wolf1 = mockWolf(new Location(overworld, 0, 64, 0));
        LunarRobotEntity robot1 = new LunarRobotEntity(plugin, wolf1, data) {
            @Override
            public Player getOnlineOwner() { return owner; }
        };
        mechanic.getActiveRobots().put(wolf1.getUniqueId(), robot1);
        mechanic.getPlayerRobotMap().put(ownerId, wolf1.getUniqueId());
        robot1.setBossBarNotification("OVERWORLD", BarColor.WHITE, 100);
        BossBar barInitial = robot1.getStatusBossBar();
        assertNotNull(barInitial);

        // 2. Teleport to Nether
        Player ownerNether = mockPlayer("Owner", new Location(nether, 10, 64, 10), ownerId, showCount, hideCount, shownBars, hiddenBars);
        mechanic.cleanupPlayerRobotBossBar(ownerNether);
        robot1.cleanup();

        Wolf wolf2 = mockWolf(new Location(nether, 10, 64, 10));
        LunarRobotEntity robot2 = new LunarRobotEntity(plugin, wolf2, data) {
            @Override
            public Player getOnlineOwner() { return ownerNether; }
        };
        mechanic.getActiveRobots().put(wolf2.getUniqueId(), robot2);
        mechanic.getPlayerRobotMap().put(ownerId, wolf2.getUniqueId());
        robot2.setBossBarNotification("NETHER", BarColor.WHITE, 100);
        BossBar barNether = robot2.getStatusBossBar();
        assertSame(barInitial, barNether, "Nether BossBar must match initial instance");

        // 3. Teleport to End
        Player ownerEnd = mockPlayer("Owner", new Location(end, 20, 64, 20), ownerId, showCount, hideCount, shownBars, hiddenBars);
        mechanic.cleanupPlayerRobotBossBar(ownerEnd);
        robot2.cleanup();

        Wolf wolf3 = mockWolf(new Location(end, 20, 64, 20));
        LunarRobotEntity robot3 = new LunarRobotEntity(plugin, wolf3, data) {
            @Override
            public Player getOnlineOwner() { return ownerEnd; }
        };
        mechanic.getActiveRobots().put(wolf3.getUniqueId(), robot3);
        mechanic.getPlayerRobotMap().put(ownerId, wolf3.getUniqueId());
        robot3.setBossBarNotification("END", BarColor.WHITE, 100);
        BossBar barEnd = robot3.getStatusBossBar();
        assertSame(barInitial, barEnd, "End BossBar must match initial instance");

        // 4. Return to Overworld
        Player ownerReturned = mockPlayer("Owner", new Location(overworld, 0, 64, 0), ownerId, showCount, hideCount, shownBars, hiddenBars);
        mechanic.cleanupPlayerRobotBossBar(ownerReturned);
        robot3.cleanup();

        Wolf wolf4 = mockWolf(new Location(overworld, 0, 64, 0));
        LunarRobotEntity robot4 = new LunarRobotEntity(plugin, wolf4, data) {
            @Override
            public Player getOnlineOwner() { return ownerReturned; }
        };
        mechanic.getActiveRobots().put(wolf4.getUniqueId(), robot4);
        mechanic.getPlayerRobotMap().put(ownerId, wolf4.getUniqueId());
        robot4.setBossBarNotification("BACK_HOME", BarColor.WHITE, 100);
        BossBar barReturned = robot4.getStatusBossBar();
        assertSame(barInitial, barReturned, "Returned Overworld BossBar must match initial instance");

        // Client shown bars list: all shown bars were the SAME instance!
        for (BossBar b : shownBars) {
            assertSame(barInitial, b, "Every BossBar shown to client must be the identical instance");
        }
    }

    @Test
    @DisplayName("MobSkillRuntime must detach boss bars from player on PlayerChangedWorldEvent even if mob entity is null")
    void testMobSkillRuntimePlayerChangedWorldNullEntity() {
        World overworld = mockWorld("world_overworld");
        World nether = mockWorld("world_nether");

        UUID playerId = UUID.randomUUID();
        AtomicInteger hideCount = new AtomicInteger(0);
        List<BossBar> hiddenBars = new ArrayList<>();
        Player playerInNether = mockPlayer("Player", new Location(nether, 0, 64, 0), playerId, null, hideCount, null, hiddenBars);

        vn.haohan.engine.core.mob.MobManager mobManager = org.mockito.Mockito.mock(vn.haohan.engine.core.mob.MobManager.class);
        vn.haohan.engine.core.mob.MobSkillRuntime runtime = new vn.haohan.engine.core.mob.MobSkillRuntime(
                mobManager,
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.SkillRegistry.class),
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.mechanic.MechanicRegistry.class),
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.condition.ConditionRegistry.class),
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.target.BasicTargeterRegistry.class),
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.CooldownRegistry.class),
                org.mockito.Mockito.mock(vn.haohan.engine.api.system.combat.skill.SkillScheduler.class),
                null,
                null
        );

        vn.haohan.engine.core.mob.ActiveMob mob = org.mockito.Mockito.mock(vn.haohan.engine.core.mob.ActiveMob.class);
        vn.haohan.engine.api.presentation.display.bossbar.LunarBossBarTracker tracker = new vn.haohan.engine.api.presentation.display.bossbar.LunarBossBarTracker();
        vn.haohan.engine.api.presentation.display.bossbar.LunarBossBar lunarBar = tracker.create(
                "bar1", net.kyori.adventure.text.Component.text("MobBar"), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS, 50.0
        );

        // Add player as viewer via initial updateViewers in nether
        lunarBar.updateViewers(new Location(nether, 0, 64, 0), List.of(playerInNether));

        org.mockito.Mockito.when(mob.bossBars()).thenReturn(tracker);
        org.mockito.Mockito.when(mob.entity()).thenReturn(null); // Unloaded entity
        org.mockito.Mockito.when(mobManager.snapshot()).thenReturn(List.of(mob));

        // Fire PlayerChangedWorldEvent
        org.bukkit.event.player.PlayerChangedWorldEvent event = org.mockito.Mockito.mock(org.bukkit.event.player.PlayerChangedWorldEvent.class);
        org.mockito.Mockito.when(event.getPlayer()).thenReturn(playerInNether);

        runtime.onPlayerChangedWorld(event);

        assertTrue(hideCount.get() >= 1, "hideBossBar must be called when player changes world and mob entity is null");
    }

    private static vn.haohan.lunar.HaoHanLunarPlugin mockPluginWithMechanic(LunarRobotMechanic mechanic) {
        vn.haohan.lunar.HaoHanLunarPlugin plugin = org.mockito.Mockito.mock(vn.haohan.lunar.HaoHanLunarPlugin.class);
        org.mockito.Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        org.mockito.Mockito.when(plugin.getLunarRobotMechanic()).thenReturn(mechanic);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(BukkitMock.createServer());
        org.mockito.Mockito.when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }

    private static class BukkitMock {
        static org.bukkit.Server createServer() {
            return (org.bukkit.Server) Proxy.newProxyInstance(org.bukkit.Server.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.Server.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getScheduler")) {
                            return createScheduler();
                        }
                        return null;
                    });
        }

        static org.bukkit.scheduler.BukkitScheduler createScheduler() {
            return (org.bukkit.scheduler.BukkitScheduler) Proxy.newProxyInstance(org.bukkit.scheduler.BukkitScheduler.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.scheduler.BukkitScheduler.class}, (proxy, method, args) -> null);
        }
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
