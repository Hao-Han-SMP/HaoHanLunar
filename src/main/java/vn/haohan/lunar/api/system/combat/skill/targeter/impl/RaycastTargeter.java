package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
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
 * Paper 1.21.1 Native Raycast Entity Targeter.
 * Fires a ray from the caster's eyes along sight direction, respecting solid block collisions.
 * Syntax: {@code @Raycast{distance=24;raySize=0.5;pierce=false;ignoreBlocks=false}}
 */
public final class RaycastTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location eyeLoc = caster.getEyeLocation();
        World world = eyeLoc.getWorld();
        if (world == null) return List.of();

        double distance = parseDouble(parameters, 16.0, "distance", "d", "range", "r", "length");
        double raySize = parseDouble(parameters, 0.2, "raysize", "size", "width", "radius");
        boolean ignoreBlocks = parseBoolean(parameters, false, "ignoreblocks", "pierceblocks");
        boolean pierce = parseBoolean(parameters, false, "pierce", "piercing");

        Vector direction = eyeLoc.getDirection().normalize();

        // 1. Check max block collision distance if not ignoring blocks
        double maxRayDistance = distance;
        if (!ignoreBlocks) {
            RayTraceResult blockHit = world.rayTraceBlocks(eyeLoc, direction, distance, FluidCollisionMode.NEVER, true);
            if (blockHit != null && blockHit.getHitPosition() != null) {
                maxRayDistance = eyeLoc.toVector().distance(blockHit.getHitPosition());
            }
        }

        if (maxRayDistance <= 0.05) {
            return List.of();
        }

        List<LivingEntity> hits = new ArrayList<>();
        if (!pierce) {
            RayTraceResult entityHit = world.rayTraceEntities(
                    eyeLoc,
                    direction,
                    maxRayDistance,
                    raySize,
                    e -> e instanceof LivingEntity living && !e.equals(caster) && TargeterFilter.isTargetable(living)
            );
            if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity living) {
                hits.add(living);
            }
        } else {
            // Pierce: collect all living entities along the line within maxRayDistance
            Vector startVec = eyeLoc.toVector();
            Location midPoint = eyeLoc.clone().add(direction.clone().multiply(maxRayDistance * 0.5));
            double searchRadius = (maxRayDistance * 0.5) + raySize + 1.0;

            for (Entity entity : world.getNearbyEntities(midPoint, searchRadius, searchRadius, searchRadius)) {
                if (!(entity instanceof LivingEntity living) || entity.equals(caster) || !TargeterFilter.isTargetable(living)) {
                    continue;
                }
                Vector toTarget = living.getLocation().toVector().subtract(startVec);
                double projection = toTarget.dot(direction);
                if (projection >= 0 && projection <= maxRayDistance) {
                    Vector linePoint = startVec.clone().add(direction.clone().multiply(projection));
                    double distToLine = linePoint.distance(living.getLocation().toVector());
                    if (distToLine <= raySize + 0.6) {
                        hits.add(living);
                    }
                }
            }
        }

        return TargetFilter.filterAndSort(hits, context, parameters);
    }

    private static double parseDouble(Map<String, Object> params, double def, String... keys) {
        if (params == null) return def;
        for (String k : keys) {
            Object val = params.get(k);
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s) {
                try { return Double.parseDouble(s.trim()); }
                catch (NumberFormatException ignored) {}
            }
        }
        return def;
    }

    private static boolean parseBoolean(Map<String, Object> params, boolean def, String... keys) {
        if (params == null) return def;
        for (String k : keys) {
            Object val = params.get(k);
            if (val instanceof Boolean b) return b;
            if (val != null) return Boolean.parseBoolean(String.valueOf(val).trim());
        }
        return def;
    }
}
