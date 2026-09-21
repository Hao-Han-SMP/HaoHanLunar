package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargetFilter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Selects targets located in front of the caster's facing vector.
 * Syntax: {@code @InFront{r=12;angle=90}}
 */
public final class InFrontTargeter implements EntityTargeter {

    private static double parseAngle(Map<String, Object> parameters, double def) {
        if (parameters == null) return def;
        Object raw = parameters.get("angle");
        if (raw == null) raw = parameters.get("a");
        if (raw instanceof Number n) return Math.clamp(n.doubleValue(), 1.0, 360.0);
        if (raw instanceof String s) {
            try {
                return Math.clamp(Double.parseDouble(s.trim()), 1.0, 360.0);
            }
            catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location origin = caster.getEyeLocation();
        World world = origin.getWorld();
        if (world == null) return List.of();

        double radius = TargeterFilter.parseRadius(parameters);
        double radiusSq = radius * radius;
        double frontAngle = parseAngle(parameters, 90.0);
        double halfAngle = frontAngle / 2.0;
        double cosThreshold = Math.cos(Math.toRadians(halfAngle));
        Vector facing = origin.getDirection().normalize();

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(origin, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || !TargeterFilter.isTargetable(living)) {
                continue;
            }
            if (living.getUniqueId().equals(caster.getUniqueId())) {
                continue;
            }

            Vector toTarget = living.getLocation().toVector().subtract(origin.toVector());
            double distSq = toTarget.lengthSquared();
            if (distSq <= 0.0001 || distSq > radiusSq) continue;

            double dot = facing.dot(toTarget.multiply(1.0 / Math.sqrt(distSq)));
            if (dot >= cosThreshold) {
                candidates.add(living);
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }
}
