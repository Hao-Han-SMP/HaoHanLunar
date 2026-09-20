package vn.haohan.lunar.core.subsystem;

import vn.haohan.lunar.HaoHanLunarPlugin;

/**
 * Represents a modular subsystem within HaoHanLunar, modeled after the LavaHack SubSystem architecture.
 * Each subsystem manages its own lifecycle (init, disable), tick loop, and event listener registration.
 */
public interface LunarSubSystem {

    /**
     * The unique name of this subsystem.
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
     * Indicates whether this subsystem requires per-tick processing.
     */
    default boolean isTickable() {
        return false;
    }

    /**
     * Invoked on every server tick if {@link #isTickable()} is true.
     */
    default void tick() {}
}
