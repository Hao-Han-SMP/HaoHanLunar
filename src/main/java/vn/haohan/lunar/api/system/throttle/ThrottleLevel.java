package vn.haohan.lunar.core.system.throttle;

/**
 * Level of Detail (LOD) throttle levels applied dynamically when server TPS fluctuates.
 */
public enum ThrottleLevel {
    /** Full fidelity (TPS >= 18.0): All AI, Skills, Spawners, and Particles tick every tick. */
    LEVEL_0_NORMAL,

    /** Minor throttle (15.0 <= TPS < 18.0): Distant mobs tick AI every 2 ticks, spawner 50%, particles 50%. */
    LEVEL_1_MINOR,

    /** Critical throttle (TPS < 15.0): Idle mobs sleep AI, particles disabled, spawners paused. */
    LEVEL_2_CRITICAL
}
