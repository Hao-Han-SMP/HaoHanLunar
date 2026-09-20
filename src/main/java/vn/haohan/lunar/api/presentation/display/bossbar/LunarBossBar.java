package vn.haohan.lunar.api.presentation.display.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encapsulates an Adventure BossBar instance with dynamic distance-based viewer tracking.
 */
public final class LunarBossBar {

    private final String id;
    private final BossBar bar;
    private double range;
    private double rangeSquared;
    private final Set<Player> currentViewers = ConcurrentHashMap.newKeySet();

    public LunarBossBar(String id, Component title, float progress, BossBar.Color color, BossBar.Overlay overlay, double range) {
        this.id = Objects.requireNonNull(id, "BossBar id must not be null");
        float clampedProgress = Math.clamp(progress, 0.0f, 1.0f);
        this.bar = BossBar.bossBar(
                title != null ? title : Component.text(id),
                clampedProgress,
                color != null ? color : BossBar.Color.PURPLE,
                overlay != null ? overlay : BossBar.Overlay.PROGRESS
        );
        setRange(range);
    }

    public String id() {
        return id;
    }

    public BossBar adventureBar() {
        return bar;
    }

    public double range() {
        return range;
    }

    public void setRange(double range) {
        this.range = Math.max(0.0, range);
        this.rangeSquared = this.range * this.range;
    }

    public float progress() {
        return bar.progress();
    }

    public void setProgress(float progress) {
        bar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
    }

    public void setTitle(Component title) {
        if (title != null) {
            bar.name(title);
        }
    }

    public void setColor(BossBar.Color color) {
        if (color != null) {
            bar.color(color);
        }
    }

    public void setOverlay(BossBar.Overlay overlay) {
        if (overlay != null) {
            bar.overlay(overlay);
        }
    }

    public Set<Player> currentViewers() {
        return Collections.unmodifiableSet(currentViewers);
    }

    /**
     * Updates visibility for players in the world according to distance from the mob.
     */
    public void updateViewers(Location mobLocation, Collection<? extends Player> players) {
        if (mobLocation == null || mobLocation.getWorld() == null || players == null) {
            removeAll();
            return;
        }

        Set<Player> nearby = new HashSet<>();
        for (Player player : players) {
            if (player == null || !player.isValid() || player.isDead()) continue;
            if (!player.getWorld().equals(mobLocation.getWorld())) continue;

            if (rangeSquared <= 0 || player.getLocation().distanceSquared(mobLocation) <= rangeSquared) {
                nearby.add(player);
                if (currentViewers.add(player)) {
                    player.showBossBar(bar);
                }
            }
        }

        // Remove players who walked out of range
        currentViewers.removeIf(player -> {
            if (!nearby.contains(player)) {
                player.hideBossBar(bar);
                return true;
            }
            return false;
        });
    }

    /**
     * Removes all viewers and hides the bar.
     */
    public void removeAll() {
        for (Player player : currentViewers) {
            try {
                player.hideBossBar(bar);
            } catch (Throwable ignored) {
            }
        }
        currentViewers.clear();
    }
}
