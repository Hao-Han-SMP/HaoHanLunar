package vn.haohan.lunar.core.world.pin;

import vn.haohan.lunar.api.system.world.pin.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PinSystemTest {

    private World mockWorld;

    @BeforeEach
    void setUp() {
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "hashCode" -> 12345;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    default -> null;
                }
        );
    }

    private Location loc(double x, double y, double z) {
        return new Location(mockWorld, x, y, z);
    }

    private LivingEntity mockLivingEntity(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> loc;
                    case "getWorld" -> mockWorld;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getType" -> EntityType.IRON_GOLEM;
                    case "getUniqueId" -> uuid;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private Player mockPlayer(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> loc;
                    case "getWorld" -> mockWorld;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getName" -> "TestPlayer";
                    case "getUniqueId" -> uuid;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private ActiveLunarMob sampleMob(double x, double y, double z) {
        LivingEntity entity = mockLivingEntity(loc(x, y, z));
        MobDefinition definition = new MobDefinition(new MobDefinitionId("boss"),
                EntityType.IRON_GOLEM, "Boss", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("boss", "1"));
    }

    @Test
    void testPolyhedralPinRegionRayCastingConvexAndNonConvex() {
        // 1. Minimum 3 pins validation
        assertThrows(IllegalArgumentException.class, () ->
                new PinRegion("INVALID", "world", List.of(
                        new SinglePin("P1", "world", 0, 0, 0),
                        new SinglePin("P2", "world", 10, 0, 0)
                ), 0, 100));

        // 2. Convex Rectangle Region (0,0) to (10,10)
        List<SinglePin> rectPins = List.of(
                new SinglePin("R1", "world", 0, 60, 0),
                new SinglePin("R2", "world", 10, 60, 0),
                new SinglePin("R3", "world", 10, 60, 10),
                new SinglePin("R4", "world", 0, 60, 10)
        );
        PinRegion rectRegion = new PinRegion("ARENA_RECT", "world", rectPins, 50, 80);

        // Inside
        assertTrue(rectRegion.contains(loc(5, 65, 5)));
        assertTrue(rectRegion.contains(loc(1, 79, 1)));
        assertTrue(rectRegion.contains(loc(9, 51, 9)));

        // Horizontally Outside
        assertFalse(rectRegion.contains(loc(15, 65, 5)));
        assertFalse(rectRegion.contains(loc(-2, 65, 5)));
        assertFalse(rectRegion.contains(loc(5, 65, 12)));

        // Vertically Outside
        assertFalse(rectRegion.contains(loc(5, 45, 5)));
        assertFalse(rectRegion.contains(loc(5, 85, 5)));

        // 3. Non-Convex L-Shaped Region: (0,0)->(10,0)->(10,5)->(5,5)->(5,10)->(0,10)
        List<SinglePin> lPins = List.of(
                new SinglePin("L1", "world", 0, 60, 0),
                new SinglePin("L2", "world", 10, 60, 0),
                new SinglePin("L3", "world", 10, 60, 5),
                new SinglePin("L4", "world", 5, 60, 5),
                new SinglePin("L5", "world", 5, 60, 10),
                new SinglePin("L6", "world", 0, 60, 10)
        );
        PinRegion lRegion = new PinRegion("ARENA_L", "world", lPins, 50, 80);

        // Inside areas
        assertTrue(lRegion.contains(loc(2, 65, 2))); // Lower left
        assertTrue(lRegion.contains(loc(8, 65, 2))); // Lower right
        assertTrue(lRegion.contains(loc(2, 65, 8))); // Upper left

        // In the cutout corner (5..10, 5..10) -> Must be OUTSIDE
        assertFalse(lRegion.contains(loc(8, 65, 8)));
    }

    @Test
    void testPinManagerPersistenceYaml(@TempDir Path tempDir) throws Exception {
        PinManager manager = new PinManager();

        SinglePin p1 = new SinglePin("P1", "world", 0, 60, 0);
        SinglePin p2 = new SinglePin("P2", "world", 20, 60, 0);
        SinglePin p3 = new SinglePin("P3", "world", 20, 60, 20);
        SinglePin p4 = new SinglePin("P4", "world", 0, 60, 20);

        manager.addPin(p1);
        manager.addPin(p2);
        manager.addPin(p3);
        manager.addPin(p4);

        assertEquals(4, manager.allPins().size());
        assertTrue(manager.getPin("P1").isPresent());

        PinRegion region = manager.createRegion("BOSS_ARENA", "world", List.of("P1", "P2", "P3", "P4"), 40, 90);
        assertEquals("BOSS_ARENA", region.name());
        assertEquals(1, manager.allRegions().size());

        Path saveFile = tempDir.resolve("regions.yml");
        manager.save(saveFile);
        assertTrue(java.nio.file.Files.isRegularFile(saveFile));

        // Restore into fresh PinManager
        PinManager restored = new PinManager();
        restored.load(saveFile);

        assertEquals(4, restored.allPins().size());
        assertTrue(restored.getPin("p2").isPresent());
        assertEquals(20.0, restored.getPin("p2").get().x());

        assertTrue(restored.getRegion("boss_arena").isPresent());
        PinRegion restoredRegion = restored.getRegion("boss_arena").get();
        assertEquals(40.0, restoredRegion.minY());
        assertEquals(90.0, restoredRegion.maxY());
        assertEquals(4, restoredRegion.pins().size());

        // Test spatial query on restored region
        assertTrue(restored.isInsideRegion("BOSS_ARENA", loc(10, 65, 10)));
        assertFalse(restored.isInsideRegion("BOSS_ARENA", loc(30, 65, 10)));

        // Removal tests
        assertTrue(restored.removeRegion("BOSS_ARENA"));
        assertFalse(restored.getRegion("BOSS_ARENA").isPresent());
        assertTrue(restored.removePin("P1"));
        assertFalse(restored.getPin("P1").isPresent());
    }

    @Test
    void testPinConditionsAndTargeters() {
        PinManager pinManager = PinManager.get();

        SinglePin center = new SinglePin("CRYSTAL_CENTER", "world", 100, 64, 100);
        SinglePin a1 = new SinglePin("A1", "world", 90, 64, 90);
        SinglePin a2 = new SinglePin("A2", "world", 110, 64, 90);
        SinglePin a3 = new SinglePin("A3", "world", 110, 64, 110);
        SinglePin a4 = new SinglePin("A4", "world", 90, 64, 110);

        pinManager.addPin(center);
        pinManager.addPin(a1);
        pinManager.addPin(a2);
        pinManager.addPin(a3);
        pinManager.addPin(a4);

        pinManager.createRegion("ARENA_CRYSTAL", "world", List.of("A1", "A2", "A3", "A4"), 60, 70);

        LivingEntity insideEntity = mockLivingEntity(loc(100, 64, 105)); // distance = 5
        LivingEntity outsideEntity = mockLivingEntity(loc(200, 64, 200));

        ConditionRegistry conditionRegistry = new ConditionRegistry();
        ConditionContext insideContext = new ConditionContext(insideEntity, null, null, new CooldownRegistry(), Map.of(), 0);
        ConditionContext outsideContext = new ConditionContext(outsideEntity, null, null, new CooldownRegistry(), Map.of(), 0);

        // 1. inpinregion condition
        assertTrue(conditionRegistry.evaluate("inpinregion", insideContext, Map.of("region", "ARENA_CRYSTAL")).matched());
        assertFalse(conditionRegistry.evaluate("inpinregion", outsideContext, Map.of("region", "ARENA_CRYSTAL")).matched());

        // 2. distancefrompin condition
        assertTrue(conditionRegistry.evaluate("distancefrompin", insideContext, Map.of("pin", "CRYSTAL_CENTER", "d", "<10")).matched());
        assertFalse(conditionRegistry.evaluate("distancefrompin", insideContext, Map.of("pin", "CRYSTAL_CENTER", "d", ">20")).matched());
        assertTrue(conditionRegistry.evaluate("distancefrompin", outsideContext, Map.of("pin", "CRYSTAL_CENTER", "d", ">50")).matched());

        // 3. BlocksInPinRegionTargeter
        TargeterRegistry targeterRegistry = new TargeterRegistry();
        ActiveLunarMob mob = sampleMob(100, 64, 100);
        SkillCastContext castContext = new SkillCastContext(mob,
                new SkillDefinition("pin_skill", Set.of(SkillTrigger.ON_COMBAT), 20),
                SkillTrigger.ON_COMBAT, 0);

        Collection<Location> floorBlocks = targeterRegistry.resolveLocations("@BlocksInPinRegion{region=ARENA_CRYSTAL}", castContext);
        assertFalse(floorBlocks.isEmpty());
        for (Location blockLoc : floorBlocks) {
            assertEquals(60.0, blockLoc.getY());
            assertTrue(blockLoc.getX() >= 90 && blockLoc.getX() <= 110);
            assertTrue(blockLoc.getZ() >= 90 && blockLoc.getZ() <= 110);
        }
    }

    @Test
    void testPinBoundaryListenerEnterAndExit() {
        PinManager pinManager = new PinManager();

        SinglePin p1 = new SinglePin("B1", "world", 0, 60, 0);
        SinglePin p2 = new SinglePin("B2", "world", 10, 60, 0);
        SinglePin p3 = new SinglePin("B3", "world", 10, 60, 10);
        SinglePin p4 = new SinglePin("B4", "world", 0, 60, 10);
        pinManager.addPin(p1);
        pinManager.addPin(p2);
        pinManager.addPin(p3);
        pinManager.addPin(p4);

        pinManager.createRegion("BOUNDED_ARENA", "world", List.of("B1", "B2", "B3", "B4"), 50, 80);

        PinBoundaryListener listener = new PinBoundaryListener(pinManager);
        AtomicReference<String> enteredRegion = new AtomicReference<>(null);
        AtomicReference<String> exitedRegion = new AtomicReference<>(null);

        listener.addEnterCallback((player, region) -> enteredRegion.set(region.name()));
        listener.addExitCallback((player, region) -> exitedRegion.set(region.name()));

        Player player = mockPlayer(loc(0, 0, 0));

        // 1. Move from outside (20, 65, 20) to inside (5, 65, 5) -> triggers Enter
        listener.processBoundaryChange(player, loc(20, 65, 20), loc(5, 65, 5));
        assertEquals("BOUNDED_ARENA", enteredRegion.get());
        assertNull(exitedRegion.get());

        // Reset
        enteredRegion.set(null);
        exitedRegion.set(null);

        // 2. Move within region (5, 65, 5) to (6, 65, 5) -> Neither triggers
        listener.processBoundaryChange(player, loc(5, 65, 5), loc(6, 65, 5));
        assertNull(enteredRegion.get());
        assertNull(exitedRegion.get());

        // 3. Move from inside (6, 65, 5) to outside (30, 65, 30) -> triggers Exit
        listener.processBoundaryChange(player, loc(6, 65, 5), loc(30, 65, 30));
        assertNull(enteredRegion.get());
        assertEquals("BOUNDED_ARENA", exitedRegion.get());
    }
}
