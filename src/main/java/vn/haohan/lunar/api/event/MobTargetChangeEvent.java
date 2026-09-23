package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.system.combat.threat.TargetChangeReason;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired when an active mob changes its primary combat target.
 * Cancelling this event prevents the target update and retains the previous target.
 */
public final class MobTargetChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ActiveMob mob;
    private final LivingEntity previousTarget;
    private LivingEntity newTarget;
    private final double newTargetThreat;
    private final TargetChangeReason reason;
    private boolean cancelled;

    public MobTargetChangeEvent(
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

    /**
     * Returns the active mob changing targets.
     *
     * @return the mob instance
     */
    public ActiveMob mob() {
        return mob;
    }

    /**
     * Returns the target entity being replaced, if one existed.
     *
     * @return optional containing the previous target
     */
    public Optional<LivingEntity> previousTarget() {
        return Optional.ofNullable(previousTarget);
    }

    /**
     * Returns the newly selected target entity, if present.
     *
     * @return optional containing the new target
     */
    public Optional<LivingEntity> newTarget() {
        return Optional.ofNullable(newTarget);
    }

    /**
     * Overrides the new target entity.
     *
     * @param newTarget the entity to set as target
     */
    public void setNewTarget(LivingEntity newTarget) {
        this.newTarget = newTarget;
    }

    /**
     * Returns the threat score of the new target.
     *
     * @return threat points accumulated by new target
     */
    public double newTargetThreat() {
        return newTargetThreat;
    }

    /**
     * Returns the contextual reason triggering this target change (e.g. THREAT_THRESHOLD, TAUNT).
     *
     * @return target change reason
     */
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
