package vn.haohan.lunar.core.mob.phase;

import vn.haohan.lunar.api.mob.phase.*;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobPhaseMachineTest {

    @Test
    void transitionsByHealthAndDispatchesExitEnterSkills() {
        List<String> skills = new ArrayList<>();
        MobPhaseMachine machine = new MobPhaseMachine(List.of(
                new MobPhase("enraged", 0.5, "enter_enraged", null, true),
                new MobPhase("normal", 1.0, "enter_normal", "exit_normal", false)),
                new CooldownRegistry(), phaseSkill -> skills.add(phaseSkill.skillId()));
        ActiveLunarMob mob = mob();

        assertTrue(machine.update(mob, 100, 100, 1).changed());
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());
        assertEquals("normal", mob.stance());

        assertTrue(machine.update(mob, 40, 100, 2).changed());
        assertEquals("enraged", machine.state(mob.entityId()).orElseThrow().phaseId());
        assertEquals("enraged", mob.stance());
        assertEquals(List.of("enter_normal", "exit_normal", "enter_enraged"), skills);
        assertFalse(machine.update(mob, 40, 100, 3).changed());
    }

    @Test
    void onceOnlyPhaseCannotBeReentered() {
        List<String> skills = new ArrayList<>();
        MobPhase oncePhase = MobPhase.builder("shield_phase")
                .priority(10)
                .minimumHealthRatio(0.7)
                .onceOnly(true)
                .onEnterSkill("enter_shield")
                .onExitSkill("exit_shield")
                .build();

        MobPhase normalPhase = MobPhase.builder("normal")
                .priority(0)
                .minimumHealthRatio(1.0)
                .build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(oncePhase, normalPhase),
                new CooldownRegistry(), phaseSkill -> skills.add(phaseSkill.skillId()));
        ActiveLunarMob mob = mob();

        // 1. Initial 100% health -> normal phase
        machine.update(mob, 100, 100, 1);
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());

        // 2. Health drops to 60% -> triggers shield_phase
        assertTrue(machine.update(mob, 60, 100, 2).changed());
        assertEquals("shield_phase", machine.state(mob.entityId()).orElseThrow().phaseId());

        // 3. Health drops to 30% -> transition to normal or lower phase
        assertTrue(machine.update(PhaseContext.ofHealth(mob, 30, 100, 3)).changed());
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());

        // 4. Boss healed back to 60% -> shield_phase is onceOnly so it CANNOT re-enter
        assertFalse(machine.update(PhaseContext.ofHealth(mob, 60, 100, 4)).changed());
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());
    }

    @Test
    void compositeConditionsEvaluation() {
        MobPhase berserkPhase = MobPhase.builder("berserk")
                .priority(20)
                .condition(PhaseCondition.aliveTimeGreaterThanOrEqual(100L))
                .condition(PhaseCondition.targetCountGreaterThanOrEqual(3))
                .onEnterSkill("enter_berserk")
                .build();

        MobPhase normalPhase = MobPhase.builder("normal")
                .priority(0)
                .minimumHealthRatio(1.0)
                .build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(berserkPhase, normalPhase),
                new CooldownRegistry(), ignored -> {});
        ActiveLunarMob mob = mob();

        // Condition not satisfied: aliveTicks < 100
        PhaseContext ctx1 = new PhaseContext(mob, 100, 100, 10, 50, 4, Map.of(), null);
        machine.update(ctx1);
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());

        // Condition not satisfied: targetCount < 3
        PhaseContext ctx2 = new PhaseContext(mob, 100, 100, 20, 120, 2, Map.of(), null);
        machine.update(ctx2);
        assertEquals("normal", machine.state(mob.entityId()).orElseThrow().phaseId());

        // All conditions satisfied: aliveTicks >= 100 AND targetCount >= 3
        PhaseContext ctx3 = new PhaseContext(mob, 100, 100, 30, 120, 3, Map.of(), null);
        assertTrue(machine.update(ctx3).changed());
        assertEquals("berserk", machine.state(mob.entityId()).orElseThrow().phaseId());
    }

    @Test
    void transitionCooldownEnforced() {
        MobPhase p1 = MobPhase.builder("p1")
                .priority(5)
                .transitionCooldownTicks(20L)
                .minimumHealthRatio(0.8)
                .build();

        MobPhase p2 = MobPhase.builder("p2")
                .priority(10)
                .minimumHealthRatio(0.4)
                .build();

        MobPhase normal = MobPhase.builder("normal")
                .priority(0)
                .minimumHealthRatio(1.0)
                .build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(p1, p2, normal),
                new CooldownRegistry(), ignored -> {});
        ActiveLunarMob mob = mob();

        // Start normal at tick 0
        machine.update(mob, 100, 100, 0);

        // Enter p1 at tick 10
        assertTrue(machine.update(mob, 70, 100, 10).changed());
        assertEquals("p1", machine.state(mob.entityId()).orElseThrow().phaseId());

        // Health drops drastically at tick 15, eligible for p2, but p1 has 20-tick cooldown (need tick >= 30)
        assertFalse(machine.update(mob, 30, 100, 15).changed());
        assertEquals("p1", machine.state(mob.entityId()).orElseThrow().phaseId());

        // At tick 31, cooldown elapsed -> can transition to p2
        assertTrue(machine.update(mob, 30, 100, 31).changed());
        assertEquals("p2", machine.state(mob.entityId()).orElseThrow().phaseId());
    }

    @Test
    void removesStateOnDeathCleanupAndRejectsInvalidHealth() {
        MobPhaseMachine machine = new MobPhaseMachine(List.of(new MobPhase("normal", 0, null, null, false)),
                new CooldownRegistry(), ignored -> { });
        ActiveLunarMob mob = mob();
        assertFalse(machine.update(mob, 1, 0, 1).changed());
        assertEquals(1, machine.update(mob, 10, 10, 2).current() == null ? 0 : 1);
        machine.remove(mob.entityId());
        assertTrue(machine.state(mob.entityId()).isEmpty());
        machine.update(mob, 10, 10, 3);
        assertEquals(1, machine.cleanupAll());
    }

    private static ActiveLunarMob mob() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
