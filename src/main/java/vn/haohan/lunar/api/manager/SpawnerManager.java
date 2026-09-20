package vn.haohan.lunar.api.manager;

/**
 * Manages fixed and dynamic mob spawners in active worlds.
 */
public interface SpawnerManager {

    /**
     * Executes periodic spawner updates, evaluating spawn timers, conditions, and mob caps.
     */
    void tick();

    /**
     * Returns the number of actively registered spawners.
     *
     * @return active spawner count
     */
    int activeSpawnerCount();
}
