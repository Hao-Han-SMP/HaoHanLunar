package vn.haohan.lunar.core.subsystem;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and lifecycle manager for all {@link ILunarSubSystem} implementations.
 * Ensures ordered startup, shutdown, and tick scheduling.
 */
public final class LunarSubSystems {

    private static final List<ILunarSubSystem> subSystems = new ArrayList<>();
    private static final List<ILunarSubSystem> tickableSubSystems = new CopyOnWriteArrayList<>();
    private static BukkitTask tickTask;

    private LunarSubSystems() {}

    /**
     * Registers a subsystem before lifecycle initialization.
     */
    public static void register(ILunarSubSystem subSystem) {
        if (subSystem != null && !subSystems.contains(subSystem)) {
            subSystems.add(subSystem);
            if (subSystem.isTickable()) {
                tickableSubSystems.add(subSystem);
            }
        }
    }

    /**
     * Initializes all registered subsystems in order of priority (highest first).
     */
    public static void init(HaoHanLunarPlugin plugin) {
        subSystems.sort(Comparator.comparingInt(ILunarSubSystem::priority).reversed());

        plugin.getLogger().info("[SubSystems] Initializing " + subSystems.size() + " subsystems...");
        for (ILunarSubSystem subSystem : subSystems) {
            try {
                plugin.getLogger().info("[SubSystems] Enabling: " + subSystem.name() + " (priority " + subSystem.priority() + ")");
                subSystem.init(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("[SubSystems] Failed to enable: " + subSystem.name());
                e.printStackTrace();
            }
        }

        // Start tick loop if any tickable subsystems exist
        if (!tickableSubSystems.isEmpty()) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (ILunarSubSystem subSystem : tickableSubSystems) {
                    try {
                        subSystem.tick();
                    } catch (Exception e) {
                        plugin.getLogger().severe("[SubSystems] Error during tick in: " + subSystem.name());
                        e.printStackTrace();
                    }
                }
            }, 1L, 1L);
        }
    }

    /**
     * Disables all registered subsystems in reverse order of priority (lowest first).
     */
    public static void disable(HaoHanLunarPlugin plugin) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        List<ILunarSubSystem> reversed = new ArrayList<>(subSystems);
        Collections.reverse(reversed);

        for (ILunarSubSystem subSystem : reversed) {
            try {
                plugin.getLogger().info("[SubSystems] Disabling: " + subSystem.name());
                subSystem.disable(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("[SubSystems] Error disabling: " + subSystem.name());
                e.printStackTrace();
            }
        }
        subSystems.clear();
        tickableSubSystems.clear();
    }

    /**
     * Finds a registered subsystem by class.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ILunarSubSystem> T get(Class<T> clazz) {
        for (ILunarSubSystem subSystem : subSystems) {
            if (clazz.isInstance(subSystem)) {
                return (T) subSystem;
            }
        }
        return null;
    }

    /**
     * Finds a registered subsystem by name.
     */
    public static ILunarSubSystem get(String name) {
        for (ILunarSubSystem subSystem : subSystems) {
            if (subSystem.name().equalsIgnoreCase(name)) {
                return subSystem;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all registered subsystems.
     */
    public static List<ILunarSubSystem> getSubSystems() {
        return Collections.unmodifiableList(subSystems);
    }
}
