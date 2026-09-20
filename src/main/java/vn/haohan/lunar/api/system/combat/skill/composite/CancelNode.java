package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;

/**
 * Explicitly trips the cancellation token of the current skill cast context.
 */
public final class CancelNode implements CompositeSkillNode {

    @Override
    public CompositeResult execute(SkillCastContext context, List<TargetRef> targets) {
        context.cancel();
        context.setLastStepSuccess(false);
        return CompositeResult.success();
    }
}
