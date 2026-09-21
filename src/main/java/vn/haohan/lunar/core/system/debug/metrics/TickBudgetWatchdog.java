package vn.haohan.lunar.core.system.debug.metrics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors server tick time usage across key subsystems (Mob AI, Skills, Auras, Spawners).
 * Detects spikes exceeding the allowed tick budget (default: 15.0ms) and provides
 * formatted performance profiling tables for /lunarmob profile.
 */
public final class TickBudgetWatchdog {

    public static final double DEFAULT_BUDGET_MILLIS = 15.0;

    private final double budgetMillis;
    private final PerformanceMetrics metrics;
    private final Map<String, Long> activeStarts = new ConcurrentHashMap<>();
    private final AtomicLong tickStartNanos = new AtomicLong();
    private final AtomicInteger budgetOverrunCount = new AtomicInteger(0);

    public TickBudgetWatchdog(double budgetMillis, PerformanceMetrics metrics) {
        this.budgetMillis = Math.max(1.0, budgetMillis);
        this.metrics = metrics != null ? metrics : new PerformanceMetrics();
    }

    public TickBudgetWatchdog() {
        this(DEFAULT_BUDGET_MILLIS, new PerformanceMetrics());
    }

    public double budgetMillis() {
        return budgetMillis;
    }

    public PerformanceMetrics metrics() {
        return metrics;
    }

    public int budgetOverrunCount() {
        return budgetOverrunCount.get();
    }

    public void startTick() {
        tickStartNanos.set(System.nanoTime());
    }

    public double endTick() {
        long start = tickStartNanos.get();
        if (start <= 0) return 0.0;
        long elapsed = System.nanoTime() - start;
        double elapsedMs = elapsed / 1_000_000.0;
        metrics.record("TOTAL_TICK", elapsed);
        if (elapsedMs > budgetMillis) {
            budgetOverrunCount.incrementAndGet();
        }
        return elapsedMs;
    }

    public void startSubsystem(String subsystem) {
        if (subsystem != null) {
            activeStarts.put(subsystem, System.nanoTime());
        }
    }

    public double stopSubsystem(String subsystem) {
        if (subsystem == null) return 0.0;
        Long start = activeStarts.remove(subsystem);
        if (start == null) return 0.0;
        long elapsed = System.nanoTime() - start;
        metrics.record(subsystem, elapsed);
        return elapsed / 1_000_000.0;
    }

    /**
     * Formats a clean profile report table for /lunarmob profile.
     */
    public String formatProfileTable() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Lunar Performance Profile (Budget: %.1fms) ===\n", budgetMillis));
        List<String> systems = List.of("MOB_AI", "SKILL", "AURA", "SPAWNER", "TOTAL_TICK");
        for (String sys : systems) {
            if (metrics.sampleCount(sys) > 0) {
                sb.append(String.format(" • %-12s Avg: %5.2fms | P95: %5.2fms | P99: %5.2fms | Max: %5.2fms (n=%d)\n",
                        sys,
                        metrics.calculateAverageMillis(sys),
                        metrics.calculateP95Millis(sys),
                        metrics.calculateP99Millis(sys),
                        metrics.calculateMaxMillis(sys),
                        metrics.sampleCount(sys)));
            }
        }
        if (budgetOverrunCount.get() > 0) {
            sb.append(String.format(" [WARNING] %d tick budget overruns detected (>%.1fms)!\n",
                    budgetOverrunCount.get(), budgetMillis));
        }
        return sb.toString().trim();
    }
}
