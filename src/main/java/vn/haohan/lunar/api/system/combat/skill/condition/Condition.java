package vn.haohan.lunar.api.system.combat.skill.condition;

import java.util.Map;

@FunctionalInterface
public interface Condition {
    boolean evaluate(ConditionContext context, Map<String, Object> parameters);
}
