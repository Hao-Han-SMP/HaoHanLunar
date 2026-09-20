package vn.haohan.lunar.core.subsystem.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.system.item.LunarItems;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

/**
 * SubSystem responsible for registering custom items with HaoHanItemCore.
 */
public final class ItemCoreSubSystem implements LunarSubSystem {

    @Override
    public String name() {
        return "ItemCore";
    }

    @Override
    public int priority() {
        return 95;
    }

    @Override
    public void init(HaoHanLunarPlugin plugin) {
        try {
            LunarItems.register();
            plugin.getLogger().info("Successfully registered custom items with HaoHanItemCore API.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register custom items with HaoHanItemCore! Is it loaded? " + e.getMessage());
        }
    }
}
