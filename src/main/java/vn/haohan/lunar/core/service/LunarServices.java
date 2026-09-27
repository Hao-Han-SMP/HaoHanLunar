package vn.haohan.lunar.core.service;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and lifecycle coordinator for all {@link ILunarService} implementations.
 * Ensures ordered startup, shutdown, and tick scheduling.
 */
public final class LunarServices {

    private static final List<ILunarService> services = new ArrayList<>();
    private static final List<ILunarService> tickableServices = new CopyOnWriteArrayList<>();
    private static BukkitTask tickTask;

    private LunarServices() {}

    /**
     * Registers a service before lifecycle initialization.
     */
    public static void register(ILunarService service) {
        if (service != null && !services.contains(service)) {
            services.add(service);
            if (service.isTickable()) {
                tickableServices.add(service);
            }
        }
    }

    /**
     * Initializes all registered services in order of priority (highest first).
     */
    public static void init(HaoHanLunarPlugin plugin) {
        services.sort(Comparator.comparingInt(ILunarService::priority).reversed());

        plugin.getLogger().info("[Services] Initializing " + services.size() + " services...");
        for (ILunarService service : services) {
            try {
                plugin.getLogger().info("[Services] Enabling: " + service.name() + " (priority " + service.priority() + ")");
                service.init(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("[Services] Failed to enable: " + service.name());
                e.printStackTrace();
            }
        }

        // Start tick loop if any tickable services exist
        if (!tickableServices.isEmpty()) {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (ILunarService service : tickableServices) {
                    try {
                        service.tick();
                    } catch (Exception e) {
                        plugin.getLogger().severe("[Services] Error during tick in: " + service.name());
                        e.printStackTrace();
                    }
                }
            }, 1L, 1L);
        }
    }

    /**
     * Disables all registered services in reverse order of priority (lowest first).
     */
    public static void disable(HaoHanLunarPlugin plugin) {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        List<ILunarService> reversed = new ArrayList<>(services);
        Collections.reverse(reversed);

        for (ILunarService service : reversed) {
            try {
                plugin.getLogger().info("[Services] Disabling: " + service.name());
                service.disable(plugin);
            } catch (Exception e) {
                plugin.getLogger().severe("[Services] Error disabling: " + service.name());
                e.printStackTrace();
            }
        }
        services.clear();
        tickableServices.clear();
    }

    /**
     * Finds a registered service by class.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ILunarService> T get(Class<T> clazz) {
        for (ILunarService service : services) {
            if (clazz.isInstance(service)) {
                return (T) service;
            }
        }
        return null;
    }

    /**
     * Finds a registered service by name.
     */
    public static ILunarService get(String name) {
        for (ILunarService service : services) {
            if (service.name().equalsIgnoreCase(name)) {
                return service;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all registered services.
     */
    public static List<ILunarService> getAll() {
        return Collections.unmodifiableList(services);
    }
}
