package vn.haohan.engine.core.service;

import org.bukkit.event.Listener;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.api.system.world.pin.PinBoundaryListener;
import vn.haohan.engine.api.system.world.pin.PinManager;
import vn.haohan.engine.core.service.IService;

/**
 * Service managing pin-based region boundaries and boundary transition events.
 */
public class PinService implements IService, Listener {

    private PinBoundaryListener boundaryListener;

    @Override
    public String name() {
        return "PinSystem";
    }

    @Override
    public int priority() {
        return 85;
    }

    @Override
    public void init(org.bukkit.plugin.Plugin plugin) {
        try {
            PinManager.get().load(plugin.getDataFolder().toPath().resolve("regions.yml"));
            boundaryListener = new PinBoundaryListener();
            plugin.getServer().getPluginManager().registerEvents(boundaryListener, plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not load regions.yml: " + t.getMessage());
        }
    }

    @Override
    public void disable(org.bukkit.plugin.Plugin plugin) {
        try {
            PinManager.get().save(plugin.getDataFolder().toPath().resolve("regions.yml"));
        } catch (Throwable ignored) {}
    }
}
