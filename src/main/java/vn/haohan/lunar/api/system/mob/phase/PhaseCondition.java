package vn.haohan.lunar.api.system.mob.phase;

import java.util.Objects;

/**
 * Predicate evaluating whether a phase transition condition is satisfied.
 */
@FunctionalInterface
public interface PhaseCondition {

    boolean matches(PhaseContext context);

    static PhaseCondition healthLessThanOrEqual(double maxHealthRatio) {
        return context -> context.healthRatio() <= maxHealthRatio;
    }

    static PhaseCondition aliveTimeGreaterThanOrEqual(long minTicks) {
        return context -> context.aliveTicks() >= minTicks;
    }

    static PhaseCondition targetCountGreaterThanOrEqual(int minTargets) {
        return context -> context.targetCount() >= minTargets;
    }

    static PhaseCondition variableEquals(String key, Object expectedValue) {
        Objects.requireNonNull(key, "Variable key must not be null");
        return context -> Objects.equals(context.variables().get(key), expectedValue);
    }

    static PhaseCondition signalEquals(String expectedSignal) {
        Objects.requireNonNull(expectedSignal, "Signal must not be null");
        return context -> expectedSignal.equalsIgnoreCase(context.signal());
    }
}
