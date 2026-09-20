package vn.haohan.lunar.core.combat;

import vn.haohan.lunar.api.system.combat.*;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobOptionDefinition;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageModifierAndImmunityTest {

    @Test
    void damageModifierTableMultipliers() {
        DamageModifierTable table = new DamageModifierTable(
                Map.of(DamageCause.FIRE, 0.5, DamageCause.FALL, 0.0, DamageCause.PROJECTILE, 1.5),
                Map.of(EntityType.IRON_GOLEM, 0.2, EntityType.PLAYER, 1.0)
        );

        // 1. Fire damage reduced by 50%
        assertEquals(0.5, table.calculateMultiplier(DamageCause.FIRE, EntityType.PLAYER), 0.001);

        // 2. Fall damage immune (0.0)
        assertEquals(0.0, table.calculateMultiplier(DamageCause.FALL, null), 0.001);

        // 3. Projectile damage increased by 150%
        assertEquals(1.5, table.calculateMultiplier(DamageCause.PROJECTILE, null), 0.001);

        // 4. Fire damage from Iron Golem combines: 0.5 * 0.2 = 0.1
        assertEquals(0.1, table.calculateMultiplier(DamageCause.FIRE, EntityType.IRON_GOLEM), 0.001);

        // 5. Unconfigured cause and entity default to 1.0
        assertEquals(1.0, table.calculateMultiplier(DamageCause.ENTITY_ATTACK, EntityType.ZOMBIE), 0.001);
    }

    @Test
    void immunityTableExpiryAndChecks() {
        AtomicLong currentTick = new AtomicLong(100L);
        ImmunityTable immunity = new ImmunityTable(currentTick::get);

        // Add 50-tick projectile immunity, expires at 150
        immunity.addCauseImmunity(DamageCause.PROJECTILE, 50);
        // Add 30-tick skill immunity for "meteor_strike", expires at 130
        immunity.addSkillImmunity("meteor_strike", 30);
        // Add 40-tick magical type immunity, expires at 140
        immunity.addTypeImmunity(DamageType.MAGICAL, 40);

        // At tick 100: all active
        assertTrue(immunity.hasCauseImmunity(DamageCause.PROJECTILE, currentTick.get()));
        assertTrue(immunity.hasSkillImmunity("meteor_strike", currentTick.get()));
        assertTrue(immunity.hasTypeImmunity(DamageType.MAGICAL, currentTick.get()));
        assertFalse(immunity.hasCauseImmunity(DamageCause.FALL, currentTick.get()));

        // Check damage context at tick 120
        currentTick.set(120L);
        DamageContext ctxProj = DamageContext.builder()
                .victim(mockLiving())
                .cause(DamageCause.PROJECTILE)
                .baseDamage(10.0)
                .build();
        assertTrue(immunity.checkImmunity(ctxProj, currentTick.get()).isPresent());

        DamageContext ctxSkill = DamageContext.builder()
                .victim(mockLiving())
                .skillId("meteor_strike")
                .baseDamage(10.0)
                .build();
        assertTrue(immunity.checkImmunity(ctxSkill, currentTick.get()).isPresent());

        // Advance to tick 135: skill immunity expired, projectile and magical still active
        currentTick.set(135L);
        assertFalse(immunity.hasSkillImmunity("meteor_strike", currentTick.get()));
        assertTrue(immunity.hasCauseImmunity(DamageCause.PROJECTILE, currentTick.get()));
        assertTrue(immunity.hasTypeImmunity(DamageType.MAGICAL, currentTick.get()));

        // Advance to tick 200: all expired
        currentTick.set(200L);
        assertFalse(immunity.hasCauseImmunity(DamageCause.PROJECTILE, currentTick.get()));
        assertFalse(immunity.hasTypeImmunity(DamageType.MAGICAL, currentTick.get()));
    }

    @Test
    void pipelineIntegratesMobModifiersAndImmunity() {
        DamagePipeline pipeline = new DamagePipeline();
        AtomicReference<Double> appliedRef = new AtomicReference<>(0.0);
        LivingEntity victimEntity = mockLiving(appliedRef::set);
        ActiveLunarMob victimMob = mockActiveMob(victimEntity, "boss_lunar");

        victimMob.setDamageModifiers(new DamageModifierTable(
                Map.of(DamageCause.FIRE, 0.5, DamageCause.FALL, 0.0),
                Map.of(EntityType.PLAYER, 1.0)
        ));

        // 1. Fire damage is reduced by 50%
        DamageContext fireCtx = DamageContext.builder()
                .victim(victimEntity)
                .victimMob(victimMob)
                .cause(DamageCause.FIRE)
                .baseDamage(40.0)
                .build();
        DamageResult fireResult = pipeline.execute(fireCtx);
        assertTrue(fireResult.executed());
        assertEquals(20.0, fireResult.appliedDamage(), 0.001);
        assertEquals(20.0, appliedRef.get(), 0.001);

        // 2. Fall damage is reduced to 0.0 -> cancels automatically
        DamageContext fallCtx = DamageContext.builder()
                .victim(victimEntity)
                .victimMob(victimMob)
                .cause(DamageCause.FALL)
                .baseDamage(50.0)
                .build();
        DamageResult fallResult = pipeline.execute(fallCtx);
        assertFalse(fallResult.executed());
        assertTrue(fallResult.cancelled());

        // 3. Grant temporary true damage immunity to the boss
        victimMob.immunityTable().addTypeImmunity(DamageType.TRUE, 5000);
        DamageContext trueCtx = DamageContext.builder()
                .victim(victimEntity)
                .victimMob(victimMob)
                .damageType(DamageType.TRUE)
                .baseDamage(15.0)
                .build();
        DamageResult trueResult = pipeline.execute(trueCtx);
        assertFalse(trueResult.executed());
        assertTrue(trueResult.cancelled());
        assertTrue(trueResult.cancellationReason().orElse("").contains("immune to TRUE"));
    }

    @Test
    void immunityMechanicInRegistryAppliesToCasterMob() {
        MechanicRegistry registry = new MechanicRegistry();
        LivingEntity casterEntity = mockLiving(d -> {});
        ActiveLunarMob casterMob = mockActiveMob(casterEntity, "boss_mechanic");

        SkillDefinition skill = new SkillDefinition(
                "channel_barrier",
                Set.of(SkillTrigger.ON_TIMER),
                10,
                List.of()
        );
        SkillCastContext castContext = new SkillCastContext(casterMob, skill, SkillTrigger.ON_TIMER, 1L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of(TargetRef.entity(casterEntity)));

        // Execute immunity mechanic: immune to PROJECTILE for 100 ticks
        MechanicResult result = registry.execute("immunity", mechContext, Map.of(
                "duration", 100,
                "cause", "PROJECTILE"
        ));
        assertTrue(result.valid());
        assertTrue(casterMob.immunityTable().hasCauseImmunity(DamageCause.PROJECTILE, System.currentTimeMillis() + 50));
    }

    // --- Helpers ---

    private static LivingEntity mockLiving() {
        return mockLiving(d -> {});
    }

    private static LivingEntity mockLiving(java.util.function.Consumer<Double> onDamage) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "isInvulnerable" -> false;
                    case "damage" -> {
                        if (args != null && args.length > 0 && args[0] instanceof Double amt) {
                            onDamage.accept(amt);
                        }
                        yield null;
                    }
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
