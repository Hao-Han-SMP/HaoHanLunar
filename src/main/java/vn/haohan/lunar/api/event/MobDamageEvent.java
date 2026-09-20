package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import vn.haohan.lunar.api.combat.DamageType;
import vn.haohan.lunar.api.mob.Mob;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired when an Mob takes damage.
 * Cancellable: cancelling negates the incoming damage.
 * Allows inspecting and modifying the final damage amount before health deduction.
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

    public Mob victim() {
        return victim;
    }

    public Optional<LivingEntity> source() {
        return Optional.ofNullable(source);
    }

    public DamageType damageType() {
        return damageType;
    }

    public DamageCause cause() {
        return cause;
    }

    public double damage() {
        return damage;
    }

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
