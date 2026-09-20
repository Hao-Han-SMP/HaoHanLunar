package vn.haohan.lunar.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Objects;

/**
 * Fired when a custom mob transitions between combat phases.
 * Cancelling this event prevents the phase transition.
 */
public final class LunarMobPhaseChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob mob;
    private final int previousPhase;
    private final int newPhase;
    private boolean cancelled;

    public LunarMobPhaseChangeEvent(Mob mob, int previousPhase, int newPhase) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.previousPhase = previousPhase;
        this.newPhase = newPhase;
    }

    /**
     * Returns the mob undergoing phase change.
     *
     * @return the mob instance
     */
    public Mob mob() {
        return mob;
    }

    /**
     * Returns the 1-based index or identifier of the exiting phase.
     *
     * @return the previous phase index
     */
    public int previousPhase() {
        return previousPhase;
    }

    /**
     * Returns the 1-based index or identifier of the entering phase.
     *
     * @return the new phase index
     */
    public int newPhase() {
        return newPhase;
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
