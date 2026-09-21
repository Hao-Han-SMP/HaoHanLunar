package vn.haohan.lunar.api.system.combat.raycast;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Predicate;

/**
 * Thread-safe spatial raycasting and voxel raymarching engine.
 * Computes entity hitbox intersections and grid traversals off the Paper main thread.
 */
public final class RaycastEngine {

    private RaycastEngine() {
    }

    /**
     * Performs a raycast against a collection of candidate entities with piercing support.
     * Hits are sorted by distance ascending.
     *
     * @param ray           the ray trajectory
     * @param candidates    candidate entities
     * @param filter        optional entity filter
     * @param hitboxPadding extra margin added to entity hitboxes
     * @param maxPierces    maximum entities that can be pierced
     * @return ordered list of raycast hits up to maxPierces
     */
    public static List<RaycastHit<LivingEntity>> raycastEntities(Ray ray, Collection<? extends LivingEntity> candidates, Predicate<LivingEntity> filter, double hitboxPadding, int maxPierces) {
        if (ray == null || candidates == null || candidates.isEmpty() || maxPierces <= 0) {
            return Collections.emptyList();
        }

        List<RaycastHit<LivingEntity>> hits = new ArrayList<>();

        for (LivingEntity entity : candidates) {
            if (entity == null || entity.isDead() || !entity.isValid()) {
                continue;
            }
            if (filter != null && !filter.test(entity)) {
                continue;
            }

            AABB box = AABB.fromEntity(entity, hitboxPadding);
            OptionalDouble distOpt = box.intersectsRay(ray);
            if (distOpt.isPresent()) {
                double dist = distOpt.getAsDouble();
                Vector hitPoint = ray.getPoint(dist);
                Vector center = new Vector((box.minX() + box.maxX()) * 0.5, (box.minY() + box.maxY()) * 0.5, (box.minZ() + box.maxZ()) * 0.5);
                Vector normal = hitPoint.clone().subtract(center);
                if (normal.lengthSquared() > 1e-9) {
                    normal.normalize();
                } else {
                    normal = ray.direction().clone().multiply(-1);
                }

                hits.add(new RaycastHit<>(hitPoint, normal, dist, entity));
            }
        }

        Collections.sort(hits);
        if (hits.size() > maxPierces) {
            return hits.subList(0, maxPierces);
        }
        return hits;
    }

    /**
     * Amanatides-Woo Fast Voxel Traversal algorithm (DDA 3D).
     * Traverses integer grid coordinates along the ray without skipping any voxels.
     *
     * @param ray               the ray trajectory
     * @param solidVoxelChecker predicate checking if an integer grid coordinate (x, y, z) is solid
     * @return RaycastHit of the first solid voxel hit, or empty if no voxel was hit within max distance
     */
    public static Optional<RaycastHit<Vector>> raymarchVoxels(Ray ray, Predicate<Vector> solidVoxelChecker) {
        if (ray == null || solidVoxelChecker == null) {
            return Optional.empty();
        }

        Vector origin = ray.origin();
        Vector dir = ray.direction();
        double maxDist = ray.maxDistance();

        int x = (int)Math.floor(origin.getX());
        int y = (int)Math.floor(origin.getY());
        int z = (int)Math.floor(origin.getZ());

        int stepX = dir.getX() > 0 ? 1 : (dir.getX() < 0 ? -1 : 0);
        int stepY = dir.getY() > 0 ? 1 : (dir.getY() < 0 ? -1 : 0);
        int stepZ = dir.getZ() > 0 ? 1 : (dir.getZ() < 0 ? -1 : 0);

        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dir.getX()) : Double.MAX_VALUE;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dir.getY()) : Double.MAX_VALUE;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dir.getZ()) : Double.MAX_VALUE;

        double tMaxX = stepX > 0 ? (Math.floor(origin.getX()) + 1.0 - origin.getX()) * tDeltaX : (origin.getX() - Math.floor(origin.getX())) * tDeltaX;
        double tMaxY = stepY > 0 ? (Math.floor(origin.getY()) + 1.0 - origin.getY()) * tDeltaY : (origin.getY() - Math.floor(origin.getY())) * tDeltaY;
        double tMaxZ = stepZ > 0 ? (Math.floor(origin.getZ()) + 1.0 - origin.getZ()) * tDeltaZ : (origin.getZ() - Math.floor(origin.getZ())) * tDeltaZ;

        Vector normal = new Vector(0, 0, 0);
        double distance = 0.0;

        while (distance <= maxDist) {
            Vector currentVoxel = new Vector(x, y, z);
            if (solidVoxelChecker.test(currentVoxel)) {
                Vector hitPoint = ray.getPoint(distance);
                return Optional.of(new RaycastHit<>(hitPoint, normal.clone(), distance, currentVoxel));
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    distance = tMaxX;
                    tMaxX += tDeltaX;
                    x += stepX;
                    normal = new Vector(-stepX, 0, 0);
                } else {
                    distance = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    normal = new Vector(0, 0, -stepZ);
                }
            } else {
                if (tMaxY < tMaxZ) {
                    distance = tMaxY;
                    tMaxY += tDeltaY;
                    y += stepY;
                    normal = new Vector(0, -stepY, 0);
                } else {
                    distance = tMaxZ;
                    tMaxZ += tDeltaZ;
                    z += stepZ;
                    normal = new Vector(0, 0, -stepZ);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Calculates the reflected velocity vector after bouncing against a surface with a given normal.
     * Formula: v_bounce = (v - 2 * (v . n) * n) * multiplier
     */
    public static Vector reflect(Vector velocity, Vector normal, double bounceMultiplier) {
        if (velocity == null || normal == null) {
            return velocity != null ? velocity.clone() : new Vector(0, 0, 0);
        }
        Vector n = normal.clone().normalize();
        double dot = velocity.dot(n);
        Vector reflected = velocity.clone().subtract(n.multiply(2.0 * dot));
        return reflected.multiply(Math.max(0.0, bounceMultiplier));
    }
}
