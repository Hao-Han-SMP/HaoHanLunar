package vn.haohan.lunar.core.system.debug.metrics;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * High-performance execution timing and latency percentile tracker.
 * Computes P95, mean, and max execution times every 100 ticks to detect TPS drops.
 */
public final class PerformanceMetrics {

    private static final int DEFAULT_WINDOW_SIZE = 100;

    private final int windowSize;
    private final Map<String, Queue<Long>> samples = new ConcurrentHashMap<>();

    public PerformanceMetrics(int windowSize) {
        this.windowSize = Math.max(10, windowSize);
    }

    public PerformanceMetrics() {
        this(DEFAULT_WINDOW_SIZE);
    }

    /**
     * Records an execution duration in nanoseconds.
     */
    public void record(String system, long elapsedNanos) {
        if (system == null || elapsedNanos < 0) return;
        Queue<Long> queue = samples.computeIfAbsent(system, k -> new ConcurrentLinkedQueue<>());
        queue.add(elapsedNanos);
        while (queue.size() > windowSize) {
            queue.poll();
        }
    }

    /**
     * Computes the 95th percentile execution time in milliseconds.
     */
    public double calculateP95Millis(String system) {
        Queue<Long> queue = samples.get(system);
        if (queue == null || queue.isEmpty()) return 0.0;

        List<Long> values = new ArrayList<>(queue);
        Collections.sort(values);

        int index = (int) Math.ceil(0.95 * values.size()) - 1;
        index = Math.clamp(index, 0, values.size() - 1);

        return values.get(index) / 1_000_000.0;
    }

    /**
     * Computes the 99th percentile execution time in milliseconds.
     */
    public double calculateP99Millis(String system) {
        Queue<Long> queue = samples.get(system);
        if (queue == null || queue.isEmpty()) return 0.0;

        List<Long> values = new ArrayList<>(queue);
        Collections.sort(values);

        int index = (int) Math.ceil(0.99 * values.size()) - 1;
        index = Math.clamp(index, 0, values.size() - 1);

        return values.get(index) / 1_000_000.0;
    }

    /**
     * Computes the average execution time in milliseconds.
     */
    public double calculateAverageMillis(String system) {
        Queue<Long> queue = samples.get(system);
        if (queue == null || queue.isEmpty()) return 0.0;

        List<Long> values = new ArrayList<>(queue);
        long sum = 0;
        for (long val : values) sum += val;
        return (sum / (double) values.size()) / 1_000_000.0;
    }

    /**
     * Computes the maximum execution time in milliseconds.
     */
    public double calculateMaxMillis(String system) {
        Queue<Long> queue = samples.get(system);
        if (queue == null || queue.isEmpty()) return 0.0;

        long max = 0;
        for (long val : queue) {
            if (val > max) max = val;
        }
        return max / 1_000_000.0;
    }

    /**
     * Checks whether the P95 latency exceeds a warning threshold in milliseconds.
     */
    public boolean isExceedingThreshold(String system, double thresholdMillis) {
        return calculateP95Millis(system) > thresholdMillis;
    }

    /**
     * Returns a summary report for a given system or all tracked systems.
     */
    public String formatReport(String system) {
        if (system != null && !system.equalsIgnoreCase("all")) {
            return String.format("§6[%s] §7P95: §e%.2fms §7| Avg: §a%.2fms §7| Max: §c%.2fms §7(n=%d)",
                    system, calculateP95Millis(system), calculateAverageMillis(system),
                    calculateMaxMillis(system), sampleCount(system));
        }

        StringBuilder sb = new StringBuilder("§6=== Lunar Performance Metrics (P95) ===\n");
        for (String sys : samples.keySet()) {
            sb.append(String.format(" §7• §f%-18s §7P95: §e%.2fms §7Avg: §a%.2fms §7Max: §c%.2fms (n=%d)\n",
                    sys, calculateP95Millis(sys), calculateAverageMillis(sys),
                    calculateMaxMillis(sys), sampleCount(sys)));
        }
        return sb.toString().trim();
    }

    public int sampleCount(String system) {
        Queue<Long> queue = samples.get(system);
        return queue == null ? 0 : queue.size();
    }

    public void clear(String system) {
        if (system == null) samples.clear();
        else samples.remove(system);
    }
}
