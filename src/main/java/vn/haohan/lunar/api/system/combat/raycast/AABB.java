package vn.haohan.lunar.api.system.combat.raycast;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import java.util.OptionalDouble;

/**
 * High-performance, immutable Axis-Aligned Bounding Box (AABB) for thread-safe spatial collision tests.
 * Can be evaluated asynchronously off the Paper main thread.
 */
public record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public AABB {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Min coordinates must be less than or equal to max coordinates");
        }
    }

    public static AABB of(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new AABB(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ), Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
    }

    public static AABB fromCenter(Vector center, double xHalf, double yHalf, double zHalf) {
        return of(center.getX() - xHalf, center.getY() - yHalf, center.getZ() - zHalf, center.getX() + xHalf, center.getY() + yHalf, center.getZ() + zHalf);
    }

    public static AABB fromEntity(Entity entity, double expand) {
        Location loc = entity.getLocation();
        double w = 0.6 / 2.0;
        double h = 1.8;
        try {
            w = entity.getWidth() / 2.0;
            h = entity.getHeight();
        }
        catch (Throwable ignored) {
        }

        return of(loc.getX() - w - expand, loc.getY() - expand, loc.getZ() - w - expand, loc.getX() + w + expand, loc.getY() + h + expand, loc.getZ() + w + expand);
    }

    public AABB expand(double amount) {
        return of(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
    }

    public boolean contains(Vector point) {
        if (point == null) return false;
        return point.getX() >= minX && point.getX() <= maxX && point.getY() >= minY && point.getY() <= maxY && point.getZ() >= minZ && point.getZ() <= maxZ;
    }

    public boolean intersects(AABB other) {
        if (other == null) return false;
        return this.maxX >= other.minX && this.minX <= other.maxX && this.maxY >= other.minY && this.minY <= other.maxY && this.maxZ >= other.minZ && this.minZ <= other.maxZ;
    }

    /**
     * Fast Slab Ray-AABB intersection test (Kay-Kajiya algorithm).
     *
     * @param ray the ray to test against
     * @return distance to intersection along the ray direction, or empty if no hit
     */
    public OptionalDouble intersectsRay(Ray ray) {
        if (ray == null) return OptionalDouble.empty();

        Vector origin = ray.origin();
        Vector dir = ray.direction();

        double tmin = (minX - origin.getX()) / (Math.abs(dir.getX()) < 1e-9 ? 1e-9 : dir.getX());
        double tmax = (maxX - origin.getX()) / (Math.abs(dir.getX()) < 1e-9 ? 1e-9 : dir.getX());

        if (tmin > tmax) {
            double temp = tmin;
            tmin = tmax;
            tmax = temp;
        }

        double tymin = (minY - origin.getY()) / (Math.abs(dir.getY()) < 1e-9 ? 1e-9 : dir.getY());
        double tymax = (maxY - origin.getY()) / (Math.abs(dir.getY()) < 1e-9 ? 1e-9 : dir.getY());

        if (tymin > tymax) {
            double temp = tymin;
            tymin = tymax;
            tymax = temp;
        }

        if ((tmin > tymax) || (tymin > tmax)) {
            return OptionalDouble.empty();
        }

        if (tymin > tmin) {
            tmin = tymin;
        }
        if (tymax < tmax) {
            tmax = tymax;
        }

        double tzmin = (minZ - origin.getZ()) / (Math.abs(dir.getZ()) < 1e-9 ? 1e-9 : dir.getZ());
        double tzmax = (maxZ - origin.getZ()) / (Math.abs(dir.getZ()) < 1e-9 ? 1e-9 : dir.getZ());

        if (tzmin > tzmax) {
            double temp = tzmin;
            tzmin = tzmax;
            tzmax = temp;
        }

        if ((tmin > tzmax) || (tzmin > tmax)) {
            return OptionalDouble.empty();
        }

        if (tzmin > tmin) {
            tmin = tzmin;
        }
        if (tzmax < tmax) {
            tmax = tzmax;
        }

        if (tmax < 0 || tmin > ray.maxDistance()) {
            return OptionalDouble.empty();
        }

        double hitDist = tmin >= 0 ? tmin : tmax;
        return hitDist <= ray.maxDistance() ? OptionalDouble.of(hitDist) : OptionalDouble.empty();
    }
}
