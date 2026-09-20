package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets the caster entity.
 */
public final class SelfTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "self";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.casterEntity() == null) {
            return List.of();
        }
        LivingEntity caster = context.casterEntity();
        if (TargeterFilter.isTargetable(caster)) {
            return List.of(caster);
        }
        return List.of();
    }
}
