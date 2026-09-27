package vn.haohan.engine.core.service;

import org.bukkit.plugin.Plugin;

/**
 * Fundamental lifecycle contract for modular services in HaoHan Engine.
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
    default void init(Plugin plugin) {}

    /**
     * Called when the plugin disables.
     */
    default void disable(Plugin plugin) {}

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
