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
 * Selects targets in a straight rectangular ray corridor projecting forward from the caster.
 * Syntax: {@code @Line{length=20;width=2}}
 */
public final class LineTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location origin = caster.getEyeLocation();
        World world = origin.getWorld();
        if (world == null) return List.of();

        double length = parseLength(parameters, 16.0);
        double width = parseWidth(parameters, 1.5);
        Vector dir = origin.getDirection().normalize();
        Vector originVec = origin.toVector();

        Location midPoint = origin.clone().add(dir.clone().multiply(length * 0.5));
        double queryRadius = (length * 0.5) + width + 2.0;

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(midPoint, queryRadius, queryRadius, queryRadius)) {
            if (!(entity instanceof LivingEntity living) || !TargeterFilter.isTargetable(living)) {
                continue;
            }

            Vector toEntity = living.getLocation().toVector().subtract(originVec);
            double projection = toEntity.dot(dir);

            if (projection >= 0 && projection <= length) {
                Vector pointOnLine = originVec.clone().add(dir.clone().multiply(projection));
                double distanceToLine = pointOnLine.distance(living.getLocation().toVector());

                if (distanceToLine <= width) {
                    candidates.add(living);
                }
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }

    private static double parseLength(Map<String, Object> params, double def) {
        if (params == null) return def;
        Object raw = params.get("length");
        if (raw == null) raw = params.get("l");
        if (raw instanceof Number n) return Math.max(1.0, Math.min(n.doubleValue(), 64.0));
        if (raw instanceof String s) {
            try { return Math.max(1.0, Math.min(Double.parseDouble(s.trim()), 64.0)); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }

    private static double parseWidth(Map<String, Object> params, double def) {
        if (params == null) return def;
        Object raw = params.get("width");
        if (raw == null) raw = params.get("w");
        if (raw instanceof Number n) return Math.max(0.2, Math.min(n.doubleValue(), 16.0));
        if (raw instanceof String s) {
            try { return Math.max(0.2, Math.min(Double.parseDouble(s.trim()), 16.0)); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
