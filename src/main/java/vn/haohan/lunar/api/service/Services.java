package vn.haohan.lunar.api.service;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and lifecycle coordinator for all {@link IService} implementations.
 * Ensures ordered startup, shutdown, and tick scheduling.
 */
public final class Services {

    private static final List<IService> services = new ArrayList<>();
    private static final List<IService> tickableServices = new CopyOnWriteArrayList<>();
    private static BukkitTask tickTask;

    private Services() {}

    /**
     * Registers a service before lifecycle initialization.
     */
    public static void register(IService service) {
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
        services.sort(Comparator.comparingInt(IService::priority).reversed());

        plugin.getLogger().info("[Services] Initializing " + services.size() + " services...");
        for (IService service : services) {
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
                for (IService service : tickableServices) {
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

        List<IService> reversed = new ArrayList<>(services);
        Collections.reverse(reversed);

        for (IService service : reversed) {
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
    public static <T extends IService> T get(Class<T> clazz) {
        for (IService service : services) {
            if (clazz.isInstance(service)) {
                return (T) service;
            }
        }
        return null;
    }

    /**
     * Finds a registered service by name.
     */
    public static IService get(String name) {
        for (IService service : services) {
            if (service.name().equalsIgnoreCase(name)) {
                return service;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all registered services.
     */
    public static List<IService> getAll() {
        return Collections.unmodifiableList(services);
    }
}
