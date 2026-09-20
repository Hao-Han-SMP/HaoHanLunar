package vn.haohan.lunar.api.system.combat.skill.targeter;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Shared filtering, bounds enforcement and safety guards for targeters.
 */
public final class TargeterFilter {

    /** Maximum allowed radius for any radius-based targeter to prevent lag. */
    public static final double MAX_RADIUS = 64.0;
    public static final double DEFAULT_RADIUS = 16.0;

    private TargeterFilter() {
    }

    /**
     * Extracts and clamps the radius from parameters.
     * Guaranteed to return a value within [0.0, MAX_RADIUS].
     */
    public static double parseRadius(Map<String, Object> parameters) {
        if (parameters == null) return DEFAULT_RADIUS;
        Object raw = parameters.get("radius");
        if (raw == null) raw = parameters.get("r");
        if (raw == null) raw = parameters.get("d");
        if (raw instanceof Number number) {
            return clampRadius(number.doubleValue());
        }
        if (raw instanceof String text) {
            try {
                return clampRadius(Double.parseDouble(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_RADIUS;
    }

    /**
     * Extracts and clamps the limit count from parameters.
     * Guaranteed to return at least 1.
     */
    public static int parseLimit(Map<String, Object> parameters) {
        if (parameters == null) return Integer.MAX_VALUE;
        Object raw = parameters.get("limit");
        if (raw == null) raw = parameters.get("l");
        if (raw == null) raw = parameters.get("count");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return Integer.MAX_VALUE;
    }

    public static double clampRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0) return 0.0;
        return Math.min(MAX_RADIUS, radius);
    }

    /**
     * Validates whether a living entity is eligible to be targeted.
     * Automatically rejects dead/invalid entities, offline players, and
     * Spectator/Creative mode players.
     */
    public static boolean isTargetable(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (!living.isValid() || living.isDead()) {
            return false;
        }
        if (living instanceof Player player) {
            if (!player.isOnline()) return false;
            GameMode mode = player.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if two locations share the exact same non-null world.
     */
    public static boolean isSameWorld(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return false;
        World w1 = loc1.getWorld();
        World w2 = loc2.getWorld();
        return w1 != null && w1.equals(w2);
    }
}
