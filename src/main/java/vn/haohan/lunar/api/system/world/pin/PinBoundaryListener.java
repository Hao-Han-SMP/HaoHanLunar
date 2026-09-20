package vn.haohan.lunar.api.world.pin;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listens to player movement to trigger onEnterBounds and onExitBounds pin arena events.
 * Highly optimized: early-exits when player has not moved across block coordinates.
 */
public final class PinBoundaryListener implements Listener {

    @FunctionalInterface
    public interface BoundaryEnterCallback {
        void onEnter(Player player, PinRegion region);
    }

    @FunctionalInterface
    public interface BoundaryExitCallback {
        void onExit(Player player, PinRegion region);
    }

    private final PinManager pinManager;
    private final List<BoundaryEnterCallback> enterCallbacks = new CopyOnWriteArrayList<>();
    private final List<BoundaryExitCallback> exitCallbacks = new CopyOnWriteArrayList<>();

    public PinBoundaryListener() {
        this(PinManager.get());
    }

    public PinBoundaryListener(PinManager pinManager) {
        this.pinManager = Objects.requireNonNull(pinManager, "PinManager must not be null");
    }

    public void addEnterCallback(BoundaryEnterCallback callback) {
        if (callback != null) enterCallbacks.add(callback);
    }

    public void addExitCallback(BoundaryExitCallback callback) {
        if (callback != null) exitCallbacks.add(callback);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Optimization: only process when block coordinate changes
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        processBoundaryChange(event.getPlayer(), from, to);
    }

    public void processBoundaryChange(Player player, Location from, Location to) {
        if (player == null || from == null || to == null) return;

        List<PinRegion> fromRegions = pinManager.getRegionsContaining(from);
        List<PinRegion> toRegions = pinManager.getRegionsContaining(to);

        Set<String> fromNames = new HashSet<>();
        for (PinRegion r : fromRegions) {
            fromNames.add(r.name().toLowerCase(java.util.Locale.ROOT));
        }

        Set<String> toNames = new HashSet<>();
        for (PinRegion r : toRegions) {
            toNames.add(r.name().toLowerCase(java.util.Locale.ROOT));
        }

        // Check for enter: in `to` but not in `from`
        for (PinRegion r : toRegions) {
            if (!fromNames.contains(r.name().toLowerCase(java.util.Locale.ROOT))) {
                for (BoundaryEnterCallback callback : enterCallbacks) {
                    try {
                        callback.onEnter(player, r);
                    } catch (Throwable ignored) {}
                }
            }
        }

        // Check for exit: in `from` but not in `to`
        for (PinRegion r : fromRegions) {
            if (!toNames.contains(r.name().toLowerCase(java.util.Locale.ROOT))) {
                for (BoundaryExitCallback callback : exitCallbacks) {
                    try {
                        callback.onExit(player, r);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }
}
