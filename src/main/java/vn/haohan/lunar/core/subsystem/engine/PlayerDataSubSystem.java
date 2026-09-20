package vn.haohan.lunar.core.subsystem.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;
import vn.haohan.lunar.core.system.data.PlayerDataManager;

/**
 * SubSystem managing persistence and in-memory caches of player lunar progression.
 */
public final class PlayerDataSubSystem implements LunarSubSystem {

    private PlayerDataManager dataManager;

    @Override
    public String name() {
        return "PlayerData";
    }

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public void init(HaoHanLunarPlugin plugin) {
        dataManager = new PlayerDataManager(plugin);
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        if (dataManager != null) {
            dataManager.saveAll();
        }
    }

    public PlayerDataManager getDataManager() {
        return dataManager;
    }
}
