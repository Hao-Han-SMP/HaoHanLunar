package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;

/**
 * Executes child skill nodes in parallel within the current tick pass.
 * Each branch operates on an isolated cloned context sharing the cancellation token.
 */
public final class ParallelNode implements CompositeSkillNode {

    private final List<CompositeSkillNode> children;

    public ParallelNode(List<CompositeSkillNode> children) {
        this.children = List.copyOf(Objects.requireNonNull(children, "Children must not be null"));
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled before parallel execution");
        }
        boolean allSuccess = true;
        for (CompositeSkillNode child : children) {
            if (context.isCancelled()) {
                context.setLastStepSuccess(false);
                return CompositeResult.failure("Skill cancelled during parallel execution");
            }
            SkillCastContext branchContext = context.deepClone();
            CompositeResult result = child.execute(branchContext, targets);
            if (!result.successful()) {
                allSuccess = false;
            }
        }
        context.setLastStepSuccess(allSuccess);
        return allSuccess ? CompositeResult.success() : CompositeResult.failure("One or more parallel branches failed");
    }
}
