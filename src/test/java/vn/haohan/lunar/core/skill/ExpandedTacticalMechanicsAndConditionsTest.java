package vn.haohan.lunar.core.skill;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionResult;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.SinglePin;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExpandedTacticalMechanicsAndConditionsTest {

    private MechanicRegistry mechanicRegistry;
    private ConditionRegistry conditionRegistry;
    private TargeterRegistry targeterRegistry;
    private PinManager pinManager;
    private World mockWorld;

    @BeforeEach
    void setUp() {
        mechanicRegistry = new MechanicRegistry();
        conditionRegistry = new ConditionRegistry();
        targeterRegistry = new TargeterRegistry();
        pinManager = new PinManager();
        PinManager.setInstance(pinManager);

        mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft("overworld");
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> null;
                });
    }

    @Test
    void testPullMechanicCalculatesVelocityTowardsCaster() {
        AtomicReference<Vector> velocityApplied = new AtomicReference<>();
        Location casterLoc = new Location(mockWorld, 0, 64, 0);
        Location targetLoc = new Location(mockWorld, 10, 64, 0);

        LivingEntity casterEntity = createMockEntity(casterLoc, null, null, null, null, null);
        LivingEntity targetEntity = createMockEntity(targetLoc, velocityApplied, null, null, null, null);

        MechanicContext ctx = createMechanicContext(casterEntity, List.of(TargetRef.of(targetEntity)));
        MechanicResult result = mechanicRegistry.execute("pull", ctx, Map.of("velocity", 2.0, "y", 0.5));

        assertTrue(result.valid());
        assertNotNull(velocityApplied.get());
        // Pull direction: from target (10, 64, 0) to caster (0, 64, 0) -> X should be negative (-2.0)
        assertEquals(-2.0, velocityApplied.get().getX(), 0.01);
        assertEquals(0.5, velocityApplied.get().getY(), 0.01);
    }

    @Test
    void testThrowMechanicLaunchesTarget() {
        AtomicReference<Vector> velocityApplied = new AtomicReference<>();
        Location casterLoc = new Location(mockWorld, 0, 64, 0, 0, 0); // Yaw 0 = facing +Z
        Location targetLoc = new Location(mockWorld, 0, 64, 2);

        LivingEntity casterEntity = createMockEntity(casterLoc, null, null, null, null, null);
        LivingEntity targetEntity = createMockEntity(targetLoc, velocityApplied, null, null, null, null);

        MechanicContext ctx = createMechanicContext(casterEntity, List.of(TargetRef.of(targetEntity)));
        MechanicResult result = mechanicRegistry.execute("throw", ctx, Map.of("velocity", 1.5, "upward", 0.8));

        assertTrue(result.valid());
        assertNotNull(velocityApplied.get());
        assertEquals(0.8, velocityApplied.get().getY(), 0.01);
        assertTrue(velocityApplied.get().getZ() > 0.5);
    }

    @Test
    void testSwapMechanicTeleportsCasterAndTarget() {
        Location casterLoc = new Location(mockWorld, 0, 64, 0);
        Location targetLoc = new Location(mockWorld, 15, 70, 20);

        AtomicReference<Location> casterTeleported = new AtomicReference<>();
        AtomicReference<Location> targetTeleported = new AtomicReference<>();

        LivingEntity casterEntity = createMockEntity(casterLoc, null, casterTeleported, null, null, null);
        LivingEntity targetEntity = createMockEntity(targetLoc, null, targetTeleported, null, null, null);

        MechanicContext ctx = createMechanicContext(casterEntity, List.of(TargetRef.of(targetEntity)));
        MechanicResult result = mechanicRegistry.execute("swap", ctx, Map.of());

        assertTrue(result.valid());
        assertNotNull(casterTeleported.get());
        assertNotNull(targetTeleported.get());
        assertEquals(15, casterTeleported.get().getX());
        assertEquals(0, targetTeleported.get().getX());
    }

    @Test
    void testIgniteAndExtinguishMechanics() {
        AtomicInteger fireTicks = new AtomicInteger(0);
        Location loc = new Location(mockWorld, 0, 64, 0);
        LivingEntity entity = createMockEntity(loc, null, null, null, null, fireTicks);

        MechanicContext ctx = createMechanicContext(entity, List.of(TargetRef.of(entity)));

        MechanicResult igniteRes = mechanicRegistry.execute("ignite", ctx, Map.of("ticks", 120));
        assertTrue(igniteRes.valid());
        assertEquals(120, fireTicks.get());

        MechanicResult extRes = mechanicRegistry.execute("extinguish", ctx, Map.of());
        assertTrue(extRes.valid());
        assertEquals(0, fireTicks.get());
    }

    @Test
    void testHealPercentAndDamagePercent() {
        AtomicReference<Double> currentHealth = new AtomicReference<>(50.0);
        AtomicReference<Double> damageInflicted = new AtomicReference<>(0.0);
        Location loc = new Location(mockWorld, 0, 64, 0);

        LivingEntity living = createMockEntity(loc, null, null, damageInflicted, currentHealth, null);

        MechanicContext ctx = createMechanicContext(living, List.of(TargetRef.of(living)));

        // Heal 25% of 100 -> heals 25 HP -> 50 + 25 = 75
        MechanicResult healRes = mechanicRegistry.execute("healpercent", ctx, Map.of("percent", 0.25));
        assertTrue(healRes.valid());
        assertEquals(75.0, currentHealth.get(), 0.01);

        // Damage 10% of 100 max health -> 10 damage
        MechanicResult dmgRes = mechanicRegistry.execute("damagepercent", ctx, Map.of("amount", 0.10));
        assertTrue(dmgRes.valid());
        assertEquals(10.0, damageInflicted.get(), 0.01);
    }

    @Test
    void testPinMechanicsAndTargeterIntegration() {
        Location pinLoc = new Location(mockWorld, 100, 65, -200);
        LivingEntity casterEntity = createMockEntity(pinLoc, null, null, null, null, null);

        MechanicContext ctx = createMechanicContext(casterEntity, List.of(TargetRef.of(pinLoc)));

        // setpin mechanic
        MechanicResult setRes = mechanicRegistry.execute("setpin", ctx, Map.of("pin", "altar_core"));
        assertTrue(setRes.valid());
        assertTrue(pinManager.getPin("altar_core").isPresent());
        assertEquals(100, pinManager.getPin("altar_core").get().x());

        // Targeter @pin{name=altar_core}
        SkillCastContext castCtx = ctx.cast();
        Collection<Location> locations = targeterRegistry.resolveLocations("@pin{name=altar_core}", castCtx);
        assertFalse(locations.isEmpty());
        assertEquals(100, locations.iterator().next().getX());

        // Condition haspin
        ConditionContext condCtx = new ConditionContext(casterEntity, null, "default", new CooldownRegistry(), Map.of(), 0L);
        assertTrue(conditionRegistry.evaluate("haspin", condCtx, Map.of("name", "altar_core")).matched());
        assertFalse(conditionRegistry.evaluate("haspin", condCtx, Map.of("name", "non_existing")).matched());

        // Condition pindistance
        assertTrue(conditionRegistry.evaluate("pindistance", condCtx, Map.of("pin", "altar_core", "distance", "<=5")).matched());
        assertFalse(conditionRegistry.evaluate("pindistance", condCtx, Map.of("pin", "altar_core", "distance", ">10")).matched());

        // removepin mechanic
        MechanicResult remRes = mechanicRegistry.execute("removepin", ctx, Map.of("pin", "altar_core"));
        assertTrue(remRes.valid());
        assertFalse(pinManager.getPin("altar_core").isPresent());
    }

    @Test
    void testBehindAndInFrontConditions() {
        Location casterLoc = new Location(mockWorld, 0, 64, 0, 0, 0); // yaw 0 = +Z facing
        Location frontTargetLoc = new Location(mockWorld, 0, 64, 10);
        Location behindTargetLoc = new Location(mockWorld, 0, 64, -10);

        LivingEntity caster = createMockEntity(casterLoc, null, null, null, null, null);
        LivingEntity frontTarget = createMockEntity(frontTargetLoc, null, null, null, null, null);
        LivingEntity behindTarget = createMockEntity(behindTargetLoc, null, null, null, null, null);

        ConditionContext frontContext = new ConditionContext(caster, frontTarget, "default", new CooldownRegistry(), Map.of(), 0L);
        ConditionContext behindContext = new ConditionContext(caster, behindTarget, "default", new CooldownRegistry(), Map.of(), 0L);

        // In front
        assertTrue(conditionRegistry.evaluate("infront", frontContext, Map.of()).matched());
        assertFalse(conditionRegistry.evaluate("behind", frontContext, Map.of()).matched());

        // Behind
        assertTrue(conditionRegistry.evaluate("behind", behindContext, Map.of()).matched());
        assertFalse(conditionRegistry.evaluate("infront", behindContext, Map.of()).matched());
    }

    @Test
    void testGlidingAndSwimmingConditions() {
        AtomicBoolean isGliding = new AtomicBoolean(true);
        AtomicBoolean isSwimming = new AtomicBoolean(false);
        Location loc = new Location(mockWorld, 0, 64, 0);

        LivingEntity subject = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> UUID.randomUUID();
                    case "getLocation" -> loc.clone();
                    case "getWorld" -> mockWorld;
                    case "isGliding" -> isGliding.get();
                    case "isSwimming" -> isSwimming.get();
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> null;
                });

        ConditionContext ctx = new ConditionContext(subject, null, "default", new CooldownRegistry(), Map.of(), 0L);

        assertTrue(conditionRegistry.evaluate("gliding", ctx, Map.of()).matched());
        assertFalse(conditionRegistry.evaluate("swimming", ctx, Map.of()).matched());

        isGliding.set(false);
        isSwimming.set(true);

        assertFalse(conditionRegistry.evaluate("gliding", ctx, Map.of()).matched());
        assertTrue(conditionRegistry.evaluate("swimming", ctx, Map.of()).matched());
    }

    private LivingEntity createMockEntity(Location loc,
                                         AtomicReference<Vector> velocitySink,
                                         AtomicReference<Location> teleportSink,
                                         AtomicReference<Double> damageSink,
                                         AtomicReference<Double> healthSink,
                                         AtomicInteger fireTicksSink) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> loc != null ? loc.clone() : new Location(mockWorld, 0, 64, 0);
                    case "getWorld" -> loc != null ? loc.getWorld() : mockWorld;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> proxy == args[0] || (args[0] instanceof LivingEntity other && uuid.equals(other.getUniqueId()));
                    case "hashCode" -> uuid.hashCode();
                    case "setVelocity" -> {
                        if (velocitySink != null) velocitySink.set((Vector) args[0]);
                        yield null;
                    }
                    case "getVelocity" -> velocitySink != null && velocitySink.get() != null ? velocitySink.get() : new Vector(0, 0, 0);
                    case "teleport" -> {
                        if (teleportSink != null) teleportSink.set((Location) args[0]);
                        yield true;
                    }
                    case "damage" -> {
                        if (damageSink != null) damageSink.set((Double) args[0]);
                        yield null;
                    }
                    case "setHealth" -> {
                        if (healthSink != null) healthSink.set((Double) args[0]);
                        yield null;
                    }
                    case "getHealth" -> healthSink != null ? healthSink.get() : 100.0;
                    case "getMaxHealth" -> 100.0;
                    case "setFireTicks" -> {
                        if (fireTicksSink != null) fireTicksSink.set((Integer) args[0]);
                        yield null;
                    }
                    case "getFireTicks" -> fireTicksSink != null ? fireTicksSink.get() : 0;
                    default -> null;
                });
    }

    private MechanicContext createMechanicContext(LivingEntity casterEntity, List<TargetRef> targets) {
        MobDefinition definition = new MobDefinition(new MobDefinitionId("boss"), org.bukkit.entity.EntityType.IRON_GOLEM,
                "Boss", null, Map.of(), Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(casterEntity, definition, new LunarMobIdentity("boss", "1"));
        SkillDefinition skill = new SkillDefinition("test_skill", Set.of(SkillTrigger.ON_COMBAT), 0);
        return new MechanicContext(new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 0), targets);
    }

    @Test
    void testLunarLaunchMechanic() {
        AtomicReference<Vector> targetVelocity = new AtomicReference<>(new Vector(0, 0, 0));
        LivingEntity target = createMockEntity(new Location(mockWorld, 0, 64, 0), targetVelocity, null, null, null, null);
        LivingEntity caster = createMockEntity(new Location(mockWorld, 0, 64, 0), null, null, null, null, null);

        MechanicContext ctx = createMechanicContext(caster, List.of(TargetRef.of(target)));
        var res = mechanicRegistry.execute("lunar_launch", ctx, Map.of("strength", 2.5));
        assertTrue(res.isSuccess());
        assertNotNull(targetVelocity.get());
        assertEquals(2.5, targetVelocity.get().getY(), 0.001);
    }
}
