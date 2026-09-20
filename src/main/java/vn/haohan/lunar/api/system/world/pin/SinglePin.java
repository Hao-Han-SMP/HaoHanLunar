package vn.haohan.lunar.api.system.world.pin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * An immutable 3D spatial anchor point.
 */
public record SinglePin(String name, String worldName, double x, double y, double z) {

    public SinglePin {
        Objects.requireNonNull(name, "Pin name must not be null");
        Objects.requireNonNull(worldName, "World name must not be null");
    }

    public static SinglePin fromLocation(String name, Location location) {
        Objects.requireNonNull(name, "Pin name must not be null");
        Objects.requireNonNull(location, "Location must not be null");
        String world = location.getWorld() != null ? location.getWorld().getName() : "world";
        return new SinglePin(name, world, location.getX(), location.getY(), location.getZ());
    }

    public Location toLocation() {
        World world = null;
        try {
            world = Bukkit.getWorld(worldName);
        } catch (Throwable ignored) {}
        return new Location(world, x, y, z);
    }

    public double distance(Location location) {
        return Math.sqrt(distanceSquared(location));
    }

    public double distanceSquared(Location location) {
        if (location == null) return Double.MAX_VALUE;
        if (location.getWorld() != null && !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return Double.MAX_VALUE;
        }
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
