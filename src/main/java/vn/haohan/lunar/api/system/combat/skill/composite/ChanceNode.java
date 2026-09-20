package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Conditionally executes a child node based on a probabilistic roll (0.0 to 1.0).
 */
public final class ChanceNode implements CompositeSkillNode {

    private final double chance;
    private final CompositeSkillNode child;

    public ChanceNode(double chance, CompositeSkillNode child) {
        if (!Double.isFinite(chance) || chance < 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("Chance must be between 0.0 and 1.0");
        }
        this.chance = chance;
        this.child = Objects.requireNonNull(child, "Child node must not be null");
    }

    public double chance() {
        return chance;
    }

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        if (context.isCancelled()) {
            return CompositeResult.failure("Skill cancelled");
        }
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll <= chance) {
            CompositeResult result = child.execute(context, targets);
            context.setLastStepSuccess(result.successful());
            return result;
        }
        // Roll did not pass, skipped as a valid outcome
        return CompositeResult.success();
    }
}
