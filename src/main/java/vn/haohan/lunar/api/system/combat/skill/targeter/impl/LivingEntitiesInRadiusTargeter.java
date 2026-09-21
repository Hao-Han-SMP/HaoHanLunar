package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.*;

/**
 * Targets all living entities within a specified radius around origin, excluding the caster.
 * Automatically excludes dead/invalid entities, Spectators, and Creatives.
 * Caps radius at {@link TargeterFilter#MAX_RADIUS} blocks.
 */
public final class LivingEntitiesInRadiusTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "living_entities_in_radius";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location center = context.origin();
        if (center == null || center.getWorld() == null) {
            return List.of();
        }

        double radius = TargeterFilter.parseRadius(parameters);
        int limit = TargeterFilter.parseLimit(parameters);
        double radiusSq = radius * radius;
        World world = center.getWorld();
        LivingEntity caster = context.casterEntity();

        Collection<LivingEntity> candidates;
        try {
            candidates = world.getNearbyLivingEntities(center, radius, radius, radius);
        } catch (Throwable ignored) {
            try {
                candidates = world.getLivingEntities();
            } catch (Throwable ignored2) {
                candidates = List.of();
            }
        }

        List<LivingEntity> matched = new ArrayList<>();
        for (LivingEntity living : candidates) {
            if (caster != null && living.equals(caster)) continue;
            if (!TargeterFilter.isTargetable(living)) continue;
            Location loc = living.getLocation();
            if (!TargeterFilter.isSameWorld(center, loc)) continue;
            if (center.distanceSquared(loc) <= radiusSq) {
                matched.add(living);
            }
        }

        matched.sort(Comparator.comparingDouble(e -> center.distanceSquared(e.getLocation())));

        List<LivingEntity> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, matched.size()); i++) {
            result.add(matched.get(i));
        }
        return List.copyOf(result);
    }
}
