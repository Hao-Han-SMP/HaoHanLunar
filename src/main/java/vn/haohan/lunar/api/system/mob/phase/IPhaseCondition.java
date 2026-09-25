package vn.haohan.lunar.api.system.mob.phase;

import java.util.Objects;

/**
 * Predicate evaluating whether a phase transition condition is satisfied.
 */
@FunctionalInterface
public interface IPhaseCondition {

    boolean matches(PhaseContext context);

    static IPhaseCondition healthLessThanOrEqual(double maxHealthRatio) {
        return context -> context.healthRatio() <= maxHealthRatio;
    }

    static IPhaseCondition aliveTimeGreaterThanOrEqual(long minTicks) {
        return context -> context.aliveTicks() >= minTicks;
    }

    static IPhaseCondition targetCountGreaterThanOrEqual(int minTargets) {
        return context -> context.targetCount() >= minTargets;
    }

    static IPhaseCondition variableEquals(String key, Object expectedValue) {
        Objects.requireNonNull(key, "Variable key must not be null");
        return context -> Objects.equals(context.variables().get(key), expectedValue);
    }

    static IPhaseCondition signalEquals(String expectedSignal) {
        Objects.requireNonNull(expectedSignal, "Signal must not be null");
        return context -> expectedSignal.equalsIgnoreCase(context.signal());
    }
}
