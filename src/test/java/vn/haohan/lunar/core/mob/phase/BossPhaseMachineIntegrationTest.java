package vn.haohan.lunar.core.mob.phase;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.system.mob.phase.MobPhase;
import vn.haohan.lunar.api.system.mob.phase.MobPhaseMachine;
import vn.haohan.lunar.api.system.mob.phase.PhaseContext;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossPhaseMachineIntegrationTest {

    @Test
    void parsePhasesFromYamlStructure() {
        Map<String, Object> yamlConfig = new LinkedHashMap<>();

        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("health", "100%");
        p1.put("priority", 1);

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("health", "50%");
        p2.put("priority", 2);
        p2.put("skills", List.of("RoarSkill", "ShieldAura"));
        p2.put("exitSkills", List.of("CleanseAura"));
        p2.put("onceOnly", true);
        p2.put("cooldown", 100L);

        Map<String, Object> p3 = new LinkedHashMap<>();
        p3.put("health", 0.2);
        p3.put("priority", 3);
        p3.put("skills", List.of("FinalEnrage"));
        p3.put("conditions", List.of("aliveTime > 600", "targetCount >= 2", "var.enraged == true", "~onSignal:FORCE_P3"));

        yamlConfig.put("phase1", p1);
        yamlConfig.put("phase2", p2);
        yamlConfig.put("phase3", p3);

        List<MobPhase> phases = MobPhase.parsePhases(yamlConfig);
        assertEquals(3, phases.size());

        MobPhase parsedP2 = phases.stream().filter(p -> p.id().equals("phase2")).findFirst().orElseThrow();
        assertEquals(0.5, parsedP2.minimumHealthRatio(), 0.001);
        assertEquals(List.of("roarskill", "shieldaura"), parsedP2.onEnterSkills());
        assertEquals(List.of("cleanseaura"), parsedP2.onExitSkills());
        assertTrue(parsedP2.onceOnly());
        assertEquals(100L, parsedP2.transitionCooldownTicks());

        MobPhase parsedP3 = phases.stream().filter(p -> p.id().equals("phase3")).findFirst().orElseThrow();
        assertEquals(5, parsedP3.conditions().size());
    }

    @Test
    void transitionsPhaseOneToTwoToThreeWithEnterAndExitSkills() {
        List<MobPhaseMachine.PhaseSkill> dispatchedSkills = new ArrayList<>();
        CooldownRegistry cooldowns = new CooldownRegistry();

        MobPhase p1 = MobPhase.builder("p1")
                .priority(1)
                .minimumHealthRatio(1.0)
                .build();

        MobPhase p2 = MobPhase.builder("p2")
                .priority(2)
                .minimumHealthRatio(0.6)
                .onEnterSkill("enter_p2")
                .onExitSkill("exit_p2")
                .onceOnly(true)
                .build();

        MobPhase p3 = MobPhase.builder("p3")
                .priority(3)
                .minimumHealthRatio(0.2)
                .onEnterSkill("enter_p3")
                .build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(p1, p2, p3), cooldowns, dispatchedSkills::add);
        ActiveMob mob = createTestMob(1000.0);

        // Tick 1: Boss at 100% health -> Initialized in Phase 1
        var r1 = machine.update(PhaseContext.ofHealth(mob, 1000.0, 1000.0, 1L));
        assertTrue(r1.changed());
        assertEquals("p1", r1.current().phaseId());
        assertEquals("p1", mob.stance());

        // Tick 10: Boss damaged to 500/1000 (50% health <= 60%) -> Enters Phase 2
        var r2 = machine.update(PhaseContext.ofHealth(mob, 500.0, 1000.0, 10L));
        assertTrue(r2.changed());
        assertEquals("p2", r2.current().phaseId());
        assertEquals("p2", mob.stance());
        assertEquals(1, dispatchedSkills.size());
        assertEquals("enter_p2", dispatchedSkills.getFirst().skillId());
        assertTrue(dispatchedSkills.getFirst().entering());

        // Tick 20: Boss damaged to 150/1000 (15% health <= 20%) -> Exits Phase 2, Enters Phase 3
        var r3 = machine.update(PhaseContext.ofHealth(mob, 150.0, 1000.0, 20L));
        assertTrue(r3.changed());
        assertEquals("p3", r3.current().phaseId());
        assertEquals("p3", mob.stance());

        // Verify skills: enter_p2, exit_p2, enter_p3
        assertEquals(3, dispatchedSkills.size());
        assertEquals("exit_p2", dispatchedSkills.get(1).skillId());
        assertFalse(dispatchedSkills.get(1).entering());
        assertEquals("enter_p3", dispatchedSkills.get(2).skillId());
        assertTrue(dispatchedSkills.get(2).entering());

        // OnceOnly check: Boss heals back to 500 (50%) -> skips Phase 2 because p2 was onceOnly, transitions to p1
        var r4 = machine.update(PhaseContext.ofHealth(mob, 500.0, 1000.0, 30L));
        assertTrue(r4.changed());
        assertEquals("p1", r4.current().phaseId());
        assertFalse(r4.current().phaseId().equals("p2"), "Once-only phase p2 must not be re-entered");

        // Staying at 500 or higher stays in p1, never re-entering p2
        var r5 = machine.update(PhaseContext.ofHealth(mob, 500.0, 1000.0, 40L));
        assertFalse(r5.changed(), "Should stay in p1 without re-triggering p2");
        assertEquals("p1", r5.current().phaseId());
    }

    @Test
    void transitionCooldownPreventsRapidPhaseFlipping() {
        CooldownRegistry cooldowns = new CooldownRegistry();
        MobPhase p1 = MobPhase.builder("p1").priority(1).minimumHealthRatio(1.0).build();
        MobPhase p2 = MobPhase.builder("p2").priority(2).minimumHealthRatio(0.5)
                .transitionCooldownTicks(50L).build();
        MobPhase p3 = MobPhase.builder("p3").priority(3).minimumHealthRatio(0.2).build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(p1, p2, p3), cooldowns, s -> {});
        ActiveMob mob = createTestMob(100.0);

        // Enters p1 at tick 0
        machine.update(PhaseContext.ofHealth(mob, 100.0, 100.0, 0L));

        // Drops to 40% at tick 10 -> enters p2
        var r2 = machine.update(PhaseContext.ofHealth(mob, 40.0, 100.0, 10L));
        assertTrue(r2.changed());
        assertEquals("p2", r2.current().phaseId());

        // Immediately drops to 10% at tick 20 (only 10 ticks in p2, cooldown is 50 ticks)
        var rBlocked = machine.update(PhaseContext.ofHealth(mob, 10.0, 100.0, 20L));
        assertFalse(rBlocked.changed(), "Phase transition must be throttled by transitionCooldownTicks");
        assertEquals("p2", machine.state(mob.entityId()).orElseThrow().phaseId());

        // At tick 70 (60 ticks elapsed > 50 ticks cooldown), transition proceeds to p3
        var rAllowed = machine.update(PhaseContext.ofHealth(mob, 10.0, 100.0, 70L));
        assertTrue(rAllowed.changed());
        assertEquals("p3", rAllowed.current().phaseId());
    }

    @Test
    void reloadMobDefinitionPreservesPhaseMachineSafely() {
        ActiveMob mob = createTestMob(500.0);
        MobPhase p1 = MobPhase.builder("phase1").priority(1).minimumHealthRatio(1.0).build();
        MobPhase p2 = MobPhase.builder("phase2").priority(2).minimumHealthRatio(0.5).build();

        MobPhaseMachine machine = new MobPhaseMachine(List.of(p1, p2), new CooldownRegistry(), s -> {});
        mob.setPhaseMachine(machine);

        machine.update(PhaseContext.ofHealth(mob, 200.0, 500.0, 1L));
        assertEquals("phase2", mob.stance());
        assertNotNull(mob.phaseMachine());

        // Simulating config reload with updated display name
        MobDefinition updatedDef = new MobDefinition(
                mob.definitionId(),
                EntityType.ZOMBIE,
                "Reloaded Boss Display",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                null,
                List.of(),
                null,
                List.of(),
                List.of()
        );

        mob.updateDefinition(updatedDef);
        assertEquals("Reloaded Boss Display", mob.definition().displayName());
        assertEquals("phase2", mob.stance());
        assertEquals("phase2", mob.phaseMachine().state(mob.entityId()).orElseThrow().phaseId());
    }

    // --- Helpers ---

    private static ActiveMob createTestMob(double maxHealth) {
        UUID id = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (p, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getMaxHealth" -> maxHealth;
                    case "getHealth" -> maxHealth;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getTicksLived" -> 100;
                    case "equals" -> p == args[0];
                    case "hashCode" -> id.hashCode();
                    default -> null;
                }
        );

        MobDefinition def = new MobDefinition(
                new MobDefinitionId("boss_mob"),
                EntityType.ZOMBIE,
                "Boss Mob",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                null,
                List.of(),
                null,
                List.of(),
                List.of()
        );

        LunarMobIdentity identity = new LunarMobIdentity("boss_mob", "1.0", java.util.Optional.empty());
        return new ActiveMob(entity, def, identity);
    }
}
