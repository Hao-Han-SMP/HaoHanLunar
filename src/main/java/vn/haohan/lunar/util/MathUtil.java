package vn.haohan.lunar.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

/**
 * Common mathematical and geometric utilities for HaoHanLunar.
 * Includes time conversions, interpolation (lerp, spline), clamping, vector calculations, and trigonometry.
 */
public final class MathUtil {
    private MathUtil() {}

    /**
     * Converts seconds to server ticks (1s = 20 ticks).
     */
    public static int secondsToTicks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    /**
     * Converts ticks to seconds.
     */
    public static double ticksToSeconds(int ticks) {
        return ticks / 20.0;
    }

    /**
     * Converts milliseconds to ticks (50ms = 1 tick).
     */
    public static int msToTicks(long ms) {
        return (int) Math.round(ms / 50.0);
    }

    /**
     * Converts ticks to milliseconds.
     */
    public static long ticksToMs(int ticks) {
        return ticks * 50L;
    }

    public static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    public static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    public static double clamp01(double val) {
        return clamp(val, 0.0, 1.0);
    }

    public static float clamp01(float val) {
        return clamp(val, 0.0f, 1.0f);
    }

    /**
     * Normalizes an angle in degrees to the [-180, 180] range.
     */
    public static float normalizeAngle(float angle) {
        while (angle <= -180.0f) angle += 360.0f;
        while (angle > 180.0f) angle -= 360.0f;
        return angle;
    }

    /**
     * Normalizes an angle in degrees to the [-180, 180] range (double precision).
     */
    public static double normalizeAngle(double angle) {
        while (angle <= -180.0) angle += 360.0;
        while (angle > 180.0) angle -= 360.0;
        return angle;
    }

    /**
     * Moves current value toward target by at most maxStep.
     */
    public static float approach(float current, float target, float maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        } else {
            return Math.max(current - maxStep, target);
        }
    }

    public static double approach(double current, double target, double maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        } else {
            return Math.max(current - maxStep, target);
        }
    }

    /**
     * Linear interpolation (double).
     */
    public static double lerp(double start, double end, double factor) {
        return start + (end - start) * factor;
    }

    /**
     * Linear interpolation (float).
     */
    public static float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    /**
     * Shortest-arc angle interpolation (float degrees).
     */
    public static float lerpAngle(float start, float end, float pct) {
        float diff = normalizeAngle(end - start);
        return normalizeAngle(start + diff * pct);
    }

    /**
     * Shortest-arc angle interpolation (double degrees).
     */
    public static double lerpAngle(double start, double end, double pct) {
        double diff = normalizeAngle(end - start);
        return normalizeAngle(start + diff * pct);
    }

    /**
     * Catmull-Rom spline interpolation for 3D Vector (JOML Vector3f).
     */
    public static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        float f0 = -0.5f * t3 + t2 - 0.5f * t;
        float f1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
        float f2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float f3 = 0.5f * t3 - 0.5f * t2;

        return new Vector3f(
                p0.x * f0 + p1.x * f1 + p2.x * f2 + p3.x * f3,
                p0.y * f0 + p1.y * f1 + p2.y * f2 + p3.y * f3,
                p0.z * f0 + p1.z * f1 + p2.z * f2 + p3.z * f3
        );
    }

    /**
     * Centripetal Catmull-Rom spline interpolation (alpha = 0.5) to prevent loops and cusps.
     */
    public static Vector3f catmullRomCentripetal(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float dt0 = (float) Math.pow(Math.max(1e-4, p0.distanceSquared(p1)), 0.25);
        float dt1 = (float) Math.pow(Math.max(1e-4, p1.distanceSquared(p2)), 0.25);
        float dt2 = (float) Math.pow(Math.max(1e-4, p2.distanceSquared(p3)), 0.25);

        float t0 = 0.0f;
        float t1 = t0 + dt0;
        float t2 = t1 + dt1;
        float t3 = t2 + dt2;

        float targetT = t1 + t * (t2 - t1);

        Vector3f a1 = new Vector3f(p0).mul((t1 - targetT) / (t1 - t0)).add(new Vector3f(p1).mul((targetT - t0) / (t1 - t0)));
        Vector3f a2 = new Vector3f(p1).mul((t2 - targetT) / (t2 - t1)).add(new Vector3f(p2).mul((targetT - t1) / (t2 - t1)));
        Vector3f a3 = new Vector3f(p2).mul((t3 - targetT) / (t3 - t2)).add(new Vector3f(p3).mul((targetT - t2) / (t3 - t2)));

        Vector3f b1 = new Vector3f(a1).mul((t2 - targetT) / (t2 - t0)).add(new Vector3f(a2).mul((targetT - t0) / (t2 - t0)));
        Vector3f b2 = new Vector3f(a2).mul((t3 - targetT) / (t3 - t1)).add(new Vector3f(a3).mul((targetT - t1) / (t3 - t1)));

        return new Vector3f(b1).mul((t2 - targetT) / (t2 - t1)).add(new Vector3f(b2).mul((targetT - t1) / (t2 - t1)));
    }

    /**
     * Quadratic Bezier curve interpolation.
     */
    public static Vector bezierQuadratic(Vector p0, Vector p1, Vector p2, double t) {
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;

        Vector res = p0.clone().multiply(uu);
        res.add(p1.clone().multiply(2.0 * u * t));
        res.add(p2.clone().multiply(tt));
        return res;
    }

    /**
     * Converts a yaw angle into a normalized horizontal direction vector (XZ plane, Y=0).
     */
    public static Vector yawToDirection(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
    }

    /**
     * Converts a yaw angle into a normalized horizontal right vector (XZ plane, Y=0).
     */
    public static Vector yawToRightVector(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();
    }

    /**
     * Converts yaw and pitch into a 3D direction vector.
     */
    public static Vector toVector(float yaw, float pitch) {
        Vector vector = new Vector();
        double rotX = yaw;
        double rotY = pitch;
        double xz = Math.cos(Math.toRadians(rotY));
        vector.setY(-Math.sin(Math.toRadians(rotY)));
        vector.setX(-xz * Math.sin(Math.toRadians(rotX)));
        vector.setZ(xz * Math.cos(Math.toRadians(rotX)));
        return vector;
    }

    /**
     * Calculates the yaw angle in degrees from a horizontal direction vector.
     */
    public static float getYaw(Vector dir) {
        if (dir.getX() == 0 && dir.getZ() == 0) return 0f;
        return (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
    }

    /**
     * Calculates the pitch angle in degrees from a 3D direction vector.
     */
    public static float getPitch(Vector dir) {
        double xz = Math.sqrt(dir.getX() * dir.getX() + dir.getZ() * dir.getZ());
        return (float) -Math.toDegrees(Math.atan2(dir.getY(), xz));
    }

    /**
     * Rotates a vector around the Y axis by angleDegrees.
     */
    public static Vector rotateAroundY(Vector vector, double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return vector.clone().setX(x).setZ(z);
    }

    /**
     * Converts a Bukkit Location to a JOML Vector3f.
     */
    public static Vector3f toVector3f(Location loc) {
        return new Vector3f((float) loc.getX(), (float) loc.getY(), (float) loc.getZ());
    }

    /**
     * Converts a Bukkit Vector to a JOML Vector3f.
     */
    public static Vector3f toVector3f(Vector vec) {
        return new Vector3f((float) vec.getX(), (float) vec.getY(), (float) vec.getZ());
    }

    /**
     * Converts a JOML Vector3f to a Bukkit Vector.
     */
    public static Vector toBukkitVector(Vector3f vec) {
        return new Vector(vec.x, vec.y, vec.z);
    }

    /**
     * Gets a location on a circle around a center point on the horizontal plane.
     */
    public static Location getPointOnCircle(Location center, double radius, double angleDegrees) {
        double angleRad = Math.toRadians(angleDegrees);
        double x = center.getX() + radius * Math.cos(angleRad);
        double z = center.getZ() + radius * Math.sin(angleRad);
        return new Location(center.getWorld(), x, center.getY(), z);
    }

    /**
     * Calculates horizontal 2D distance between (x1, z1) and (x2, z2).
     */
    public static double distance(double x1, double z1, double x2, double z2) {
        double dx = x1 - x2;
        double dz = z1 - z2;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Calculates horizontal 2D distance between two locations on the XZ plane.
     */
    public static double distanceXZ(Location loc1, Location loc2) {
        return distance(loc1.getX(), loc1.getZ(), loc2.getX(), loc2.getZ());
    }

    /**
     * Calculates squared horizontal 2D distance between two locations on the XZ plane.
     */
    public static double distanceXZSq(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dz * dz;
    }
}
