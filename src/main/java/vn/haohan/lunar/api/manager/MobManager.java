package vn.haohan.lunar.api.manager;

import org.bukkit.entity.Entity;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public interface for managing custom Lunar mobs.
 */
public interface MobManager {

    /**
     * Finds an active Mob by its Bukkit entity UUID.
     */
    Optional<? extends Mob> getMob(UUID entityUuid);

    /**
     * Finds an active Mob by Bukkit Entity.
     */
    default Optional<? extends Mob> getMob(Entity entity) {
        return entity == null ? Optional.empty() : getMob(entity.getUniqueId());
    }

    /**
     * Checks if an entity is a registered custom Lunar mob.
     */
    default boolean isLunarMob(Entity entity) {
        return entity != null && isManaged(entity.getUniqueId());
    }

    /**
     * Checks if an entity UUID is actively managed.
     */
    boolean isManaged(UUID entityUuid);

    /**
     * @return Number of currently active custom mobs.
     */
    int activeCount();

    /**
     * @return An unmodifiable collection of all active mobs.
     */
    Collection<? extends Mob> getActiveMobs();

    /**
     * Unregisters a mob by its entity UUID.
     */
    Mob unregister(UUID entityUuid);
}
