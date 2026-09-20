package vn.haohan.lunar.core.system.debug;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.core.system.command.LunarMobCommand;
import vn.haohan.lunar.core.system.config.ConfigValidationReport;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.core.system.debug.trace.SkillTracer;
import vn.haohan.lunar.core.system.debug.validator.ConfigValidationService;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.mob.MobDefinitionRegistry;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DebugToolingAndMetricsTest {

    @TempDir
    Path tempConfigDir;

    @Test
    void testConfigValidationServiceValidAndCorruptFiles() throws IOException {
        Path mobsDir = tempConfigDir.resolve("mobs");
        Files.createDirectories(mobsDir);

        // 1. Valid file
        Files.writeString(mobsDir.resolve("valid_mob.yml"),
                "id: valid_mob\nentity_type: ZOMBIE\nhealth: 50.0\n");

        // 2. Missing ID
        Files.writeString(mobsDir.resolve("bad_id.yml"),
                "entity_type: SKELETON\n");

        // 3. Syntax Error (broken yaml indentation/tabs)
        Files.writeString(mobsDir.resolve("syntax_error.yml"),
                "id: broken\n  something:\n   tab\tindented: oops\n");

        ConfigValidationService validator = new ConfigValidationService();
        ConfigValidationService.ValidationResult result = validator.validate(tempConfigDir, "mobs");

        assertEquals(3, result.totalFiles());
        assertEquals(1, result.validFiles().size());
        assertTrue(result.validFiles().contains("valid_mob.yml"));
        assertEquals(2, result.errors().size());

        String summary = result.formatSummary("mobs");
        assertTrue(summary.contains("=== Lunar Validation Report: MOBS ==="));
        assertTrue(summary.contains("Total files scanned: §f3"));
        assertTrue(summary.contains("Valid files: §f1"));
        assertTrue(summary.contains("Errors: §f2"));
    }

    @Test
    void testSkillTracerRecordingAndTimeout() throws InterruptedException {
        // Fast 50ms timeout for testing expiration
        SkillTracer tracer = new SkillTracer(50L);
        UUID mobId = UUID.randomUUID();

        assertFalse(tracer.isTracing(mobId));
        tracer.enableTrace(mobId);
        assertTrue(tracer.isTracing(mobId));
        assertEquals(1, tracer.activeSessionCount());

        // Record a trace entry
        tracer.record(mobId, "moon_strike", "ON_COMBAT_TICK",
                List.of("health < 50%"), List.of("target_in_range"),
                1, 2_500_000L, 120.0);

        List<SkillTracer.TraceEntry> logs = tracer.getLogs(mobId);
        assertEquals(1, logs.size());
        SkillTracer.TraceEntry entry = logs.getFirst();
        assertEquals("moon_strike", entry.skillId());
        assertEquals("ON_COMBAT_TICK", entry.trigger());
        assertEquals(1, entry.targetCount());
        assertEquals(120.0, entry.finalDamage());
        assertTrue(entry.format().contains("[TRACE] moon_strike"));

        // Wait for session to expire (> 50ms)
        Thread.sleep(60L);

        assertFalse(tracer.isTracing(mobId));
        assertEquals(0, tracer.activeSessionCount());
    }

    @Test
    void testPerformanceMetricsP95AndThreshold() {
        PerformanceMetrics metrics = new PerformanceMetrics(100);

        // Record 100 durations: 1ms to 100ms
        for (int i = 1; i <= 100; i++) {
            metrics.record("SkillScheduler", i * 1_000_000L);
        }

        assertEquals(100, metrics.sampleCount("SkillScheduler"));

        // P95 of 1..100 is 95.0ms
        double p95 = metrics.calculateP95Millis("SkillScheduler");
        assertEquals(95.0, p95, 0.01);

        double avg = metrics.calculateAverageMillis("SkillScheduler");
        assertEquals(50.5, avg, 0.01);

        double max = metrics.calculateMaxMillis("SkillScheduler");
        assertEquals(100.0, max, 0.01);

        // Check threshold
        assertTrue(metrics.isExceedingThreshold("SkillScheduler", 50.0));
        assertFalse(metrics.isExceedingThreshold("SkillScheduler", 96.0));

        String report = metrics.formatReport("SkillScheduler");
        assertTrue(report.contains("SkillScheduler"));
        assertTrue(report.contains("P95: §e95.00ms"));
    }

    @Test
    void testLunarMobCommandDebugSubcommands() throws IOException {
        Path mobsDir = tempConfigDir.resolve("mobs");
        Files.createDirectories(mobsDir);
        Files.writeString(mobsDir.resolve("warden.yml"), "id: warden\ntype: IRON_GOLEM\n");

        ConfigValidationService validator = new ConfigValidationService();
        SkillTracer tracer = new SkillTracer();
        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.record("SkillScheduler", 5_000_000L);

        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        LunarMobCommand command = new LunarMobCommand(registry, new LunarMobManager(),
                (player, mob) -> true, () -> new ConfigValidationReport(List.of()),
                (mob, signal) -> {}, validator, tracer, metrics, tempConfigDir);

        Sender sender = new Sender(true);

        // 1. /lunarmob validate mobs
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"validate", "mobs"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Lunar Validation Report")));

        // 2. /lunarmob trace <uuid> on
        UUID testMobId = UUID.randomUUID();
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"trace", testMobId.toString(), "on"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("ENABLED")));
        assertTrue(tracer.isTracing(testMobId));

        // 3. /lunarmob trace <uuid> off
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"trace", testMobId.toString(), "off"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("DISABLED")));
        assertFalse(tracer.isTracing(testMobId));

        // 4. /lunarmob metrics
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"metrics"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Performance Metrics")));
    }

    private static final class Sender {
        private final List<String> messages = new ArrayList<>();
        private final CommandSender proxy;

        private Sender(boolean permission) {
            proxy = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class}, (p, method, args) -> {
                        if (method.getName().equals("hasPermission")) return permission;
                        if (method.getName().equals("sendMessage")) {
                            messages.add(String.valueOf(args[0]));
                            return null;
                        }
                        return null;
                    });
        }
    }
}
