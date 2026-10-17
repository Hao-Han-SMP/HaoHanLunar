package vn.haohan.lunar.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Utility for astronomical calculations in Minecraft Overworld regarding celestial bodies (specifically the Moon).
 */
public final class MoonUtil {

    private MoonUtil() {
    }

    /**
     * Checks if the current world tick corresponds to night time where the moon is in the sky.
     * In Minecraft, night begins around tick 12542 (dusk) and ends around 23460 (dawn).
     *
     * @param worldTime the time in ticks (0 - 24000)
     * @return true if it's night time
     */
    public static boolean isNightTime(long worldTime) {
        long timeOfDay = ((worldTime % 24000L) + 24000L) % 24000L;
        return timeOfDay >= 12500L && timeOfDay <= 23500L;
    }

    /**
     * Calculates the normalized direction vector pointing towards the Moon in Minecraft Overworld.
     * <p>
     * In Minecraft vanilla celestial coordinate system:
     * - The Sun and Moon rotate across the East-West axis (X-axis).
     * - Sun rises in East (+X), peaks at noon (Y = 1), sets in West (-X).
     * - The Moon is opposite to the Sun by 180 degrees (offset by 0.5 cycle or 12000 ticks).
     * - At midnight (tick 18000), celestial angle is 0.5 (or offset by 180 deg), placing the Moon at the zenith: (X=0, Y=1, Z=0).
     *
     * @param worldTime the world time in ticks
     * @return normalized Vector pointing towards the Moon from the ground
     */
    public static Vector getMoonDirection(long worldTime) {
        long timeOfDay = ((worldTime % 24000L) + 24000L) % 24000L;
        // fraction of day: 0.0 at tick 0 (sunrise), 0.25 at tick 6000 (noon), 0.5 at tick 12000 (sunset), 0.75 at tick 18000 (midnight)
        double fraction = (double) timeOfDay / 24000.0;
        
        // Celestial angle in radians. In Minecraft:
        // angle = (fraction - 0.25) * 2 * PI
        // At noon (fraction = 0.25): angle = 0 -> Sun at zenith (Y=1, X=0).
        // Moon is opposite to the Sun (+ PI radians).
        // Therefore at midnight (fraction = 0.75): angle = 0.5 * 2 * PI = PI.
        // Moon angle = PI + PI = 2*PI = 0 -> Moon at zenith (Y=1, X=0).
        double sunAngle = (fraction - 0.25) * 2.0 * Math.PI;
        double moonAngle = sunAngle + Math.PI;

        // X = -sin(moonAngle), Y = cos(moonAngle), Z = 0
        double x = -Math.sin(moonAngle);
        double y = Math.cos(moonAngle);
        double z = 0.0;

        return new Vector(x, y, z).normalize();
    }

    /**
     * Checks if the angle between two normalized vectors is within maxAngleDegrees.
     *
     * @param lookDir         player looking direction vector
     * @param targetDir       target direction vector
     * @param maxAngleDegrees threshold in degrees
     * @return true if within angle threshold
     */
    public static boolean isAngleClose(Vector lookDir, Vector targetDir, double maxAngleDegrees) {
        if (lookDir == null || targetDir == null) {
            return false;
        }
        double dot = lookDir.dot(targetDir);
        // Clamp dot product to [-1.0, 1.0] to avoid NaN from acos due to floating point inaccuracies
        dot = Math.max(-1.0, Math.min(1.0, dot));
        double angleRad = Math.acos(dot);
        double angleDeg = Math.toDegrees(angleRad);
        return angleDeg <= maxAngleDegrees;
    }

    /**
     * Checks if a player is looking at the Moon.
     *
     * @param player          the player
     * @param maxAngleDegrees allowable deviation angle in degrees
     * @return true if player is looking at the Moon and conditions are met
     */
    public static boolean isLookingAtMoon(Player player, double maxAngleDegrees) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        var world = player.getWorld();
        if (!isNightTime(world.getTime())) {
            return false;
        }
        Vector lookDir = player.getEyeLocation().getDirection().normalize();
        Vector moonDir = getMoonDirection(world.getTime());
        return isAngleClose(lookDir, moonDir, maxAngleDegrees);
    }
}
