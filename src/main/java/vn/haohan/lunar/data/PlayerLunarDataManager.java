package vn.haohan.lunar.data;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerLunarDataManager {

    private final Plugin plugin;
    private final Map<UUID, PlayerLunarData> cache = new HashMap<>();

    public PlayerLunarDataManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public PlayerLunarData get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), uuid -> {
            PlayerLunarData data = new PlayerLunarData(player);
            data.load(plugin);
            return data;
        });
    }

    public void saveAndRemove(Player player) {
        PlayerLunarData data = cache.remove(player.getUniqueId());
        if (data != null) {
            data.save(plugin);
        }
    }

    public void remove(Player player) {
        cache.remove(player.getUniqueId());
    }

    public void saveAll() {
        for (PlayerLunarData data : cache.values()) {
            data.save(plugin);
        }
        cache.clear();
    }
}
