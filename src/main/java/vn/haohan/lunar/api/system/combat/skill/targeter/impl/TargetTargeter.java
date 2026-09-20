package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets the current combat target of the mob.
 */
public final class TargetTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "target";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        LivingEntity target = null;
        LivingEntity caster = context.casterEntity();
        if (caster instanceof Mob mob) {
            try {
                target = mob.getTarget();
            } catch (Exception ignored) {
            }
        }

        if (target == null && context.get("target") instanceof LivingEntity ctxTarget) {
            target = ctxTarget;
        }

        if (target != null && TargeterFilter.isTargetable(target)) {
            if (caster != null && caster.getWorld() != null && target.getWorld() != null
                    && !caster.getWorld().equals(target.getWorld())) {
                return List.of();
            }
            return List.of(target);
        }
        return List.of();
    }
}
