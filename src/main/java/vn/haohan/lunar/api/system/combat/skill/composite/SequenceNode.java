package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;

/**
 * Executes a sequence of skill nodes in order.
 * Stops execution immediately if the cancellation token is tripped or duration expires.
 */
public final class SequenceNode implements CompositeSkillNode {

    private final List<CompositeSkillNode> children;
    private final boolean stopOnFailure;
    private final boolean cloneContextPerChild;

    public SequenceNode(List<CompositeSkillNode> children) {
        this(children, true, false);
    }

    public SequenceNode(List<CompositeSkillNode> children, boolean stopOnFailure, boolean cloneContextPerChild) {
        this.children = List.copyOf(Objects.requireNonNull(children, "Children must not be null"));
        this.stopOnFailure = stopOnFailure;
        this.cloneContextPerChild = cloneContextPerChild;
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled");
        }
        for (CompositeSkillNode child : children) {
            if (context.isCancelled()) {
                context.setLastStepSuccess(false);
                return CompositeResult.failure("Skill cancelled during sequence execution");
            }
            SkillCastContext childContext = cloneContextPerChild ? context.deepClone() : context;
            CompositeResult result = child.execute(childContext, targets);
            context.setLastStepSuccess(result.successful());
            if (!result.successful() && stopOnFailure) {
                return result;
            }
        }
        return CompositeResult.success();
    }
}
