package vn.haohan.lunar.core.system.debug.trace;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debug tracer for monitoring skill execution flow on active mobs.
 * Automatically deactivates and prunes trace sessions after 60 seconds.
 */
public final class SkillTracer {

    public record TraceEntry(
            long timestampMillis,
            String skillId,
            String trigger,
            List<String> passedConditions,
            List<String> failedConditions,
            int targetCount,
            long elapsedNanos,
            double finalDamage
    ) {
        public String format() {
            double elapsedMs = elapsedNanos / 1_000_000.0;
            return String.format("[TRACE] %s trigger=%s targets=%d time=%.2fms dmg=%.1f | pass=%s fail=%s",
                    skillId, trigger, targetCount, elapsedMs, finalDamage,
                    passedConditions, failedConditions);
        }
    }

    private static final long DEFAULT_TRACE_TIMEOUT_MS = 60_000L;

    private final Map<UUID, Long> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<TraceEntry>> traceLogs = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public SkillTracer(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis > 0 ? timeoutMillis : DEFAULT_TRACE_TIMEOUT_MS;
    }

    public SkillTracer() {
        this(DEFAULT_TRACE_TIMEOUT_MS);
    }

    /**
     * Activates trace mode for the given mob UUID.
     */
    public void enableTrace(UUID mobId) {
        if (mobId == null) return;
        pruneExpired();
        activeSessions.put(mobId, System.currentTimeMillis() + timeoutMillis);
        traceLogs.computeIfAbsent(mobId, k -> Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Deactivates trace mode for the given mob UUID.
     */
    public void disableTrace(UUID mobId) {
        if (mobId == null) return;
        activeSessions.remove(mobId);
        traceLogs.remove(mobId);
    }

    /**
     * Checks if tracing is currently enabled and unexpired for the mob.
     */
    public boolean isTracing(UUID mobId) {
        if (mobId == null) return false;
        Long expiry = activeSessions.get(mobId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            disableTrace(mobId);
            return false;
        }
        return true;
    }

    /**
     * Records a trace event if the mob is currently actively traced.
     */
    public void record(UUID mobId, String skillId, String trigger,
                       List<String> passed, List<String> failed,
                       int targetCount, long elapsedNanos, double finalDamage) {
        if (!isTracing(mobId)) return;

        List<TraceEntry> logs = traceLogs.get(mobId);
        if (logs != null) {
            logs.add(new TraceEntry(
                    System.currentTimeMillis(),
                    skillId != null ? skillId : "unknown",
                    trigger != null ? trigger : "unknown",
                    passed != null ? List.copyOf(passed) : List.of(),
                    failed != null ? List.copyOf(failed) : List.of(),
                    targetCount,
                    elapsedNanos,
                    finalDamage
            ));
        }
    }

    /**
     * Gets an immutable snapshot of all recorded traces for this mob.
     */
    public List<TraceEntry> getLogs(UUID mobId) {
        List<TraceEntry> logs = traceLogs.get(mobId);
        if (logs == null) return List.of();
        synchronized (logs) {
            return List.copyOf(logs);
        }
    }

    /**
     * Cleans up expired tracing sessions.
     */
    public void pruneExpired() {
        long now = System.currentTimeMillis();
        activeSessions.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                traceLogs.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public int activeSessionCount() {
        pruneExpired();
        return activeSessions.size();
    }
}
