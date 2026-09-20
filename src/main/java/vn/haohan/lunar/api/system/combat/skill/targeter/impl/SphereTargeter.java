package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargetFilter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Selects targets within a spherical 3D volume around caster or origin location.
 * Syntax: {@code @Sphere{radius=10}}
 */
public final class SphereTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        Location center = context.origin() != null
                ? context.origin()
                : context.caster().entity().getLocation();
        World world = center.getWorld();
        if (world == null) return List.of();

        double radius = TargeterFilter.parseRadius(parameters);
        double radiusSq = radius * radius;

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || !TargeterFilter.isTargetable(living)) {
                continue;
            }

            if (center.distanceSquared(living.getLocation()) <= radiusSq) {
                candidates.add(living);
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }
}
