package vn.haohan.lunar.api.system.combat;

import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable table of damage multipliers based on DamageCause and attacker EntityType.
 * Maps directly to MythicMobs DamageModifiers and EntityDamageModifiers configurations.
 */
public final class DamageModifierTable {

    private final Map<DamageCause, Double> causeModifiers;
    private final Map<EntityType, Double> entityModifiers;

    public DamageModifierTable(Map<DamageCause, Double> causeModifiers, Map<EntityType, Double> entityModifiers) {
        this.causeModifiers = causeModifiers != null ? Map.copyOf(causeModifiers) : Map.of();
        this.entityModifiers = entityModifiers != null ? Map.copyOf(entityModifiers) : Map.of();
    }

    public static DamageModifierTable empty() {
        return new DamageModifierTable(Map.of(), Map.of());
    }

    public double getCauseModifier(DamageCause cause) {
        if (cause == null) return 1.0;
        return causeModifiers.getOrDefault(cause, 1.0);
    }

    public double getEntityModifier(EntityType entityType) {
        if (entityType == null) return 1.0;
        return entityModifiers.getOrDefault(entityType, 1.0);
    }

    /**
     * Calculates combined multiplier for the given cause and attacker entity type.
     * Result is clamped to non-negative (>= 0.0).
     */
    public double calculateMultiplier(DamageCause cause, EntityType attackerType) {
        double causeMod = getCauseModifier(cause);
        double entityMod = attackerType != null ? getEntityModifier(attackerType) : 1.0;
        return Math.max(0.0, causeMod * entityMod);
    }

    /**
     * Applies this modifier table to a DamageContext.
     */
    public void apply(DamageContext context) {
        Objects.requireNonNull(context, "DamageContext must not be null");
        EntityType attackerType = context.attacker() != null ? context.attacker().getType() : null;
        double multiplier = calculateMultiplier(context.cause(), attackerType);
        if (multiplier != 1.0) {
            context.multiplyDamage(multiplier);
        }
    }

    public Map<DamageCause, Double> causeModifiers() {
        return causeModifiers;
    }

    public Map<EntityType, Double> entityModifiers() {
        return entityModifiers;
    }
}
