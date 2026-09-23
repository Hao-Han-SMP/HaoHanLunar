package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Paper 1.21.1 Native Raycast Location Targeter.
 * Traces a ray along the caster's sight and returns the precise point of impact or maximum range location.
 * Syntax: {@code @RaycastLocation{distance=30;ignoreEntities=true;offset=-0.2}}
 */
public final class RaycastLocationTargeter implements LocationTargeter {

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location eyeLoc = caster.getEyeLocation();
        World world = eyeLoc.getWorld();
        if (world == null) return List.of();

        double distance = parseDouble(parameters, 24.0, "distance", "d", "range", "r", "length");
        double pullBack = parseDouble(parameters, 0.0, "offset", "pullback");
        double yOffset = parseDouble(parameters, 0.0, "yoffset", "y");
        boolean ignoreEntities = parseBoolean(parameters, true, "ignoreentities");
        boolean ignoreBlocks = parseBoolean(parameters, false, "ignoreblocks");

        Vector direction = eyeLoc.getDirection().normalize();
        Location targetLoc;

        if (ignoreBlocks && ignoreEntities) {
            targetLoc = eyeLoc.clone().add(direction.clone().multiply(distance));
        } else {
            RayTraceResult hit;
            if (ignoreEntities) {
                hit = world.rayTraceBlocks(eyeLoc, direction, distance, FluidCollisionMode.NEVER, true);
            } else {
                hit = world.rayTrace(
                        eyeLoc,
                        direction,
                        distance,
                        FluidCollisionMode.NEVER,
                        true,
                        0.2,
                        e -> !e.equals(caster) && e instanceof LivingEntity
                );
            }

            if (hit != null && hit.getHitPosition() != null) {
                Vector hitPos = hit.getHitPosition();
                targetLoc = new Location(world, hitPos.getX(), hitPos.getY(), hitPos.getZ());
                if (pullBack != 0.0) {
                    targetLoc.add(direction.clone().multiply(-pullBack));
                }
            } else {
                targetLoc = eyeLoc.clone().add(direction.clone().multiply(distance));
            }
        }

        if (yOffset != 0.0) {
            targetLoc.add(0, yOffset, 0);
        }

        return List.of(targetLoc);
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
