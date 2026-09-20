package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import vn.haohan.lunar.api.system.combat.DamageType;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired when a custom mob takes damage.
 * Cancelling this event prevents damage calculation and health deduction.
 */
public final class LunarMobDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob victim;
    private final LivingEntity source;
    private final DamageType damageType;
    private final DamageCause cause;
    private double damage;
    private boolean cancelled;

    public LunarMobDamageEvent(Mob victim, LivingEntity source, DamageType damageType, DamageCause cause, double damage) {
        this.victim = Objects.requireNonNull(victim, "Victim must not be null");
        this.source = source;
        this.damageType = damageType != null ? damageType : DamageType.PHYSICAL;
        this.cause = cause != null ? cause : DamageCause.ENTITY_ATTACK;
        this.damage = Math.max(0.0, damage);
    }

    /**
     * Returns the custom mob receiving the damage.
     *
     * @return the victim mob
     */
    public Mob victim() {
        return victim;
    }

    /**
     * Returns the attacking entity, or empty if environmental.
     *
     * @return optional containing the damage source entity
     */
    public Optional<LivingEntity> source() {
        return Optional.ofNullable(source);
    }

    /**
     * Returns the classified damage category (e.g. PHYSICAL, MAGIC, TRUE).
     *
     * @return the damage type
     */
    public DamageType damageType() {
        return damageType;
    }

    /**
     * Returns the underlying Bukkit damage cause.
     *
     * @return the damage cause
     */
    public DamageCause cause() {
        return cause;
    }

    /**
     * Returns the current damage amount.
     *
     * @return the damage value
     */
    public double damage() {
        return damage;
    }

    /**
     * Sets the damage amount to apply.
     *
     * @param damage the new damage value (clamped to non-negative)
     */
    public void setDamage(double damage) {
        this.damage = Math.max(0.0, damage);
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
