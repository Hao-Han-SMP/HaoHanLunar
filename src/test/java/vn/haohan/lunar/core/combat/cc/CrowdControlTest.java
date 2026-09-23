package vn.haohan.lunar.core.combat.cc;

import vn.haohan.lunar.api.system.combat.cc.*;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.system.combat.DamageResult;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CrowdControlTest {

    @Test
    void testBasicApplicationAndExpiration() {
        AtomicLong tick = new AtomicLong(100L);
        CrowdControlTracker tracker = new CrowdControlTracker(tick::get);
        UUID source = UUID.randomUUID();

        // Apply STUN for 50 ticks (expires at 150)
        assertTrue(tracker.apply(CCState.STUN, 50, source, 1));
        assertTrue(tracker.isStunned());
        assertFalse(tracker.canMove());
        assertFalse(tracker.canCast());
        assertFalse(tracker.canAttack());

        // At tick 140 -> still active
        tick.set(140L);
        assertTrue(tracker.isStunned());
        assertFalse(tracker.canMove());

        // At tick 150 -> expired
        tick.set(150L);
        assertFalse(tracker.isStunned());
        assertTrue(tracker.canMove());
        assertTrue(tracker.canCast());
        assertTrue(tracker.canAttack());
    }

    @Test
    void testPriorityAndStackingRules() {
        AtomicLong tick = new AtomicLong(0L);
        CrowdControlTracker tracker = new CrowdControlTracker(tick::get);
        UUID source1 = UUID.randomUUID();
        UUID source2 = UUID.randomUUID();

        // Apply ROOT with priority 1 for 20 ticks (expires at 20)
        assertTrue(tracker.apply(CCState.ROOT, 20, source1, 1));
        assertEquals(20L, tracker.getEffect(CCState.ROOT).orElseThrow().expiryTick());

        // Attempt to apply lower priority ROOT (priority 0) -> rejected
        assertFalse(tracker.apply(CCState.ROOT, 100, source2, 0));
        assertEquals(20L, tracker.getEffect(CCState.ROOT).orElseThrow().expiryTick());

        // Apply equal priority ROOT (priority 1) with longer duration (expires at 50) -> extended
        assertTrue(tracker.apply(CCState.ROOT, 50, source2, 1));
        assertEquals(50L, tracker.getEffect(CCState.ROOT).orElseThrow().expiryTick());

        // Apply higher priority ROOT (priority 5) -> overrides
        assertTrue(tracker.apply(CCState.ROOT, 10, source1, 5));
        assertEquals(5, tracker.getEffect(CCState.ROOT).orElseThrow().priority());
        assertEquals(10L, tracker.getEffect(CCState.ROOT).orElseThrow().expiryTick());
    }

    @Test
    void testImmunityBlocksAndCleansUp() {
        CrowdControlTracker tracker = new CrowdControlTracker();
        tracker.apply(CCState.FEAR, 50, UUID.randomUUID(), 1);
        assertTrue(tracker.isFeared());

        // Adding immunity instantly removes existing active effect and blocks new ones
        tracker.addImmunity(CCState.FEAR);
        assertFalse(tracker.isFeared());
        assertTrue(tracker.isImmune(CCState.FEAR));

        assertFalse(tracker.apply(CCState.FEAR, 100, UUID.randomUUID(), 10));
        assertFalse(tracker.isFeared());

        // Removing immunity allows application again
        tracker.removeImmunity(CCState.FEAR);
        assertFalse(tracker.isImmune(CCState.FEAR));
        assertTrue(tracker.apply(CCState.FEAR, 100, UUID.randomUUID(), 1));
        assertTrue(tracker.isFeared());
    }

    @Test
    void testStatePermissions() {
        CrowdControlTracker tracker = new CrowdControlTracker();

        // ROOT prevents moving, but allows casting and attacking
        tracker.apply(CCState.ROOT, 100, null, 1);
        assertFalse(tracker.canMove());
        assertTrue(tracker.canCast());
        assertTrue(tracker.canAttack());

        tracker.clear();

        // SILENCE prevents casting, allows moving and attacking
        tracker.apply(CCState.SILENCE, 100, null, 1);
        assertTrue(tracker.canMove());
        assertFalse(tracker.canCast());
        assertTrue(tracker.canAttack());

        tracker.clear();

        // DISARM prevents attacking, allows moving and casting
        tracker.apply(CCState.DISARM, 100, null, 1);
        assertTrue(tracker.canMove());
        assertTrue(tracker.canCast());
        assertFalse(tracker.canAttack());
    }

    @Test
    void testDamagePipelineBlocksInvulnerableVictim() {
        DamagePipeline pipeline = new DamagePipeline();
        LivingEntity victimEntity = mockLiving();
        ActiveLunarMob victimMob = mockActiveMob(victimEntity, "boss_target");

        // Apply invulnerable CC
        victimMob.crowdControl().apply(CCState.INVULNERABLE, 100, null, 1);

        DamageContext ctx = DamageContext.builder()
                .victim(victimEntity)
                .victimMob(victimMob)
                .baseDamage(50.0)
                .cause(DamageCause.MAGIC)
                .build();

        DamageResult result = pipeline.execute(ctx);
        assertFalse(result.executed());
        assertTrue(result.cancelled());
        assertTrue(result.cancellationReason().orElse("").contains("CCState.INVULNERABLE"));
    }

    @Test
    void testDamagePipelineBlocksStunnedAndDisarmedAttacker() {
        DamagePipeline pipeline = new DamagePipeline();
        LivingEntity attackerEntity = mockLiving();
        ActiveLunarMob attackerMob = mockActiveMob(attackerEntity, "attacker_boss");
        LivingEntity victimEntity = mockLiving();

        // 1. Attacker is stunned -> all damage blocked
        attackerMob.crowdControl().apply(CCState.STUN, 100, null, 1);

        DamageContext ctx1 = DamageContext.builder()
                .attacker(attackerEntity)
                .attackerMob(attackerMob)
                .victim(victimEntity)
                .baseDamage(20.0)
                .cause(DamageCause.ENTITY_ATTACK)
                .build();

        DamageResult result1 = pipeline.execute(ctx1);
        assertFalse(result1.executed());
        assertTrue(result1.cancelled());
        assertTrue(result1.cancellationReason().orElse("").contains("CCState.STUN"));

        // 2. Attacker is disarmed -> basic attacks blocked, but spell damage allowed
        attackerMob.crowdControl().remove(CCState.STUN);
        attackerMob.crowdControl().apply(CCState.DISARM, 100, null, 1);

        DamageContext basicAttackCtx = DamageContext.builder()
                .attacker(attackerEntity)
                .attackerMob(attackerMob)
                .victim(victimEntity)
                .baseDamage(20.0)
                .cause(DamageCause.ENTITY_ATTACK)
                .build();

        DamageResult basicResult = pipeline.execute(basicAttackCtx);
        assertFalse(basicResult.executed());
        assertTrue(basicResult.cancelled());
        assertTrue(basicResult.cancellationReason().orElse("").contains("CCState.DISARM"));

        // Spell / Magic damage by disarmed attacker is allowed
        DamageContext spellCtx = DamageContext.builder()
                .attacker(attackerEntity)
                .attackerMob(attackerMob)
                .victim(victimEntity)
                .baseDamage(20.0)
                .cause(DamageCause.MAGIC)
                .build();

        DamageResult spellResult = pipeline.execute(spellCtx);
        assertTrue(spellResult.executed());
    }

    @Test
    void testMechanicRegistryAppliesCC() {
        MechanicRegistry registry = new MechanicRegistry();
        LivingEntity casterEntity = mockLiving();
        ActiveLunarMob casterMob = mockActiveMob(casterEntity, "caster_mob");

        SkillDefinition skill = new SkillDefinition("freeze_blast", Set.of(SkillTrigger.ON_TIMER), 10, List.of());
        SkillCastContext castContext = new SkillCastContext(casterMob, skill, SkillTrigger.ON_TIMER, 1L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of(TargetRef.entity(casterEntity)));

        // Execute stun mechanic
        MechanicResult stunRes = registry.execute("stun", mechContext, Map.of("duration", 80, "priority", 2));
        assertTrue(stunRes.valid());
        assertTrue(casterMob.crowdControl().isStunned());

        // Execute cc mechanic with SILENCE
        MechanicResult ccRes = registry.execute("cc", mechContext, Map.of("state", "SILENCE", "duration", 60));
        assertTrue(ccRes.valid());
        assertTrue(casterMob.crowdControl().isSilenced());
    }

    // --- Helpers ---

    private static LivingEntity mockLiving() {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "isInvulnerable" -> false;
                    case "damage" -> null;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "MockLiving[" + uuid + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ActiveLunarMob mockActiveMob(LivingEntity entity, String id) {
        MobDefinition def = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.IRON_GOLEM,
                "TestBoss",
                null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 500.0)),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );
        LunarMobIdentity identity = new LunarMobIdentity(id, "1.0.0");
        return new ActiveLunarMob(entity, def, identity);
    }
}
