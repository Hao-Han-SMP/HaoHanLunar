package vn.haohan.lunar.api.system.mob.stat;

import java.util.Map;
import java.util.Objects;

/**
 * An immutable point-in-time calculation of an entity's statistics.
 */
public record StatSnapshot(Map<StatType, Double> values) {

    public StatSnapshot {
        Objects.requireNonNull(values, "Values must not be null");
        values = Map.copyOf(values);
    }

    public double get(StatType type) {
        if (type == null) return 0.0;
        return values.getOrDefault(type, type == StatType.CRIT_DAMAGE ? 1.5 : type.min());
    }

    public double damage() {
        return get(StatType.DAMAGE);
    }

    public double armor() {
        return get(StatType.ARMOR);
    }

    public double critChance() {
        return get(StatType.CRIT_CHANCE);
    }

    public double critDamage() {
        return get(StatType.CRIT_DAMAGE);
    }

    public double lifesteal() {
        return get(StatType.LIFESTEAL);
    }

    public double cooldownReduction() {
        return get(StatType.COOLDOWN_REDUCTION);
    }

    public double luck() {
        return get(StatType.LUCK);
    }

    public double knockbackResistance() {
        return get(StatType.KNOCKBACK_RESISTANCE);
    }
}
