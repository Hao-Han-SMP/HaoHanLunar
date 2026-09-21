package vn.haohan.lunar.core.system.debug.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMetricsAndProfilingTest {

    private PerformanceMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new PerformanceMetrics(100);
    }

    @Test
    void testBasicPercentileCalculations() {
        // Record samples: 1ms to 100ms
        for (int i = 1; i <= 100; i++) {
            metrics.record("test_sys", i * 1_000_000L); // nanoseconds
        }

        assertEquals(100, metrics.sampleCount("test_sys"));

        double p95 = metrics.calculateP95Millis("test_sys");
        double p99 = metrics.calculateP99Millis("test_sys");
        double avg = metrics.calculateAverageMillis("test_sys");
        double max = metrics.calculateMaxMillis("test_sys");

        assertTrue(p95 >= 94.0 && p95 <= 96.0, "P95 expected ~95ms, was: " + p95);
        assertTrue(p99 >= 98.0 && p99 <= 100.0, "P99 expected ~99ms, was: " + p99);
        assertEquals(50.5, avg, 0.5, "Average expected ~50.5ms");
        assertEquals(100.0, max, 0.001, "Max expected 100.0ms");

        assertTrue(metrics.isExceedingThreshold("test_sys", 50.0));
        assertFalse(metrics.isExceedingThreshold("test_sys", 150.0));
    }

    @Test
    void testSlidingWindowBound() {
        PerformanceMetrics bounded = new PerformanceMetrics(20);

        for (int i = 0; i < 50; i++) {
            bounded.record("bounded_sys", i * 1_000_000L);
        }

        // Bounded window should not exceed window size
        assertTrue(bounded.sampleCount("bounded_sys") <= 20);
    }

    @Test
    void testConcurrentHighThroughputRecording() throws InterruptedException {
        int threads = 8;
        int recordsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        for (int t = 0; t < threads; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    String sys = (threadId % 2 == 0) ? "mob_manager" : "projectiles";
                    for (int i = 0; i < recordsPerThread; i++) {
                        metrics.record(sys, 500_000L + (i % 100) * 10_000L);
                    }
                } catch (Throwable t1) {
                    errorOccurred.set(true);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        assertFalse(errorOccurred.get(), "Thread safety must be maintained under high concurrent load");

        String report = metrics.formatReport("all");
        assertNotNull(report);
        assertTrue(report.contains("mob_manager"));
        assertTrue(report.contains("projectiles"));

        executor.shutdown();
    }
}
