package vn.haohan.engine.api.system.combat.skill.condition;

import java.util.Map;

@FunctionalInterface
public interface ICondition {
    boolean evaluate(ConditionContext context, Map<String, Object> parameters);
}
