package vn.haohan.lunar.api.system.mob.scaling;

import java.util.Map;

/**
 * Configuration for dynamic difficulty scaling based on nearby player count.
 */
public record DynamicScalingDefinition(
        boolean enabled,
        double radius,
        double healthPerPlayer,
        double damagePerPlayer,
        double cooldownReductionPerPlayer,
        int maxPlayers,
        int baselinePlayers
) {
    public static final DynamicScalingDefinition DISABLED =
            new DynamicScalingDefinition(false, 32.0, 0.0, 0.0, 0.0, 20, 1);

    public DynamicScalingDefinition {
        if (radius <= 0) radius = 32.0;
        if (maxPlayers <= 0) maxPlayers = 20;
        if (baselinePlayers < 0) baselinePlayers = 1;
    }

    public static DynamicScalingDefinition fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return DISABLED;
        }

        boolean enabled = Boolean.parseBoolean(String.valueOf(
                map.getOrDefault("Enabled", map.getOrDefault("enabled", false))
        ));
        if (!enabled) {
            return DISABLED;
        }

        double radius = getDouble(map, "Radius", getDouble(map, "radius", 32.0));
        double health = getDouble(map, "HealthPerPlayer", getDouble(map, "healthPerPlayer", 0.25));
        double damage = getDouble(map, "DamagePerPlayer", getDouble(map, "damagePerPlayer", 0.05));
        double cdr = getDouble(map, "CooldownReductionPerPlayer", getDouble(map, "cooldownReductionPerPlayer", 0.02));
        int maxPlayers = getInt(map, "MaxPlayers", getInt(map, "maxPlayers", 20));
        int baseline = getInt(map, "BaselinePlayers", getInt(map, "baselinePlayers", 1));

        return new DynamicScalingDefinition(true, radius, health, damage, cdr, maxPlayers, baseline);
    }

    private static double getDouble(Map<String, Object> map, String key, double def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try {
                return Double.parseDouble(val.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val != null) {
            try {
                return Integer.parseInt(val.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
