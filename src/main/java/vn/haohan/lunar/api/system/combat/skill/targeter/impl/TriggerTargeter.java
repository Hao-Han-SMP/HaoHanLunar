package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets the entity that triggered the skill event.
 */
public final class TriggerTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "trigger";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Entity trigger = context.triggerEntity();
        if (trigger == null && context.get("trigger") instanceof Entity ctxTrigger) {
            trigger = ctxTrigger;
        }

        if (trigger instanceof LivingEntity living && TargeterFilter.isTargetable(living)) {
            return List.of(living);
        }
        return List.of();
    }
}
