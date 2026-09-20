package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes a single random branch from a list of candidate child nodes.
 */
public final class RandomNode implements CompositeSkillNode {

    private final List<CompositeSkillNode> children;

    public RandomNode(List<CompositeSkillNode> children) {
        this.children = List.copyOf(Objects.requireNonNull(children, "Children must not be null"));
        if (this.children.isEmpty()) {
            throw new IllegalArgumentException("RandomNode must have at least one child node");
        }
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled");
        }
        int index = ThreadLocalRandom.current().nextInt(children.size());
        CompositeSkillNode chosen = children.get(index);
        CompositeResult result = chosen.execute(context.deepClone(), targets);
        context.setLastStepSuccess(result.successful());
        return result;
    }
}
