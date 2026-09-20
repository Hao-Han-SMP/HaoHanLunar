package vn.haohan.lunar.api.manager;

/**
 * Public interface for managing world spawners.
 */
public interface SpawnerManager {

    /**
     * Ticks spawner instances.
     */
    void tick();

    /**
     * @return Number of active registered spawners.
     */
    int activeSpawnerCount();
}
