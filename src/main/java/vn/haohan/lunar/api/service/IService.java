package vn.haohan.lunar.api.service;

import vn.haohan.lunar.HaoHanLunarPlugin;

/**
 * Fundamental lifecycle contract for modular services in HaoHanLunar.
 * Each service manages its own lifecycle (init, disable), tick loop, priority, and dependencies.
 */
public interface IService {

    /**
     * The unique name of this service.
     */
    String name();

    /**
     * Initialization priority. Higher values are initialized earlier and disabled later.
     * Default is 0.
     */
    default int priority() {
        return 0;
    }

    /**
     * Called when the plugin enables.
     */
    default void init(HaoHanLunarPlugin plugin) {}

    /**
     * Called when the plugin disables.
     */
    default void disable(HaoHanLunarPlugin plugin) {}

    /**
     * Indicates whether this service requires per-tick processing.
     */
    default boolean isTickable() {
        return false;
    }

    /**
     * Invoked on every server tick if {@link #isTickable()} is true.
     */
    default void tick() {}
}
