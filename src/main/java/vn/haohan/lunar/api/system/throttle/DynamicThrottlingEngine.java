package vn.haohan.lunar.core.system.throttle;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamic self-throttling engine that monitors server TPS and dynamically
 * adjusts Level of Detail (LOD) for Mob AI, Aura particles, and Spawner checks.
 */
public final class DynamicThrottlingEngine {

    private final AtomicReference<ThrottleLevel> currentLevel = new AtomicReference<>(ThrottleLevel.LEVEL_0_NORMAL);
    private volatile double lastTps = 20.0;

    public ThrottleLevel level() {
        return currentLevel.get();
    }

    public double lastTps() {
        return lastTps;
    }

    /**
     * Updates current server TPS and recalibrates throttle level.
     */
    public void updateTPS(double tps) {
        this.lastTps = tps;
        if (tps >= 18.0) {
            currentLevel.set(ThrottleLevel.LEVEL_0_NORMAL);
        } else if (tps >= 15.0) {
            currentLevel.set(ThrottleLevel.LEVEL_1_MINOR);
        } else {
            currentLevel.set(ThrottleLevel.LEVEL_2_CRITICAL);
        }
    }

    /**
     * Determines whether Mob AI should be processed this tick based on combat state and proximity.
     */
    public boolean shouldTickMobAI(boolean inCombat, double distanceToPlayer, long currentTick) {
        ThrottleLevel lvl = currentLevel.get();
        return switch (lvl) {
            case LEVEL_0_NORMAL -> true;
            case LEVEL_1_MINOR -> inCombat || distanceToPlayer <= 24.0 || (currentTick % 2 == 0);
            case LEVEL_2_CRITICAL -> inCombat ? (currentTick % 2 == 0) : (currentTick % 5 == 0);
        };
    }

    /**
     * Determines whether spawner countdown / spawn checks should execute this tick.
     */
    public boolean shouldTickSpawner(long currentTick) {
        ThrottleLevel lvl = currentLevel.get();
        return switch (lvl) {
            case LEVEL_0_NORMAL -> true;
            case LEVEL_1_MINOR -> (currentTick % 2 == 0);
            case LEVEL_2_CRITICAL -> false;
        };
    }

    /**
     * Returns particle density ratio (1.0 = 100%, 0.5 = 50%, 0.0 = 0% decorative particles).
     */
    public double particleReductionRatio() {
        ThrottleLevel lvl = currentLevel.get();
        return switch (lvl) {
            case LEVEL_0_NORMAL -> 1.0;
            case LEVEL_1_MINOR -> 0.5;
            case LEVEL_2_CRITICAL -> 0.0;
        };
    }
}
