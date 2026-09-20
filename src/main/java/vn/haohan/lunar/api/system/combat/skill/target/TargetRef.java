package vn.haohan.lunar.api.system.combat.skill.target;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable target result that can represent either an entity or a location in the world.
 */
public record TargetRef(Entity entity, Location location) {

    public TargetRef {
        if (entity == null && location == null) {
            throw new IllegalArgumentException("Target must contain an entity or a location");
        }
        if (location != null) {
            location = location.clone();
        }
    }

    public static TargetRef entity(Entity entity) {
        return new TargetRef(Objects.requireNonNull(entity, "Target entity must not be null"), null);
    }

    public static TargetRef location(Location location) {
        return new TargetRef(null, Objects.requireNonNull(location, "Target location must not be null"));
    }

    public static TargetRef of(Entity entity) {
        return entity(entity);
    }

    public static TargetRef of(Location location) {
        return location(location);
    }

    public Location asLocation() {
        if (location != null) {
            return location.clone();
        }
        if (entity != null) {
            try {
                return entity.getLocation();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public Optional<Entity> entityOptional() {
        return Optional.ofNullable(entity);
    }

    public Optional<Location> locationOptional() {
        return Optional.ofNullable(location == null ? null : location.clone());
    }
}
