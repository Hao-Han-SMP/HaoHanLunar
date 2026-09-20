package vn.haohan.lunar.api.system.combat.skill.targeter;

import org.bukkit.Location;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;

import java.util.Collection;
import java.util.Map;

/**
 * Targeter returning a collection of Locations.
 */
@FunctionalInterface
public interface LocationTargeter extends SkillTargeter<Location> {

    @Override
    default String name() {
        return getClass().getSimpleName();
    }

    @Override
    Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters);
}
