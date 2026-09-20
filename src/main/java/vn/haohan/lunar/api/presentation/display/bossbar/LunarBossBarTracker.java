package vn.haohan.lunar.api.presentation.display.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and manages multiple independent BossBars attached to an ActiveLunarMob.
 */
public final class LunarBossBarTracker {

    private final Map<String, LunarBossBar> bars = new ConcurrentHashMap<>();

    public LunarBossBar create(String id, Component title, float progress,
                               BossBar.Color color, BossBar.Overlay overlay, double range) {
        if (id == null) return null;
        String key = id.toLowerCase(Locale.ROOT);
        LunarBossBar existing = bars.get(key);
        if (existing != null) {
            existing.removeAll();
        }
        LunarBossBar bar = new LunarBossBar(key, title, progress, color, overlay, range);
        bars.put(key, bar);
        return bar;
    }

    public Optional<LunarBossBar> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(bars.get(id.toLowerCase(Locale.ROOT)));
    }

    public Optional<LunarBossBar> remove(String id) {
        if (id == null) return Optional.empty();
        LunarBossBar removed = bars.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            removed.removeAll();
        }
        return Optional.ofNullable(removed);
    }

    public void removeAll() {
        for (LunarBossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    public Map<String, LunarBossBar> allBars() {
        return Collections.unmodifiableMap(bars);
    }

    public void tick(Location mobLocation, Collection<? extends Player> worldPlayers) {
        if (bars.isEmpty()) return;
        for (LunarBossBar bar : bars.values()) {
            bar.updateViewers(mobLocation, worldPlayers);
        }
    }
}
