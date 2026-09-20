package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets the origin location of the cast context.
 */
public final class OriginTargeter implements LocationTargeter {

    @Override
    public String name() {
        return "origin";
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location origin = context.origin();
        if (origin != null) {
            return List.of(origin.clone());
        }

        if (context.casterEntity() != null) {
            try {
                Location loc = context.casterEntity().getLocation();
                if (loc != null) return List.of(loc.clone());
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }
}
