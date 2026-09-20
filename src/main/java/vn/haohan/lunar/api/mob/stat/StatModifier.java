package vn.haohan.lunar.api.mob.stat;

import java.util.Objects;

/**
 * An individual stat modifier with source identity, mathematical operation, value, and expiration tick.
 */
public record StatModifier(
        String sourceId,
        StatType statType,
        ModifierOperation operation,
        double value,
        long expiresAtTick
) {

    public StatModifier {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(statType, "statType must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
    }

    /**
     * Creates a permanent modifier that never expires.
     */
    public static StatModifier permanent(String sourceId, StatType statType, ModifierOperation operation, double value) {
        return new StatModifier(sourceId, statType, operation, value, -1L);
    }

    /**
     * Creates a timed modifier that expires at the specified tick.
     */
    public static StatModifier timed(String sourceId, StatType statType, ModifierOperation operation, double value, long expiresAtTick) {
        return new StatModifier(sourceId, statType, operation, value, expiresAtTick);
    }

    public boolean isExpired(long currentTick) {
        return expiresAtTick >= 0 && currentTick >= expiresAtTick;
    }
}
