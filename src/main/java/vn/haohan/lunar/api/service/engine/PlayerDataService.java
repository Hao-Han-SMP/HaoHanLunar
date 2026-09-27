package vn.haohan.lunar.api.service.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.service.IService;
import vn.haohan.lunar.api.system.data.PlayerDataManager;

/**
 * Service managing persistence and in-memory caches of player lunar progression.
 */
public class PlayerDataService implements IService {

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
