package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;

/**
 * Executes a child node conditionally if the preceding skill action succeeded.
 */
public final class OnSuccessNode implements CompositeSkillNode {

    private final CompositeSkillNode child;

    public OnSuccessNode(CompositeSkillNode child) {
        this.child = Objects.requireNonNull(child, "Child node must not be null");
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled");
        }
        if (context.lastStepSuccess()) {
            return child.execute(context, targets);
        }
        return CompositeResult.success();
    }
}
