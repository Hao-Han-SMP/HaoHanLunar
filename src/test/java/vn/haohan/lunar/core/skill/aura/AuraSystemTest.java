package vn.haohan.lunar.core.skill.aura;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.skill.aura.component.FearAuraComponent;
import vn.haohan.lunar.core.skill.aura.component.FlyAuraComponent;
import vn.haohan.lunar.core.skill.aura.component.GlowAuraComponent;
import vn.haohan.lunar.core.skill.aura.component.OnAttackAuraComponent;
import vn.haohan.lunar.core.skill.aura.component.OnDamagedAuraComponent;
import vn.haohan.lunar.core.skill.aura.component.StatAuraComponent;
import vn.haohan.lunar.core.skill.mechanic.MechanicContext;
import vn.haohan.lunar.core.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.skill.SkillCastContext;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;
import vn.haohan.lunar.core.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraSystemTest {

    @Test
    void auraLifecycleAndStackModes() {
        AuraScheduler scheduler = new AuraScheduler();
        UUID entityUuid = UUID.randomUUID();
        LivingEntity mockEntity = createMockEntity(entityUuid);
        AuraAttachment attachment = AuraAttachment.ofEntity(mockEntity);

        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger tickCount = new AtomicInteger(0);
        AtomicBoolean expired = new AtomicBoolean(false);

        AuraComponent testComponent = new AuraComponent() {
            @Override
            public void onStart(ActiveAura aura) {
                started.set(true);
            }

            @Override
            public void onTick(ActiveAura aura, long currentTick) {
                tickCount.incrementAndGet();
            }

            @Override
            public void onExpire(ActiveAura aura) {
                expired.set(true);
            }
        };

        // 1. REFRESH mode
        AuraDefinition defRefresh = AuraDefinition.builder("test_refresh")
                .durationTicks(100)
                .intervalTicks(10)
                .maxStacks(1)
                .stackMode(StackMode.REFRESH)
                .component(testComponent)
                .build();

        ActiveAura a1 = scheduler.applyAura(defRefresh, attachment, entityUuid, 1000);
        assertTrue(started.get());
        assertEquals(1, a1.currentStacks());
        assertEquals(1100, a1.endTick());

        // Refresh at tick 1050
        ActiveAura a1Refreshed = scheduler.applyAura(defRefresh, attachment, entityUuid, 1050);
        assertEquals(1, a1Refreshed.currentStacks());
        assertEquals(1150, a1Refreshed.endTick());

        // 2. ADD_STACK mode
        AuraDefinition defAddStack = AuraDefinition.builder("test_stack")
                .durationTicks(100)
                .intervalTicks(10)
                .maxStacks(3)
                .stackMode(StackMode.ADD_STACK)
                .build();

        ActiveAura s1 = scheduler.applyAura(defAddStack, attachment, entityUuid, 1000);
        assertEquals(1, s1.currentStacks());

        scheduler.applyAura(defAddStack, attachment, entityUuid, 1020);
        assertEquals(2, s1.currentStacks());

        scheduler.applyAura(defAddStack, attachment, entityUuid, 1040);
        assertEquals(3, s1.currentStacks());

        // Bounded at maxStacks (3)
        scheduler.applyAura(defAddStack, attachment, entityUuid, 1060);
        assertEquals(3, s1.currentStacks());

        // 3. Ticking and Natural Expiration
        scheduler.tick(1010);
        assertTrue(tickCount.get() >= 1);

        // Advance beyond expiration (1160)
        scheduler.tick(1170);
        assertTrue(a1.isExpired());
        assertTrue(expired.get());
        assertEquals(0, scheduler.size());
    }

    @Test
    void attackAndDamagedAuraComponents() {
        AuraScheduler scheduler = new AuraScheduler();
        UUID attackerUuid = UUID.randomUUID();
        UUID victimUuid = UUID.randomUUID();
        LivingEntity attackerEntity = createMockEntity(attackerUuid);
        LivingEntity victimEntity = createMockEntity(victimUuid);

        AtomicInteger attackTriggers = new AtomicInteger();
        AtomicInteger damageTriggers = new AtomicInteger();

        AuraDefinition attackAura = AuraDefinition.builder("counter_attack")
                .durationTicks(100)
                .component(new OnAttackAuraComponent((aura, target, dmg) -> attackTriggers.incrementAndGet()))
                .component(new OnDamagedAuraComponent((aura, source, dmg) -> damageTriggers.incrementAndGet()))
                .build();

        scheduler.applyAura(attackAura, AuraAttachment.ofEntity(attackerEntity), attackerUuid, 100);

        scheduler.dispatchAttack(attackerEntity, victimEntity, 25.0);
        assertEquals(1, attackTriggers.get());

        scheduler.dispatchDamaged(attackerEntity, victimEntity, 15.0);
        assertEquals(1, damageTriggers.get());
    }

    @Test
    void mechanicRegistryAuraIntegration() {
        MechanicRegistry registry = new MechanicRegistry();
        AuraScheduler scheduler = new AuraScheduler();
        AuraRegistry auraRegistry = new AuraRegistry();

        registry.setAuraScheduler(scheduler);
        registry.setAuraRegistry(auraRegistry);

        AuraDefinition customAura = AuraDefinition.builder("boss_shield")
                .durationTicks(200)
                .intervalTicks(20)
                .build();
        auraRegistry.register(customAura);

        ActiveLunarMob mob = sampleMob();
        SkillCastContext context = sampleContext(mob);
        MechanicContext mechContext = new MechanicContext(context, List.of(TargetRef.entity(mob.entity())));

        MechanicResult result = registry.execute("aura", mechContext, Map.of(
                "aura", "boss_shield",
                "stack-mode", "REFRESH"
        ));

        assertTrue(result.valid());
        assertEquals(1, scheduler.size());
    }

    private static SkillCastContext sampleContext(ActiveLunarMob mob) {
        SkillDefinition skill = new SkillDefinition("cast_aura", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100);
    }

    private static ActiveLunarMob sampleMob() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = createMockEntity(uuid);
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }

    private static LivingEntity createMockEntity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getVelocity")) return new Vector(0, 0, 0);
                    if (method.getName().equals("setVelocity")) return null;
                    if (method.getName().equals("setGlowing")) return null;
                    if (method.getName().equals("equals")) return args.length > 0 && args[0] != null && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
