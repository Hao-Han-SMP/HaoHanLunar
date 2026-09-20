package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets a specific location, supporting absolute and relative (~offset) coordinates.
 */
public final class LocationTargeterImpl implements LocationTargeter {

    @Override
    public String name() {
        return "location";
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || parameters == null) return List.of();

        Location origin = context.origin();
        World world = null;
        Object worldParam = parameters.get("world");
        if (worldParam instanceof String worldName && !worldName.isBlank()) {
            try {
                world = Bukkit.getWorld(worldName.trim());
            } catch (Throwable ignored) {
            }
        }
        if (world == null && origin != null) {
            world = origin.getWorld();
        }
        if (world == null) {
            return List.of();
        }

        double baseX = origin != null ? origin.getX() : 0.0;
        double baseY = origin != null ? origin.getY() : 0.0;
        double baseZ = origin != null ? origin.getZ() : 0.0;

        double x = parseCoordinate(parameters.get("x"), baseX);
        double y = parseCoordinate(parameters.get("y"), baseY);
        double z = parseCoordinate(parameters.get("z"), baseZ);

        float yaw = origin != null ? origin.getYaw() : 0.0f;
        float pitch = origin != null ? origin.getPitch() : 0.0f;
        if (parameters.get("yaw") instanceof Number n) yaw = n.floatValue();
        if (parameters.get("pitch") instanceof Number n) pitch = n.floatValue();

        return List.of(new Location(world, x, y, z, yaw, pitch));
    }

    private static double parseCoordinate(Object raw, double base) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("~")) {
                String offsetStr = trimmed.substring(1).trim();
                if (offsetStr.isEmpty()) return base;
                try {
                    return base + Double.parseDouble(offsetStr);
                } catch (NumberFormatException ignored) {
                    return base;
                }
            }
            try {
                return Double.parseDouble(trimmed);
            } catch (NumberFormatException ignored) {
            }
        }
        return base;
    }
}
