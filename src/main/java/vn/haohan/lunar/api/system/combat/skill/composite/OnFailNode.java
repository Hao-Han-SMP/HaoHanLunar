package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;

/**
 * Executes a fallback child node conditionally if the preceding skill action failed.
 */
public final class OnFailNode implements CompositeSkillNode {

    private final CompositeSkillNode fallbackChild;

    public OnFailNode(CompositeSkillNode fallbackChild) {
        this.fallbackChild = Objects.requireNonNull(fallbackChild, "Fallback child node must not be null");
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled");
        }
        if (!context.lastStepSuccess()) {
            return fallbackChild.execute(context, targets);
        }
        return CompositeResult.success();
    }
}
