package vn.haohan.lunar.api.service;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.LunarAPI;
import vn.haohan.lunar.core.mob.ActiveMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.service.engine.ItemCoreService;
import vn.haohan.lunar.api.service.engine.MobCoreService;
import vn.haohan.lunar.api.service.engine.PinService;
import vn.haohan.lunar.api.service.engine.PlayerDataService;
import vn.haohan.lunar.core.util.SafeExpressionEvaluator;
import vn.haohan.lunar.api.system.variable.VariableValue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MobCoreServiceTest {

    private static MechanicContext createTestContext() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("test_mob"), EntityType.IRON_GOLEM,
                "TestMob", null, Map.of(), Map.of(), List.of(), null, Set.of());
        ActiveMob mob = new ActiveMob(entity, definition, new LunarMobIdentity("test_mob", "1"));
        SkillDefinition skill = new SkillDefinition("test", Set.of(SkillTrigger.ON_COMBAT), 0);
        return new MechanicContext(new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 0), List.of());
    }

    @Test
    void testServiceInitializationAndDependencyInjection() {
        MobCoreService subSystem = new MobCoreService();
        subSystem.init(null);

        assertNotNull(subSystem.getMobManager(), "IMobManager must be initialized");
        assertNotNull(subSystem.getMobRegistry(), "MobRegistry must be initialized");
        assertNotNull(subSystem.getSkillRegistry(), "SkillRegistry must be initialized");
        assertNotNull(subSystem.getDamagePipeline(), "DamagePipeline must be initialized");
        assertNotNull(subSystem.getConditionRegistry(), "ConditionRegistry must be initialized");
        assertNotNull(subSystem.getMechanicRegistry(), "MechanicRegistry must be initialized");
        assertNotNull(subSystem.getTargeterRegistry(), "TargeterRegistry must be initialized");
        assertNotNull(subSystem.getDropManager(), "DropManager must be initialized");
        assertNotNull(subSystem.getFixedSpawnerManager(), "FixedSpawnerManager must be initialized");
        assertNotNull(subSystem.getAuraRegistry(), "AuraRegistry must be initialized");
        assertNotNull(subSystem.getAuraScheduler(), "AuraScheduler must be initialized");
        assertNotNull(subSystem.getProjectileTracker(), "ProjectileTracker must be initialized");
        assertNotNull(subSystem.getVariableManager(), "VariableManager must be initialized");
        assertNotNull(subSystem.getDynamicThrottlingEngine(), "DynamicThrottlingEngine must be initialized");
        assertNotNull(subSystem.getSkillScheduler(), "SkillScheduler must be initialized");

        // Verify LunarAPI bindings
        assertNotNull(LunarAPI.getMobManager());
        assertNotNull(LunarAPI.getSkillManager());
        assertNotNull(LunarAPI.getCombatManager());
        assertNotNull(LunarAPI.getLootManager());
        assertNotNull(LunarAPI.getSpawnerManager());

        // Test Variable mechanic execution via injected VariableManager
        MechanicContext ctx = createTestContext();
        subSystem.getMechanicRegistry().execute("setvariable", ctx, Map.of(
                "var", "test_score",
                "val", "150.0",
                "scope", "GLOBAL"
        ));
        assertEquals(150.0, subSystem.getVariableManager().getGlobal().get("test_score").map(VariableValue::asDouble).orElse(0.0));

        subSystem.getMechanicRegistry().execute("variableadd", ctx, Map.of(
                "var", "test_score",
                "val", "50.0",
                "scope", "GLOBAL"
        ));
        assertEquals(200.0, subSystem.getVariableManager().getGlobal().get("test_score").map(VariableValue::asDouble).orElse(0.0));

        // Subsystem disable cleanup
        subSystem.disable(null);
    }

    @Test
    void testTickLoopWithThrottlingAndSchedulers() {
        MobCoreService subSystem = new MobCoreService();
        subSystem.init(null);

        assertTrue(subSystem.isTickable());

        // Run ticks to ensure batch processing and throttler execute cleanly
        for (int i = 0; i < 45; i++) {
            assertDoesNotThrow(subSystem::tick);
        }

        subSystem.disable(null);
    }

    @Test
    void testSafeExpressionEvaluatorASTAndConstantCache() {
        // Deterministic constant expression caching
        double val1 = SafeExpressionEvaluator.evaluate("100 * 2.5 + (50 - 10) / 2");
        double val2 = SafeExpressionEvaluator.evaluate("100 * 2.5 + (50 - 10) / 2");
        assertEquals(270.0, val1);
        assertEquals(val1, val2);

        // Variable evaluation with cached token streams
        Map<String, Double> varsA = Map.of("base_dmg", 50.0, "mult", 2.0);
        Map<String, Double> varsB = Map.of("base_dmg", 100.0, "mult", 1.5);
        assertEquals(100.0, SafeExpressionEvaluator.evaluate("base_dmg * mult", varsA, 0.0));
        assertEquals(150.0, SafeExpressionEvaluator.evaluate("base_dmg * mult", varsB, 0.0));

        // Non-deterministic random expression should evaluate without caching stale random numbers
        double rand1 = SafeExpressionEvaluator.evaluate("random()");
        assertTrue(rand1 >= 0.0 && rand1 < 1.0);
    }

    @Test
    void testServicePriorityOrdering() {
        MobCoreService mob = new MobCoreService();
        ItemCoreService item = new ItemCoreService();
        PlayerDataService player = new PlayerDataService();
        PinService pin = new PinService();

        assertEquals(100, mob.priority());
        assertEquals(95, item.priority());
        assertEquals(90, player.priority());
        assertEquals(85, pin.priority());

        assertTrue(mob.priority() > item.priority());
        assertTrue(item.priority() > player.priority());
        assertTrue(player.priority() > pin.priority());
    }
}
