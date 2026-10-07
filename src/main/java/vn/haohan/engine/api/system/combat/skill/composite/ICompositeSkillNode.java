package vn.haohan.engine.api.system.combat.skill.composite;

import vn.haohan.engine.api.system.combat.skill.SkillCastContext;
import vn.haohan.engine.api.system.combat.skill.target.TargetRef;

import java.util.List;

/** Functional contract for a composite skill tree node. */
@FunctionalInterface
public interface ICompositeSkillNode {
    CompositeResult execute(SkillCastContext context, List<TargetRef> targets);
}
