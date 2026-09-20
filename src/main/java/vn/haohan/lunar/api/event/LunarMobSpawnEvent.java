package vn.haohan.lunar.api.event;

import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Objects;

/**
 * Fired when a custom mob is spawned and registered into a world.
 * Cancelling this event aborts the registration and removes the underlying entity.
 */
public final class LunarMobSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob mob;
    private final Location location;
    private final String spawnInstanceId;
    private boolean cancelled;

    public LunarMobSpawnEvent(Mob mob, Location location, String spawnInstanceId) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.location = Objects.requireNonNull(location, "Location must not be null");
        this.spawnInstanceId = spawnInstanceId;
    }

    /**
     * Returns the newly spawned custom mob.
     *
     * @return the mob instance
     */
    public Mob mob() {
        return mob;
    }

    /**
     * Returns a copy of the spawn location.
     *
     * @return cloned spawn location
     */
    public Location location() {
        return location.clone();
    }

    /**
     * Returns the unique spawn instance identifier, or null if spawned manually.
     *
     * @return spawn instance ID
     */
    public String spawnInstanceId() {
        return spawnInstanceId;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
