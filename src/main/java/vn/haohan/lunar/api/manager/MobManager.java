package vn.haohan.lunar.api.manager;

import org.bukkit.entity.Entity;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages active custom mob instances within the world.
 */
public interface MobManager {

    /**
     * Looks up an active custom mob by its Minecraft entity UUID.
     *
     * @param entityUuid the entity UUID
     * @return optional containing the active mob, or empty if not managed
     */
    Optional<? extends Mob> getMob(UUID entityUuid);

    /**
     * Looks up an active custom mob by its Bukkit entity instance.
     *
     * @param entity the Bukkit entity
     * @return optional containing the active mob, or empty if null or unmanaged
     */
    default Optional<? extends Mob> getMob(Entity entity) {
        return entity == null ? Optional.empty() : getMob(entity.getUniqueId());
    }

    /**
     * Checks if the given Bukkit entity represents an active custom mob.
     *
     * @param entity the entity to inspect
     * @return true if managed by this runtime
     */
    default boolean isLunarMob(Entity entity) {
        return entity != null && isManaged(entity.getUniqueId());
    }

    /**
     * Checks if an entity UUID is actively managed by this runtime.
     *
     * @param entityUuid the entity UUID to query
     * @return true if an active mob with this UUID exists
     */
    boolean isManaged(UUID entityUuid);

    /**
     * Returns the total count of currently active managed mobs.
     *
     * @return active mob count
     */
    int activeCount();

    /**
     * Returns an unmodifiable snapshot collection of all active mobs.
     *
     * @return active mobs collection
     */
    Collection<? extends Mob> getActiveMobs();

    /**
     * Unregisters and removes a mob by its entity UUID.
     *
     * @param entityUuid the UUID of the entity to unregister
     * @return the removed mob instance, or null if not found
     */
    Mob unregister(UUID entityUuid);
}
