package vn.haohan.lunar.core.system.throttle;

import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.core.system.debug.metrics.TickBudgetWatchdog;

import static org.junit.jupiter.api.Assertions.*;

class TickBudgetWatchdogAndThrottlingTest {

    @Test
    void testTickBudgetWatchdogMetricsAndOverrun() {
        PerformanceMetrics metrics = new PerformanceMetrics(100);
        TickBudgetWatchdog watchdog = new TickBudgetWatchdog(10.0, metrics); // 10ms budget

        assertEquals(10.0, watchdog.budgetMillis());
        assertEquals(0, watchdog.budgetOverrunCount());

        // Simulate 100 subsystem recordings
        for (int i = 1; i <= 100; i++) {
            metrics.record("MOB_AI", i * 100_000L); // 0.1ms to 10.0ms
            metrics.record("SKILL", i * 50_000L);   // 0.05ms to 5.0ms
        }

        assertEquals(100, metrics.sampleCount("MOB_AI"));
        assertEquals(10.0, metrics.calculateMaxMillis("MOB_AI"), 0.01);
        assertEquals(5.05, metrics.calculateAverageMillis("MOB_AI"), 0.01);
        assertTrue(metrics.calculateP95Millis("MOB_AI") >= 9.5);
        assertTrue(metrics.calculateP99Millis("MOB_AI") >= 9.9);

        // Record total tick that overruns the 10ms budget
        metrics.record("TOTAL_TICK", 15_000_000L); // 15ms

        String table = watchdog.formatProfileTable();
        assertTrue(table.contains("MOB_AI"));
        assertTrue(table.contains("SKILL"));
        assertTrue(table.contains("Avg:"));
        assertTrue(table.contains("P95:"));
        assertTrue(table.contains("P99:"));
    }

    @Test
    void testDynamicThrottlingLODTransitions() {
        DynamicThrottlingEngine engine = new DynamicThrottlingEngine();

        // 1. Initial TPS: 20.0 -> LEVEL_0_NORMAL
        engine.updateTPS(20.0);
        assertEquals(ThrottleLevel.LEVEL_0_NORMAL, engine.level());
        assertEquals(1.0, engine.particleReductionRatio());
        assertTrue(engine.shouldTickSpawner(1L));
        assertTrue(engine.shouldTickSpawner(2L));
        assertTrue(engine.shouldTickMobAI(false, 30.0, 1L)); // Distant idle mob still ticks
        assertTrue(engine.shouldTickMobAI(true, 5.0, 1L));

        // 2. TPS drops to 16.5 -> LEVEL_1_MINOR
        engine.updateTPS(16.5);
        assertEquals(ThrottleLevel.LEVEL_1_MINOR, engine.level());
        assertEquals(0.5, engine.particleReductionRatio());

        // Spawners tick every other tick (even ticks)
        assertFalse(engine.shouldTickSpawner(1L));
        assertTrue(engine.shouldTickSpawner(2L));

        // Close mobs (<= 24 blocks) tick every tick
        assertTrue(engine.shouldTickMobAI(false, 15.0, 1L));
        assertTrue(engine.shouldTickMobAI(false, 15.0, 2L));

        // Distant mobs (> 24 blocks) tick only on even ticks
        assertFalse(engine.shouldTickMobAI(false, 30.0, 1L));
        assertTrue(engine.shouldTickMobAI(false, 30.0, 2L));

        // In-combat mobs tick every tick regardless of distance
        assertTrue(engine.shouldTickMobAI(true, 30.0, 1L));

        // 3. TPS drops to 12.0 -> LEVEL_2_CRITICAL
        engine.updateTPS(12.0);
        assertEquals(ThrottleLevel.LEVEL_2_CRITICAL, engine.level());
        assertEquals(0.0, engine.particleReductionRatio());

        // Spawners completely paused
        assertFalse(engine.shouldTickSpawner(1L));
        assertFalse(engine.shouldTickSpawner(2L));

        // In-combat mobs tick every 2 ticks
        assertFalse(engine.shouldTickMobAI(true, 10.0, 1L));
        assertTrue(engine.shouldTickMobAI(true, 10.0, 2L));

        // Idle mobs sleep: tick only every 5 ticks
        assertFalse(engine.shouldTickMobAI(false, 10.0, 1L));
        assertFalse(engine.shouldTickMobAI(false, 10.0, 2L));
        assertFalse(engine.shouldTickMobAI(false, 10.0, 3L));
        assertFalse(engine.shouldTickMobAI(false, 10.0, 4L));
        assertTrue(engine.shouldTickMobAI(false, 10.0, 5L));

        // 4. TPS recovers to 19.5 -> Recovers to LEVEL_0_NORMAL
        engine.updateTPS(19.5);
        assertEquals(ThrottleLevel.LEVEL_0_NORMAL, engine.level());
        assertEquals(1.0, engine.particleReductionRatio());
        assertTrue(engine.shouldTickSpawner(1L));
        assertTrue(engine.shouldTickMobAI(false, 50.0, 1L));
    }
}
