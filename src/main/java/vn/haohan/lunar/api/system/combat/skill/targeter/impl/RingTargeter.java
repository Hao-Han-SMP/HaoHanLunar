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
 * Selects targets in a donut ring around the caster or origin location.
 * Syntax: {@code @Ring{radius=10;width=3}}
 */
public final class RingTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        Location center = context.origin() != null
                ? context.origin()
                : context.caster().entity().getLocation();
        World world = center.getWorld();
        if (world == null) return List.of();

        double outerRadius = TargeterFilter.parseRadius(parameters);
        double width = parseWidth(parameters, 3.0);
        double innerRadius = Math.max(0.0, outerRadius - width);

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, outerRadius, outerRadius, outerRadius)) {
            if (!(entity instanceof LivingEntity living) || !TargeterFilter.isTargetable(living)) {
                continue;
            }

            double dist = center.distance(living.getLocation());
            if (dist >= innerRadius && dist <= outerRadius) {
                candidates.add(living);
            }
        }

        return TargetFilter.filterAndSort(candidates, context, parameters);
    }

    private static double parseWidth(Map<String, Object> params, double def) {
        if (params == null) return def;
        Object raw = params.get("width");
        if (raw == null) raw = params.get("w");
        if (raw instanceof Number n) return Math.max(0.1, n.doubleValue());
        if (raw instanceof String s) {
            try { return Math.max(0.1, Double.parseDouble(s.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
