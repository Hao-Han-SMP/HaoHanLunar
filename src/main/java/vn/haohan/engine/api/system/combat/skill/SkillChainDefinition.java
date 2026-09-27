package vn.haohan.engine.api.system.combat.skill;

import vn.haohan.engine.api.system.combat.skill.condition.ConditionRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Parsed execution chain for one skill. */
public record SkillChainDefinition(SkillDefinition definition,
                                   List<ConditionRegistry.ConditionCall> conditions,
                                   String targeter,
                                   List<MechanicStep> mechanics) {
    public SkillChainDefinition {
        definition = Objects.requireNonNull(definition, "Skill definition must not be null");
        conditions = List.copyOf(Objects.requireNonNull(conditions, "Conditions must not be null"));
        targeter = Objects.requireNonNull(targeter, "ITargeter must not be null");
        mechanics = List.copyOf(Objects.requireNonNull(mechanics, "Mechanics must not be null"));
    }

    public record MechanicStep(String id, Map<String, Object> parameters,
                               long delayTicks, int repeat, String castSkill) {
        public MechanicStep {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("IMechanic ID must not be blank");
            parameters = Map.copyOf(Objects.requireNonNull(parameters, "IMechanic parameters must not be null"));
            if (delayTicks < 0) throw new IllegalArgumentException("IMechanic delay must not be negative");
            if (repeat < 1) throw new IllegalArgumentException("IMechanic repeat must be positive");
            castSkill = castSkill == null || castSkill.isBlank() ? null : castSkill.trim().toLowerCase();
        }
    }
}
