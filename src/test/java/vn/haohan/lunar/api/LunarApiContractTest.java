package vn.haohan.lunar.api;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamageResult;
import vn.haohan.lunar.api.system.combat.DamageType;
import vn.haohan.lunar.api.manager.CombatManager;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.manager.MobManager;
import vn.haohan.lunar.api.manager.SkillManager;
import vn.haohan.lunar.api.system.combat.skill.target.Targeter;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.equipment.ItemProviderRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LunarApiContractTest {

    private vn.haohan.lunar.core.mob.LunarMobManager coreMobManager;
    private SkillRegistry coreSkillRegistry;
    private DamagePipeline coreDamagePipeline;
    private DropManager coreDropManager;
    private FixedSpawnerManager coreSpawnerManager;
    private ItemProviderRegistry coreItemProvider;

    @BeforeEach
    void setUp() {
        coreMobManager = new vn.haohan.lunar.core.mob.LunarMobManager(entity -> {});
        var mobRegistry = new MobDefinitionRegistry();
        coreSkillRegistry = new SkillRegistry();
        coreDamagePipeline = new DamagePipeline();
        var conditionRegistry = new ConditionRegistry();
        var mechanicRegistry = new MechanicRegistry();
        var targeterRegistry = new TargeterRegistry();
        coreSkillRegistry.setRegistries(mechanicRegistry, conditionRegistry, targeterRegistry);

        coreDropManager = new DropManager(
                mobRegistry, coreMobManager,
                HaoHanItemBridge.get(),
                conditionRegistry
        );
        coreSpawnerManager = new FixedSpawnerManager(mobRegistry, coreMobManager);
        coreItemProvider = new ItemProviderRegistry();

        // Wire to public LunarAPI
        LunarAPI.setMobManager(coreMobManager);
        LunarAPI.setSkillManager(coreSkillRegistry);
        LunarAPI.setCombatManager(coreDamagePipeline);
        LunarAPI.setLootManager(coreDropManager);
        LunarAPI.setSpawnerManager(coreSpawnerManager);
        LunarAPI.setItemProvider(coreItemProvider);
    }

    @Test
    @DisplayName("LunarAPI service locator exposes all core subsystems cleanly")
    void testLunarApiFacade() {
        assertTrue(LunarAPI.isReady());
        assertNotNull(LunarAPI.getMobManager());
        assertNotNull(LunarAPI.getSkillManager());
        assertNotNull(LunarAPI.getCombatManager());
        assertNotNull(LunarAPI.getLootManager());
        assertNotNull(LunarAPI.getSpawnerManager());
        assertNotNull(LunarAPI.getItemProvider());
    }

    @Test
    @DisplayName("MobManager API correctly queries active mobs")
    void testLunarMobManagerApi() {
        MobManager manager = LunarAPI.getMobManager();
        assertEquals(0, manager.activeCount());
        assertTrue(manager.getActiveMobs().isEmpty());

        UUID dummyUuid = UUID.randomUUID();
        assertFalse(manager.isManaged(dummyUuid));
        assertTrue(manager.getMob(dummyUuid).isEmpty());
        assertNull(manager.unregister(dummyUuid));
    }

    @Test
    @DisplayName("SkillManager API allows registering custom targeters and checking skills")
    void testLunarSkillManagerApi() {
        SkillManager skillManager = LunarAPI.getSkillManager();

        assertFalse(skillManager.hasSkill("non_existent_skill"));

        // Register custom targeter through API
        Targeter customTargeter = ctx -> List.of();
        assertDoesNotThrow(() -> skillManager.registerTargeter("custom_targeter", customTargeter));

        // Register custom condition through API
        assertDoesNotThrow(() -> skillManager.registerCondition("custom_condition", (context, parameters) -> true));

        // Register custom mechanic through API
        assertDoesNotThrow(() -> skillManager.registerMechanic("custom_mechanic", (context, parameters) -> {}));
    }

    @Test
    @DisplayName("ThreatTable API satisfies full hostility management contract")
    void testThreatTableApi() {
        UUID mobId = UUID.randomUUID();
        vn.haohan.lunar.api.system.combat.ThreatTable threatTable = new ThreatTable(mobId);

        assertEquals(mobId, threatTable.mobId());
        assertEquals(0.0, threatTable.getThreat(UUID.randomUUID()));
        assertTrue(threatTable.getTopTarget().isEmpty());

        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        threatTable.addThreat(playerA, 50.0);
        threatTable.addThreat(playerB, 100.0);

        assertEquals(50.0, threatTable.getThreat(playerA));
        assertEquals(100.0, threatTable.getThreat(playerB));
        assertEquals(150.0, threatTable.totalThreat());
        assertEquals(Optional.of(playerB), threatTable.getTopTarget());

        threatTable.clearTarget(playerA);
        assertEquals(0.0, threatTable.getThreat(playerA));
        assertEquals(100.0, threatTable.totalThreat());

        threatTable.clearAll();
        assertEquals(0.0, threatTable.totalThreat());
        assertTrue(threatTable.isEmpty());
    }

    @Test
    @DisplayName("DamageContext and DamagePipeline API allow pre-checks and modifiers")
    void testCombatPipelineApi() {
        CombatManager combatManager = LunarAPI.getCombatManager();

        LivingEntity dummyVictim = (LivingEntity) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if ("isValid".equals(method.getName())) return true;
                    if ("isDead".equals(method.getName())) return false;
                    if ("isInvulnerable".equals(method.getName())) return false;
                    if ("getUniqueId".equals(method.getName())) return UUID.randomUUID();
                    if ("damage".equals(method.getName())) return null;
                    return null;
                }
        );

        DamageContext ctx = DamageContext.builder()
                .victim(dummyVictim)
                .baseDamage(100.0)
                .damageType(DamageType.MAGICAL)
                .build();

        assertEquals(100.0, ctx.baseDamage());
        assertEquals(DamageType.MAGICAL, ctx.damageType());

        DamageResult result = combatManager.execute(ctx);
        assertTrue(result.executed());
        assertEquals(100.0, result.appliedDamage());
    }
}
