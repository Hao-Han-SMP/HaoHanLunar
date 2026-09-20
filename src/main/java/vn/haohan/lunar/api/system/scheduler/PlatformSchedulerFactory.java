package vn.haohan.lunar.core.system.scheduler;

import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Factory and detector creating the appropriate platform scheduler for Paper or Folia.
 */
public final class PlatformSchedulerFactory {

    private PlatformSchedulerFactory() {
    }

    /**
     * Determines whether the server runtime is Folia.
     */
    public static boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Creates an appropriate platform scheduler instance.
     */
    public static LunarPlatformScheduler create(Plugin plugin) {
        Objects.requireNonNull(plugin, "Plugin must not be null");
        if (isFoliaServer()) {
            return new FoliaSchedulerAdapter(plugin);
        }
        return new PaperSchedulerAdapter(plugin);
    }
}
