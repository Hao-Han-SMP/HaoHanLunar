package vn.haohan.lunar.api.system.mob.scaling;

/**
 * Result metrics calculated by {@link DynamicScalingService}.
 */
public record DynamicScalingResult(
        int rawPlayerCount,
        int effectivePlayers,
        double healthMultiplier,
        double damageMultiplier,
        double cooldownReduction
) {
    public static final DynamicScalingResult UNCHANGED =
            new DynamicScalingResult(1, 1, 1.0, 1.0, 0.0);
}
