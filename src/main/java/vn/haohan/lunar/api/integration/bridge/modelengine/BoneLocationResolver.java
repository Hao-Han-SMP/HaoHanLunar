package vn.haohan.lunar.api.integration.bridge.modelengine;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Functional resolver for bone 3D locations on modeled entities.
 */
@FunctionalInterface
public interface BoneLocationResolver {

    /**
     * Resolves the real-time 3D coordinate of the specified bone on the entity.
     * Must safely fallback to eye location or root location if the bone or model does not exist.
     *
     * @param entity the underlying entity
     * @param boneName the bone identifier (e.g. "head", "right_hand", "mouth")
     * @return the resolved 3D location, or null if entity is null
     */
    Location resolveBoneLocation(Entity entity, String boneName);
}
