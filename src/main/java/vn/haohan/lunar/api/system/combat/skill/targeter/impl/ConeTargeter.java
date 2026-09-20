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
 * Sweeps a conical 3D vision cone in front of the caster.
 * Syntax: {@code @Cone{angle=90;radius=12}}
 */
public final class ConeTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location origin = caster.getEyeLocation();
        World world = origin.getWorld();
        if (world == null) return List.of();

        double radius = TargeterFilter.parseRadius(parameters);
        double angle = parseAngle(parameters, 90.0);
        double halfAngle = angle / 2.0;
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
            double dist = toTarget.length();
            if (dist <= 0.0001 || dist > radius) continue;

            double degrees = Math.toDegrees(facing.angle(toTarget));
            if (degrees <= halfAngle) {
                candidates.add(living);
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }

    private static double parseAngle(Map<String, Object> params, double def) {
        if (params == null) return def;
        Object raw = params.get("angle");
        if (raw == null) raw = params.get("a");
        if (raw instanceof Number n) return Math.max(1.0, Math.min(n.doubleValue(), 360.0));
        if (raw instanceof String s) {
            try { return Math.max(1.0, Math.min(Double.parseDouble(s.trim()), 360.0)); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
