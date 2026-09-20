package vn.haohan.lunar.core.subsystem;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Registry and orchestrator for all engine subsystems, modeled after LavaHack's SubSystems.
 */
public final class LunarSubSystems {

    private static final List<LunarSubSystem> subSystems = new ArrayList<>();
    private static final List<LunarSubSystem> tickableSubSystems = new CopyOnWriteArrayList<>();
    private static BukkitTask tickTask;

    private LunarSubSystems() {}

    /**
     * Registers a subsystem with the manager before initialization.
     */
    public static void register(LunarSubSystem subSystem) {
        if (subSystem != null && !subSystems.contains(subSystem)) {
            subSystems.add(subSystem);
        }
    }

    /**
     * Initializes all registered subsystems in descending priority order.
     * Subsystems that implement {@link Listener} are automatically registered with Bukkit.
     * Starts the central ticking loop for tickable subsystems.
     */
    public static void init(HaoHanLunarPlugin plugin) {
        // Sort descending: highest priority executes first
        subSystems.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        tickableSubSystems.clear();

        for (LunarSubSystem subSystem : subSystems) {
            try {
                subSystem.init(plugin);
                if (subSystem instanceof Listener listener) {
                    Bukkit.getPluginManager().registerEvents(listener, plugin);
                }
                if (subSystem.isTickable()) {
                    tickableSubSystems.add(subSystem);
                }
                plugin.getLogger().info("[SubSystem] Initialized: " + subSystem.name() + " (Priority: " + subSystem.priority() + ")");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[SubSystem] Failed to initialize: " + subSystem.name(), t);
            }
        }

        // Central ticking task (1 tick)
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (LunarSubSystem subSystem : tickableSubSystems) {
                try {
                    subSystem.tick();
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "[SubSystem] Error ticking " + subSystem.name() + ": " + t.getMessage(), t);
                }
            }
        }, 1L, 1L);
    }

    /**
     * Disables all registered subsystems in reverse priority order (lowest priority first).
     */
    public static void disable(HaoHanLunarPlugin plugin) {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        List<LunarSubSystem> reversed = new ArrayList<>(subSystems);
        Collections.reverse(reversed);

        for (LunarSubSystem subSystem : reversed) {
            try {
                subSystem.disable(plugin);
                plugin.getLogger().info("[SubSystem] Disabled: " + subSystem.name());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "[SubSystem] Error disabling " + subSystem.name(), t);
            }
        }
        tickableSubSystems.clear();
    }

    /**
     * Retrieves a registered subsystem by its class type.
     */
    @SuppressWarnings("unchecked")
    public static <T extends LunarSubSystem> T get(Class<T> clazz) {
        for (LunarSubSystem subSystem : subSystems) {
            if (clazz.isInstance(subSystem)) {
                return (T) subSystem;
            }
        }
        return null;
    }

    /**
     * Retrieves a registered subsystem by name.
     */
    public static LunarSubSystem get(String name) {
        for (LunarSubSystem subSystem : subSystems) {
            if (subSystem.name().equalsIgnoreCase(name)) {
                return subSystem;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all registered subsystems.
     */
    public static List<LunarSubSystem> getSubSystems() {
        return Collections.unmodifiableList(subSystems);
    }

    /**
     * Clears all registered subsystems (useful for reloads or testing).
     */
    public static void clear() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
        subSystems.clear();
        tickableSubSystems.clear();
    }
}
