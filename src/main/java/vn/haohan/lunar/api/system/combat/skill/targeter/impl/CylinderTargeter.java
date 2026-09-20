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
 * Selects targets in a vertical cylinder (e.g. lightning strike column, blizzard vortex).
 * Syntax: {@code @Cylinder{radius=10;height=6}}
 */
public final class CylinderTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        Location center = context.origin() != null
                ? context.origin()
                : context.caster().entity().getLocation();
        World world = center.getWorld();
        if (world == null) return List.of();

        double radius = TargeterFilter.parseRadius(parameters);
        double height = parseHeight(parameters, 8.0);
        double radiusSq = radius * radius;

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, height, radius)) {
            if (!(entity instanceof LivingEntity living) || !TargeterFilter.isTargetable(living)) {
                continue;
            }

            Location targetLoc = living.getLocation();
            double dx = targetLoc.getX() - center.getX();
            double dz = targetLoc.getZ() - center.getZ();
            double dy = Math.abs(targetLoc.getY() - center.getY());

            if ((dx * dx + dz * dz) <= radiusSq && dy <= height) {
                candidates.add(living);
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }

    private static double parseHeight(Map<String, Object> params, double def) {
        if (params == null) return def;
        Object raw = params.get("height");
        if (raw == null) raw = params.get("h");
        if (raw instanceof Number n) return Math.max(0.5, Math.min(n.doubleValue(), 128.0));
        if (raw instanceof String s) {
            try { return Math.max(0.5, Math.min(Double.parseDouble(s.trim()), 128.0)); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
