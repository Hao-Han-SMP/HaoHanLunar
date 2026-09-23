package vn.haohan.lunar.api.system.mob.phase;

import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Context passed to phase conditions to evaluate phase eligibility.
 */
public record PhaseContext(
        ActiveMob mob,
        double health,
        double maxHealth,
        long currentTick,
        long aliveTicks,
        int targetCount,
        Map<String, Object> variables,
        String signal
) {
    public PhaseContext {
        Objects.requireNonNull(mob, "ActiveMob must not be null");
        variables = variables == null ? Map.of() : Collections.unmodifiableMap(variables);
    }

    public static PhaseContext ofHealth(ActiveMob mob, double health, double maxHealth, long currentTick) {
        return new PhaseContext(mob, health, maxHealth, currentTick, 0L, 0, Map.of(), null);
    }

    public double healthRatio() {
        if (!Double.isFinite(health) || !Double.isFinite(maxHealth) || maxHealth <= 0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, health / maxHealth));
    }
}
