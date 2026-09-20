package vn.haohan.lunar.core.skill.condition;

import vn.haohan.lunar.api.system.combat.skill.condition.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedConditionsTest {

    @Test
    void damageCauseCondition() {
        ConditionRegistry registry = new ConditionRegistry();
        LivingEntity caster = createMockEntity(0, 50, 0);

        ConditionContext ctx = new ConditionContext(caster, null, "P1", new CooldownRegistry(),
                Map.of("damage_cause", "ENTITY_ATTACK"), 100);

        ConditionResult match = registry.evaluate("damagecause", ctx, Map.of("cause", "ENTITY_ATTACK"));
        assertTrue(match.valid());
        assertTrue(match.matched());

        ConditionResult mismatch = registry.evaluate("damagecause", ctx, Map.of("cause", "FALL"));
        assertTrue(mismatch.valid());
        assertFalse(mismatch.matched());
    }

    @Test
    void altitudeCondition() {
        ConditionRegistry registry = new ConditionRegistry();
        LivingEntity caster = createMockEntity(0, 45, 0); // Y = 45

        ConditionContext ctx = new ConditionContext(caster, null, "P1", new CooldownRegistry(), Map.of(), 100);

        // Y <= 50 -> true
        ConditionResult r1 = registry.evaluate("altitude", ctx, Map.of("y", 50.0, "compare", "<="));
        assertTrue(r1.valid());
        assertTrue(r1.matched());

        // Y <= 30 -> false
        ConditionResult r2 = registry.evaluate("altitude", ctx, Map.of("y", 30.0, "compare", "<="));
        assertTrue(r2.valid());
        assertFalse(r2.matched());
    }

    @Test
    void timeAliveCondition() {
        ConditionRegistry registry = new ConditionRegistry();
        LivingEntity caster = createMockEntity(0, 50, 0); // ticksLived = 200

        ConditionContext ctx = new ConditionContext(caster, null, "P1", new CooldownRegistry(), Map.of(), 100);

        // ticks >= 100 -> true
        ConditionResult r1 = registry.evaluate("timealive", ctx, Map.of("ticks", 100, "compare", ">="));
        assertTrue(r1.valid());
        assertTrue(r1.matched());

        // ticks >= 500 -> false
        ConditionResult r2 = registry.evaluate("timealive", ctx, Map.of("ticks", 500, "compare", ">="));
        assertTrue(r2.valid());
        assertFalse(r2.matched());
    }

    @Test
    void lunarPhaseCondition() {
        ConditionRegistry registry = new ConditionRegistry();
        LivingEntity caster = createMockEntity(0, 50, 0); // world fullTime = 0 -> day 0 -> FULL_MOON

        ConditionContext ctx = new ConditionContext(caster, null, "P1", new CooldownRegistry(), Map.of(), 100);

        ConditionResult r1 = registry.evaluate("lunarphase", ctx, Map.of("phase", "FULL_MOON"));
        assertTrue(r1.valid());
        assertTrue(r1.matched());

        ConditionResult r2 = registry.evaluate("lunarphase", ctx, Map.of("phase", "NEW_MOON"));
        assertTrue(r2.valid());
        assertFalse(r2.matched());
    }

    @Test
    void targetCountCondition() {
        ConditionRegistry registry = new ConditionRegistry();
        LivingEntity caster = createMockEntity(0, 50, 0); // getNearbyEntities returns empty list -> count = 0

        ConditionContext ctx = new ConditionContext(caster, null, "P1", new CooldownRegistry(), Map.of(), 100);

        ConditionResult r1 = registry.evaluate("targetcount", ctx, Map.of("amount", 0, "compare", "<="));
        assertTrue(r1.valid());
        assertTrue(r1.matched());

        ConditionResult r2 = registry.evaluate("targetcount", ctx, Map.of("amount", 2, "compare", ">="));
        assertTrue(r2.valid());
        assertFalse(r2.matched());
    }

    private static LivingEntity createMockEntity(double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        World mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("getFullTime")) return 0L;
                    if (method.getName().equals("getNearbyEntities")) return List.of();
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });

        Location loc = new Location(mockWorld, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return mockWorld;
                    if (method.getName().equals("getTicksLived")) return 200;
                    if (method.getName().equals("getType")) return EntityType.ZOMBIE;
                    if (method.getName().equals("getEquipment")) return null;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
