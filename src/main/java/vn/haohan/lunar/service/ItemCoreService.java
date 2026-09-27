package vn.haohan.lunar.service;

import org.bukkit.plugin.Plugin;
import vn.haohan.engine.core.service.IService;
import vn.haohan.lunar.item.LunarItems;

public class ItemCoreService implements IService {
    @Override public String name() { return "ItemCore"; }
    @Override public int priority() { return 95; }
    @Override public void init(Plugin plugin) {
        try {
            LunarItems.register();
            if (plugin != null) plugin.getLogger().info("Successfully registered custom items with HaoHanItemCore API.");
        } catch (Exception e) {
            if (plugin != null) plugin.getLogger().severe("Failed to register custom items with HaoHanItemCore! Is it loaded? " + e.getMessage());
        }
    }
}
