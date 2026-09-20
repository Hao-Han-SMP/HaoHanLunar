package vn.haohan.lunar.api.system.combat.skill.targeter;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;

import java.util.Collection;
import java.util.Map;

/**
 * Targeter returning a collection of LivingEntities.
 */
@FunctionalInterface
public interface EntityTargeter extends SkillTargeter<LivingEntity> {

    @Override
    default String name() {
        return getClass().getSimpleName();
    }

    @Override
    Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters);
}
