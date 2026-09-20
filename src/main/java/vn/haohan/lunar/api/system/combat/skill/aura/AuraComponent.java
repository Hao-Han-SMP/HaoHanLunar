package vn.haohan.lunar.api.system.combat.skill.aura;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Pluggable modular behavior component for an active aura.
 */
public interface AuraComponent {

    /** Invoked immediately when the aura starts. */
    default void onStart(ActiveAura aura) {}

    /** Invoked periodically on each configured tick interval. */
    default void onTick(ActiveAura aura, long currentTick) {}

    /** Invoked when the aura's owner/holder successfully hits a target. */
    default void onHit(ActiveAura aura, LivingEntity target, double damage) {}

    /** Invoked when the aura's owner/holder receives damage. */
    default void onDamaged(ActiveAura aura, Entity attacker, double damage) {}

    /** Invoked when the aura expires naturally or is cancelled. */
    default void onExpire(ActiveAura aura) {}
}
