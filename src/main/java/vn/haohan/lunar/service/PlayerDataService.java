package vn.haohan.lunar.service;

import org.bukkit.plugin.Plugin;
import vn.haohan.engine.core.service.IService;
import vn.haohan.engine.api.system.data.PlayerDataManager;
import vn.haohan.lunar.HaoHanLunarPlugin;

public class PlayerDataService implements IService {
    private PlayerDataManager dataManager;
    @Override public String name() { return "PlayerData"; }
    @Override public int priority() { return 90; }
    @Override public void init(Plugin plugin) {
        if (plugin instanceof HaoHanLunarPlugin lunarPlugin) {
            dataManager = new PlayerDataManager(lunarPlugin);
        }
    }
    @Override public void disable(Plugin plugin) {
        if (dataManager != null) {
            dataManager.saveAll();
        }
    }
    public PlayerDataManager getDataManager() { return dataManager; }
}
