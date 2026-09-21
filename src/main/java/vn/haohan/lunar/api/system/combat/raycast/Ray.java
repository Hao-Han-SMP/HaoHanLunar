package vn.haohan.lunar.api.system.combat.raycast;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Immutable 3D ray for geometric raycasting.
 */
public record Ray(Vector origin, Vector direction, double maxDistance) {

    public Ray {
        Objects.requireNonNull(origin, "Origin vector must not be null");
        Objects.requireNonNull(direction, "Direction vector must not be null");
        if (direction.lengthSquared() <= 1e-12) {
            throw new IllegalArgumentException("Direction vector cannot be zero length");
        }
        direction = direction.clone().normalize();
        maxDistance = Math.max(0.01, maxDistance);
    }

    public static Ray of(Vector origin, Vector direction, double maxDistance) {
        return new Ray(origin.clone(), direction, maxDistance);
    }

    public static Ray fromLocation(Location location, double maxDistance) {
        Objects.requireNonNull(location, "Location must not be null");
        return new Ray(location.toVector(), location.getDirection(), maxDistance);
    }

    public Vector getPoint(double distance) {
        return origin.clone().add(direction.clone().multiply(distance));
    }
}
