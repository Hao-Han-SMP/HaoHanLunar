package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Repeats a child node until a predicate condition is satisfied, or the safety iteration limit is reached.
 */
public final class RepeatUntilNode implements CompositeSkillNode {

    public static final int MAX_SAFE_ITERATIONS = 50;

    private final CompositeSkillNode child;
    private final Predicate<SkillCastContext> stopCondition;
    private final int maxIterations;

    public RepeatUntilNode(CompositeSkillNode child, Predicate<SkillCastContext> stopCondition, int maxIterations) {
        this.child = Objects.requireNonNull(child, "Child node must not be null");
        this.stopCondition = Objects.requireNonNull(stopCondition, "Stop condition must not be null");
        this.maxIterations = Math.max(1, Math.min(maxIterations, MAX_SAFE_ITERATIONS));
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        int iterations = 0;
        while (iterations < maxIterations) {
            if (context.isCancelled()) {
                return CompositeResult.failure("Skill cancelled during repeat loop");
            }
            if (stopCondition.test(context)) {
                return CompositeResult.success();
            }
            CompositeResult result = child.execute(context, targets);
            context.setLastStepSuccess(result.successful());
            if (!result.successful()) {
                return result;
            }
            iterations++;
        }
        return CompositeResult.success();
    }
}
