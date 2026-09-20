package vn.haohan.lunar.api.system.combat;

import java.util.Objects;
import java.util.Optional;

/**
 * Result record returned by combat damage executions.
 */
public record DamageResult(
        boolean executed,
        boolean cancelled,
        double appliedDamage,
        String reason,
        DamageContext context
) {
    public DamageResult {
        Objects.requireNonNull(context, "DamageContext must not be null");
    }

    public static DamageResult success(DamageContext context, double appliedDamage) {
        return new DamageResult(true, false, appliedDamage, null, context);
    }

    public static DamageResult cancelled(DamageContext context, String reason) {
        return new DamageResult(false, true, 0.0, reason, context);
    }

    public Optional<String> cancellationReason() {
        return Optional.ofNullable(reason);
    }
}
