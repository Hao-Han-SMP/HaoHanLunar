package vn.haohan.lunar.core.skill.condition;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.skill.CooldownRegistry;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionRegistryTest {

    @Test
    void evaluatesHealthPhaseWorldCooldownAndVariables() {
        LivingEntity caster = entity(20, Set.of("boss"));
        CooldownRegistry cooldowns = new CooldownRegistry();
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 5);
        cooldowns.tryAcquire(caster.getUniqueId(), skill, 0);
        ConditionContext context = new ConditionContext(caster, null, "enraged", cooldowns,
                Map.of("score", 10), 1);
        ConditionRegistry registry = new ConditionRegistry();

        assertTrue(registry.evaluate("health_above", context, Map.of("value", 10)).matched());
        assertTrue(registry.evaluate("phase", context, Map.of("value", "ENRAGED")).matched());
        assertTrue(registry.evaluate("world", context, Map.of("value", "world")).matched());
        assertTrue(registry.evaluate("tag", context, Map.of("value", "boss")).matched());
        assertFalse(registry.evaluate("cooldown_ready", context, Map.of("skill", "slam")).matched());
        assertTrue(registry.evaluate("variable_compare", context,
                Map.of("variable", "score", "operator", ">", "value", 5)).matched());
    }

    @Test
    void invalidParametersReturnValidationErrorInsteadOfThrowing() {
        ConditionRegistry registry = new ConditionRegistry();
        ConditionContext context = new ConditionContext(entity(10, Set.of()), null, null,
                new CooldownRegistry(), Map.of(), 0);

        ConditionResult result = registry.evaluate("health_below", context, Map.of("value", "not-number"));
        ConditionResult unknown = registry.evaluate("missing", context, Map.of());

        assertFalse(result.valid());
        assertTrue(result.error().contains("numeric"));
        assertFalse(unknown.valid());
    }

    @Test
    void targetWithinAndLineOfSightRejectMissingTargetSafely() {
        ConditionRegistry registry = new ConditionRegistry();
        ConditionContext context = new ConditionContext(entity(10, Set.of()), null, null,
                new CooldownRegistry(), Map.of(), 0);

        assertFalse(registry.evaluate("target_within", context, Map.of("radius", 5)).valid());
        assertFalse(registry.evaluate("line_of_sight", context, Map.of()).valid());
    }

    @Test
    void supportsAndOrNotComposition() {
        ConditionRegistry registry = new ConditionRegistry();
        ConditionContext context = new ConditionContext(entity(10, Set.of("boss")), null, "one",
                new CooldownRegistry(), Map.of(), 0);
        var phase = new ConditionRegistry.ConditionCall("phase", Map.of("value", "one"));
        var tag = new ConditionRegistry.ConditionCall("tag", Map.of("value", "boss"));
        var wrong = new ConditionRegistry.ConditionCall("phase", Map.of("value", "two"));

        assertTrue(registry.and(context, List.of(phase, tag)).matched());
        assertTrue(registry.or(context, List.of(wrong, tag)).matched());
        assertTrue(registry.not(context, wrong).matched());
    }

    private static LivingEntity entity(double health, Set<String> tags) {
        UUID uuid = UUID.randomUUID();
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft("overworld");
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getHealth" -> health;
                    case "getWorld" -> world;
                    case "getLocation" -> new Location(world, 0, 64, 0);
                    case "getScoreboardTags" -> tags;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "hasLineOfSight" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
