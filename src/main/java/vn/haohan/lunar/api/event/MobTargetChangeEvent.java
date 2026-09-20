package vn.haohan.lunar.api.combat.threat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Objects;
import java.util.Optional;

/**
 * Event fired when an ActiveMob changes its primary combat target.
 * Cancellable: cancelling keeps the previous target.
 */
public final class LunarMobTargetChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveMob mob;
    private final LivingEntity previousTarget;
    private LivingEntity newTarget;
    private final double newTargetThreat;
    private final TargetChangeReason reason;
    private boolean cancelled;

    public LunarMobTargetChangeEvent(
            ActiveMob mob,
            LivingEntity previousTarget,
            LivingEntity newTarget,
            double newTargetThreat,
            TargetChangeReason reason) {
        this.mob = Objects.requireNonNull(mob, "ActiveMob must not be null");
        this.previousTarget = previousTarget;
        this.newTarget = newTarget;
        this.newTargetThreat = newTargetThreat;
        this.reason = reason != null ? reason : TargetChangeReason.CUSTOM;
    }

    public ActiveMob mob() {
        return mob;
    }

    public Optional<LivingEntity> previousTarget() {
        return Optional.ofNullable(previousTarget);
    }

    public Optional<LivingEntity> newTarget() {
        return Optional.ofNullable(newTarget);
    }

    public void setNewTarget(LivingEntity newTarget) {
        this.newTarget = newTarget;
    }

    public double newTargetThreat() {
        return newTargetThreat;
    }

    public TargetChangeReason reason() {
        return reason;
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
