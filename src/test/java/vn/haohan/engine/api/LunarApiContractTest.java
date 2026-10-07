package vn.haohan.engine.api;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.engine.api.system.combat.DamageContext;
import vn.haohan.engine.api.system.combat.DamageResult;
import vn.haohan.engine.api.system.combat.DamageType;
import vn.haohan.engine.api.manager.ICombatManager;
import vn.haohan.engine.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.engine.api.manager.IMobManager;
import vn.haohan.engine.api.manager.ISkillManager;
import vn.haohan.engine.api.system.combat.skill.target.ITargeter;
import vn.haohan.engine.api.system.combat.DamagePipeline;
import vn.haohan.engine.api.system.combat.threat.IThreatTable;
import vn.haohan.engine.api.system.combat.threat.ThreatTable;
import vn.haohan.engine.api.system.loot.DropManager;
import vn.haohan.engine.api.system.mob.MobDefinitionRegistry;
import vn.haohan.engine.api.system.mob.equipment.ItemProviderRegistry;
import vn.haohan.engine.api.system.combat.skill.SkillRegistry;
import vn.haohan.engine.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.engine.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.engine.api.system.combat.skill.target.BasicTargeterRegistry;
import vn.haohan.engine.api.system.spawner.fixed.FixedSpawnerManager;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LunarApiContractTest {

    private vn.haohan.engine.core.mob.MobManager coreMobManager;
    private SkillRegistry coreSkillRegistry;
    private DamagePipeline coreDamagePipeline;
    private DropManager coreDropManager;
    private FixedSpawnerManager coreSpawnerManager;
    private ItemProviderRegistry coreItemProvider;

    @BeforeEach
    void setUp() {
        coreMobManager = new vn.haohan.engine.core.mob.MobManager(entity -> {});
        var mobRegistry = new MobDefinitionRegistry();
        coreSkillRegistry = new SkillRegistry();
        coreDamagePipeline = new DamagePipeline();
        var conditionRegistry = new ConditionRegistry();
        var mechanicRegistry = new MechanicRegistry();
        var targeterRegistry = new BasicTargeterRegistry();
        coreSkillRegistry.setRegistries(mechanicRegistry, conditionRegistry, targeterRegistry);

        coreDropManager = new DropManager(
                mobRegistry, coreMobManager,
                HaoHanItemBridge.get(),
                conditionRegistry
        );
        coreSpawnerManager = new FixedSpawnerManager(mobRegistry, coreMobManager);
        coreItemProvider = new ItemProviderRegistry();

        // Wire to public EngineAPI
        EngineAPI.setMobManager(coreMobManager);
        EngineAPI.setSkillManager(coreSkillRegistry);
        EngineAPI.setCombatManager(coreDamagePipeline);
        EngineAPI.setLootManager(coreDropManager);
        EngineAPI.setSpawnerManager(coreSpawnerManager);
        EngineAPI.setItemProvider(coreItemProvider);
    }

    @Test
    @DisplayName("EngineAPI service locator exposes all core subsystems cleanly")
    void testLunarApiFacade() {
        assertTrue(EngineAPI.isReady());
        assertNotNull(EngineAPI.getMobManager());
        assertNotNull(EngineAPI.getSkillManager());
        assertNotNull(EngineAPI.getCombatManager());
        assertNotNull(EngineAPI.getLootManager());
        assertNotNull(EngineAPI.getSpawnerManager());
        assertNotNull(EngineAPI.getItemProvider());
    }

    @Test
    @DisplayName("IMobManager API correctly queries active mobs")
    void testMobManagerApi() {
        IMobManager manager = EngineAPI.getMobManager();
        assertEquals(0, manager.activeCount());
        assertTrue(manager.getActiveMobs().isEmpty());

        UUID dummyUuid = UUID.randomUUID();
        assertFalse(manager.isManaged(dummyUuid));
        assertTrue(manager.getMob(dummyUuid).isEmpty());
        assertNull(manager.unregister(dummyUuid));
    }

    @Test
    @DisplayName("ISkillManager API allows registering custom targeters and checking skills")
    void testLunarSkillManagerApi() {
        ISkillManager skillManager = EngineAPI.getSkillManager();

        assertFalse(skillManager.hasSkill("non_existent_skill"));

        // Register custom targeter through API
        ITargeter customTargeter = ctx -> List.of();
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
        IThreatTable threatTable = new ThreatTable(mobId);

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
        ICombatManager combatManager = EngineAPI.getCombatManager();

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

@Test
    @DisplayName("IMob API contract exposes domain systems without casting to ActiveMob")
    void testIMobDomainApiContract() {
        LivingEntity dummyEntity = (LivingEntity) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if ("isValid".equals(method.getName())) return true;
                    if ("isDead".equals(method.getName())) return false;
                    if ("isInvulnerable".equals(method.getName())) return false;
                    if ("getUniqueId".equals(method.getName())) return UUID.randomUUID();
                    if ("getHealth".equals(method.getName())) return 100.0;
                    return null;
                }
        );

        vn.haohan.engine.api.system.mob.MobDefinition definition = new vn.haohan.engine.api.system.mob.MobDefinition(
                new vn.haohan.engine.api.system.mob.MobDefinitionId("test_mob"),
                org.bukkit.entity.EntityType.ZOMBIE,
                "Test Mob",
                null,
                java.util.Map.of(),
                java.util.Map.of(),
                java.util.List.of(),
                null,
                java.util.Set.of()
        );
        vn.haohan.engine.core.mob.MobIdentity identity = new vn.haohan.engine.core.mob.MobIdentity("test_mob", "inst-1");

        vn.haohan.engine.api.system.mob.IMob mob = new vn.haohan.engine.core.mob.ActiveMob(dummyEntity, definition, identity);

        // Verify API contracts
        assertNotNull(mob.stats());
        assertNotNull(mob.immunityTable());
        assertNotNull(mob.crowdControl());
        assertNotNull(mob.antiStuckController());
        assertNotNull(mob.damageModifiers());
        assertNotNull(mob.bossBars());

        assertFalse(mob.isInvulnerable(10L));
        mob.setInvulnerableTicks(20, 10L);
        assertTrue(mob.isInvulnerable(15L));
        assertFalse(mob.isInvulnerable(35L));

        assertFalse(mob.isBerserk());
        mob.setBerserk(true);
        assertTrue(mob.isBerserk());

        assertFalse(mob.isSoftLeashed());
        mob.setSoftLeashed(true);
        assertTrue(mob.isSoftLeashed());

        assertFalse(mob.isMounted());
        UUID mountId = UUID.randomUUID();
        mob.setMountUUID(mountId);
        assertTrue(mob.isMounted());
        assertEquals(mountId, mob.mountUUID());

        assertFalse(mob.hasRiders());
        assertFalse(mob.isChanneling());
        assertNull(mob.activeChannelingSkill());
        assertEquals(0, mob.interruptActiveSkills(vn.haohan.engine.api.system.combat.skill.interrupt.InterruptReason.STUNNED));
    }
}
