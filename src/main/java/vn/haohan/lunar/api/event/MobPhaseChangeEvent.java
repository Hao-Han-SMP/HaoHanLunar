package vn.haohan.lunar.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Objects;

/**
 * Fired when an Mob transitions between boss/combat phases.
 * Cancellable: cancelling prevents the phase switch.
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

    public Mob mob() {
        return mob;
    }

    public int previousPhase() {
        return previousPhase;
    }

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
