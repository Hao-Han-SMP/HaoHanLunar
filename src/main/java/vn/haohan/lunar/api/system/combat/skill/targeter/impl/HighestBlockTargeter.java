package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Resolves the surface/highest block coordinate at the target or origin location.
 * Ideal for meteor strikes, lightning bolts, and ground fissure mechanics.
 * Syntax: {@code @HighestBlock} or {@code @HighestBlock{y_offset=1}}
 */
public final class HighestBlockTargeter implements LocationTargeter {

    private static double parseYOffset(Map<String, Object> parameters) {
        if (parameters == null) return 0.0;
        Object raw = parameters.get("y_offset");
        if (raw == null) raw = parameters.get("yoffset");
        if (raw == null) raw = parameters.get("y");
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            }
            catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location base = context.origin();
        if (base == null && context.triggerEntity() != null) {
            base = context.triggerEntity().getLocation();
        }
        if (base == null && context.casterEntity() != null) {
            base = context.casterEntity().getLocation();
        }
        if (base == null) return List.of();

        World world = base.getWorld();
        if (world == null) return List.of();

        double yOffset = parseYOffset(parameters);
        int highestY = world.getHighestBlockYAt(base.getBlockX(), base.getBlockZ(), HeightMap.MOTION_BLOCKING);
        Location surfaceLoc = new Location(world, base.getX(), highestY + yOffset, base.getZ(), base.getYaw(), base.getPitch());

        return List.of(surfaceLoc);
    }
}
