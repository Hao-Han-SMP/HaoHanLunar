package vn.haohan.lunar.api.presentation.particle.geometric;

import org.bukkit.util.Vector;
import vn.haohan.lunar.core.system.util.FastMath;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometric curve and shape point generator for particle animation sequences.
 * Uses FastMath trigonometric lookup tables for CPU-efficient calculations.
 */
public final class CurveMath {

    private static final double TWO_PI = Math.PI * 2.0;

    private CurveMath() {}

    /**
     * Generates a 3D helix spiral.
     *
     * @param radius    horizontal radius of the helix
     * @param height    vertical height
     * @param points    number of discrete points along the helix
     * @param rotations total number of full 360-degree rotations
     * @return list of offset vectors relative to origin
     */
    public static List<Vector> helix(double radius, double height, int points, double rotations) {
        int count = Math.max(1, Math.min(1000, points));
        List<Vector> offsets = new ArrayList<>(count);
        double totalAngle = TWO_PI * Math.max(0.1, rotations);

        for (int i = 0; i < count; i++) {
            double progress = (double) i / (count > 1 ? (count - 1) : 1);
            double angle = progress * totalAngle;
            double x = radius * FastMath.cos(angle);
            double y = progress * height;
            double z = radius * FastMath.sin(angle);
            offsets.add(new Vector(x, y, z));
        }
        return offsets;
    }

    /**
     * Generates a flat horizontal ring/circle.
     *
     * @param radius horizontal radius
     * @param points number of points
     * @return list of offset vectors relative to origin
     */
    public static List<Vector> ring(double radius, int points) {
        int count = Math.max(3, Math.min(500, points));
        List<Vector> offsets = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            double angle = (i * TWO_PI) / count;
            double x = radius * FastMath.cos(angle);
            double z = radius * FastMath.sin(angle);
            offsets.add(new Vector(x, 0.0, z));
        }
        return offsets;
    }

    /**
     * Generates a regular polygon (e.g. triangle, square, pentagon, hexagon, octagon).
     *
     * @param sides         number of polygon sides (>= 3)
     * @param radius        distance from center to each vertex
     * @param pointsPerSide points to interpolate along each side
     * @return list of offset vectors relative to origin
     */
    public static List<Vector> polygon(int sides, double radius, int pointsPerSide) {
        int numSides = Math.max(3, Math.min(32, sides));
        int pps = Math.max(1, Math.min(50, pointsPerSide));
        List<Vector> vertices = new ArrayList<>(numSides);

        for (int i = 0; i < numSides; i++) {
            double angle = (i * TWO_PI) / numSides;
            vertices.add(new Vector(radius * FastMath.cos(angle), 0.0, radius * FastMath.sin(angle)));
        }

        List<Vector> offsets = new ArrayList<>(numSides * pps);
        for (int i = 0; i < numSides; i++) {
            Vector v1 = vertices.get(i);
            Vector v2 = vertices.get((i + 1) % numSides);
            for (int j = 0; j < pps; j++) {
                double t = (double) j / pps;
                double x = v1.getX() + (v2.getX() - v1.getX()) * t;
                double z = v1.getZ() + (v2.getZ() - v1.getZ()) * t;
                offsets.add(new Vector(x, 0.0, z));
            }
        }
        return offsets;
    }

    /**
     * Generates points along a directional line.
     *
     * @param direction unit or non-unit direction vector
     * @param length    total length of the line
     * @param points    number of points along the line
     * @return list of offset vectors relative to origin
     */
    public static List<Vector> line(Vector direction, double length, int points) {
        int count = Math.max(2, Math.min(500, points));
        List<Vector> offsets = new ArrayList<>(count);
        Vector dir = (direction != null && direction.lengthSquared() > 0)
                ? direction.clone().normalize()
                : new Vector(0, 0, 1);

        for (int i = 0; i < count; i++) {
            double distance = (double) i / (count - 1) * length;
            offsets.add(dir.clone().multiply(distance));
        }
        return offsets;
    }

    /**
     * Generates a curved ballistic arc between origin and end offset.
     *
     * @param endOffset target position offset relative to origin
     * @param arcHeight apex height above the linear trajectory
     * @param points    number of points
     * @return list of offset vectors relative to origin
     */
    public static List<Vector> arc(Vector endOffset, double arcHeight, int points) {
        int count = Math.max(3, Math.min(500, points));
        List<Vector> offsets = new ArrayList<>(count);
        Vector end = endOffset != null ? endOffset : new Vector(0, 0, 10);

        for (int i = 0; i < count; i++) {
            double t = (double) i / (count - 1);
            // Parabolic curve: 4 * t * (1 - t) reaches 1.0 at t = 0.5
            double heightOffset = 4.0 * t * (1.0 - t) * arcHeight;
            double x = end.getX() * t;
            double y = end.getY() * t + heightOffset;
            double z = end.getZ() * t;
            offsets.add(new Vector(x, y, z));
        }
        return offsets;
    }
}
