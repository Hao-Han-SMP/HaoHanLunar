package vn.haohan.engine.api.system.combat.skill.mechanic;

import vn.haohan.engine.api.system.combat.skill.SkillCastContext;
import vn.haohan.engine.api.system.combat.skill.target.TargetRef;

import java.util.List;
import java.util.Objects;

public record MechanicContext(SkillCastContext cast, List<TargetRef> targets) {
    public MechanicContext {
        cast = Objects.requireNonNull(cast, "Cast context must not be null");
        targets = List.copyOf(Objects.requireNonNull(targets, "Targets must not be null"));
    }
}
