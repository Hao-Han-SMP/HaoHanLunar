package vn.haohan.lunar.api.system.combat.skill.composite;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.List;

/** Functional contract for a composite skill tree node. */
@FunctionalInterface
public interface CompositeSkillNode {
    CompositeResult execute(SkillCastContext context, List<TargetRef> targets);
}
