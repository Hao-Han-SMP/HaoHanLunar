package vn.haohan.lunar.api.system.combat.raycast;

import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Result record of an intersection between a Ray and an object/entity/voxel.
 *
 * @param <T> type of object hit (LivingEntity, Block, or AABB)
 */
public record RaycastHit<T>(Vector point, Vector normal, double distance,
                            T target) implements Comparable<RaycastHit<T>> {

    public RaycastHit {
        Objects.requireNonNull(point, "Hit point must not be null");
        Objects.requireNonNull(normal, "Normal vector must not be null");
    }

    @Override
    public int compareTo(RaycastHit<T> o) {
        return Double.compare(this.distance, o.distance);
    }
}
