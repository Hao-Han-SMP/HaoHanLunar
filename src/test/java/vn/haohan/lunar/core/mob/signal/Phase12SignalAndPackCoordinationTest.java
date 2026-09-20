package vn.haohan.lunar.core.mob.signal;

import vn.haohan.lunar.api.mob.signal.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionResult;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.mob.pack.PackCoordinationService;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class Phase12SignalAndPackCoordinationTest {

    private ConditionRegistry conditionRegistry;
    private MechanicRegistry mechanicRegistry;
    private MobSignalBus signalBus;
    private PackCoordinationService packService;
    private LunarMobManager mobManager;

    @BeforeEach
    void setUp() {
        conditionRegistry = new ConditionRegistry();
        signalBus = new MobSignalBus();
        packService = new PackCoordinationService(2.0);
        mobManager = new LunarMobManager(e -> {});

        mechanicRegistry = new MechanicRegistry();
        mechanicRegistry.setSignalBus(signalBus);
        mechanicRegistry.setPackService(packService);
        mechanicRegistry.setMobManager(mobManager);
    }

    @Test
    void testDirectPointToPointSignal() {
        World world = createMockWorld("lunar_world");
        ActiveLunarMob boss = createMockMob("lunar_boss", world, new Location(world, 0, 64, 0));
        ActiveLunarMob guard = createMockMob("lunar_guard", world, new Location(world, 5, 64, 5));
        mobManager.register(boss);
        mobManager.register(guard);

        List<String> receivedSignals = new ArrayList<>();
        signalBus.registerListener((delivery, depth) -> {
            if (delivery.recipient().entityId().equals(guard.entityId())) {
                receivedSignals.add(delivery.signal());
            }
        });

        // Execute signal mechanic targeting guard
        SkillDefinition skill = new SkillDefinition("callGuard", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext cast = new SkillCastContext(boss, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(guard.entity())));

        mechanicRegistry.execute("signal", ctx, Map.of("sig", "DEFEND_LEADER"));

        assertEquals(1, receivedSignals.size());
        assertEquals("DEFEND_LEADER", receivedSignals.get(0));
    }

    @Test
    void testBroadcastSignalToFiveGuardsWithFiltering() {
        World world = createMockWorld("lunar_world");
        ActiveLunarMob boss = createMockMob("king_boss", world, new Location(world, 0, 64, 0));
        mobManager.register(boss);

        List<ActiveLunarMob> guards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ActiveLunarMob guard = createMockMob("guard_minion", world, new Location(world, i * 2, 64, 0));
            // Set minions parent to boss
            guard.setParentUUID(boss.entityId());
            mobManager.register(guard);
            guards.add(guard);
        }

        // Add an unrelated foreign mob far away
        ActiveLunarMob outsider = createMockMob("outsider", world, new Location(world, 100, 64, 100));
        mobManager.register(outsider);

        List<UUID> notifiedMobIds = new ArrayList<>();
        signalBus.registerListener((delivery, depth) -> {
            notifiedMobIds.add(delivery.recipient().entityId());
        });

        // Boss casts broadcastsignal with MINIONS filter and 32 blocks radius
        SkillDefinition skill = new SkillDefinition("protectKing", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext cast = new SkillCastContext(boss, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(boss.entity())));

        mechanicRegistry.execute("broadcastsignal", ctx, Map.of(
                "sig", "SHIELD_WALL",
                "radius", 32.0,
                "target", "MINIONS"
        ));

        assertEquals(5, notifiedMobIds.size(), "All 5 guard minions should receive the broadcast signal");
        assertFalse(notifiedMobIds.contains(outsider.entityId()), "Outsider far away should not receive the signal");
    }

    @Test
    void testSignalInfiniteRecursionCascadeDepthBarrier() {
        World world = createMockWorld("lunar_world");
        ActiveLunarMob mobA = createMockMob("ping_mob", world, new Location(world, 0, 64, 0));
        ActiveLunarMob mobB = createMockMob("pong_mob", world, new Location(world, 1, 64, 0));
        mobManager.register(mobA);
        mobManager.register(mobB);

        AtomicInteger deliveryCount = new AtomicInteger(0);

        // Simulate ping-pong cascade listener
        signalBus.registerListener((delivery, depth) -> {
            deliveryCount.incrementAndGet();
            if ("PING".equals(delivery.signal())) {
                // Mob B replies with PONG at incremented depth
                signalBus.sendSignal(delivery.recipient(), mobA, "PONG", depth + 1);
            } else if ("PONG".equals(delivery.signal())) {
                // Mob A replies with PING at incremented depth
                signalBus.sendSignal(delivery.recipient(), mobB, "PING", depth + 1);
            }
        });

        // Start ping-pong chain at depth 0
        boolean delivered = signalBus.sendSignal(mobA, mobB, "PING", 0);
        assertTrue(delivered);

        // Max depth is 3:
        // Depth 0: PING (delivered, calls listener, triggers depth 1)
        // Depth 1: PONG (delivered, calls listener, triggers depth 2)
        // Depth 2: PING (delivered, calls listener, triggers depth 3)
        // Depth 3: PONG (delivered, calls listener, triggers depth 4)
        // Depth 4: PING -> dropped by cascade barrier!
        assertEquals(4, deliveryCount.get(), "Cascade depth must strictly truncate at MAX_CASCADE_DEPTH (3)");
    }

    @Test
    void testDistressCallThreatSharing() {
        World world = createMockWorld("lunar_world");
        ActiveLunarMob caller = createMockMob("pack_wolf_1", world, new Location(world, 0, 64, 0));
        ActiveLunarMob ally1 = createMockMob("pack_wolf_2", world, new Location(world, 5, 64, 0));
        ActiveLunarMob ally2 = createMockMob("pack_wolf_3", world, new Location(world, 10, 64, 0));
        mobManager.register(caller);
        mobManager.register(ally1);
        mobManager.register(ally2);

        LivingEntity attacker = createMockLivingEntity("mock_player", world, new Location(world, 0, 64, 1));

        // Caller has threat 200 against attacker
        caller.threatTable().addThreat(attacker.getUniqueId(), 200.0);

        // Execute distresscall mechanic with threatShare = 0.8 (80%)
        SkillDefinition skill = new SkillDefinition("howlForHelp", Set.of(SkillTrigger.ON_DAMAGED), 0);
        SkillCastContext cast = new SkillCastContext(caller, skill, SkillTrigger.ON_DAMAGED, 50L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(attacker)));

        mechanicRegistry.execute("distresscall", ctx, Map.of("radius", 32.0, "threatShare", 0.8));

        // Both allies should have received 160.0 threat (80% of 200)
        assertEquals(160.0, ally1.threatTable().getThreat(attacker.getUniqueId()), 0.001);
        assertEquals(160.0, ally2.threatTable().getThreat(attacker.getUniqueId()), 0.001);
    }

    @Test
    void testFlockingSeparationVectorCalculation() {
        World world = createMockWorld("lunar_world");
        // Wolf A at (0, 64, 0)
        ActiveLunarMob wolfA = createMockMob("wolf_a", world, new Location(world, 0, 64, 0));
        // Wolf B very close at (0.5, 64, 0)
        ActiveLunarMob wolfB = createMockMob("wolf_b", world, new Location(world, 0.5, 64, 0));

        // Separation distance is 2.0. Distance is 0.5.
        Vector push = packService.computeSeparationVector(wolfA, List.of(wolfB));

        // Wolf A should be pushed in -X direction (away from Wolf B)
        assertTrue(push.getX() < 0, "Push vector X should be negative to move away from neighbor");
        assertEquals(0, push.getY(), 0.001, "Push vector Y should remain 0 on flat plane");
        assertEquals(0, push.getZ(), 0.001, "Push vector Z should remain 0 on X axis offset");
        assertTrue(push.length() > 0 && push.length() <= 0.5, "Push vector length should be clamped to <= 0.5");
    }

    @Test
    void testSignalConditionEvaluation() {
        World world = createMockWorld("lunar_world");
        ActiveLunarMob mob = createMockMob("cond_mob", world, new Location(world, 0, 64, 0));

        ConditionContext ctx = new ConditionContext(
                mob.entity(), null, "default", new CooldownRegistry(), Map.of("signal", "SHIELD_WALL"), 0L);

        ConditionResult match = conditionRegistry.evaluate("signal", ctx, Map.of("signal", "SHIELD_WALL"));
        assertTrue(match.valid() && match.matched(), "Condition should match SHIELD_WALL");

        ConditionResult mismatch = conditionRegistry.evaluate("signal", ctx, Map.of("signal", "ATTACK_NOW"));
        assertTrue(mismatch.valid() && !mismatch.matched(), "Condition should not match ATTACK_NOW");
    }

    // --- Helpers and Dynamic Mocks ---

    private ActiveLunarMob createMockMob(String id, World world, Location loc) {
        MobDefinition def = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.ZOMBIE,
                id,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                null,
                List.of()
        );
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = createMockLivingEntity(id, world, loc, uuid);
        return new ActiveLunarMob(entity, def, new LunarMobIdentity(id, "1"));
    }

    private LivingEntity createMockLivingEntity(String name, World world, Location loc) {
        return createMockLivingEntity(name, world, loc, UUID.randomUUID());
    }

    private LivingEntity createMockLivingEntity(String name, World world, Location loc, UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc;
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private World createMockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }
}
