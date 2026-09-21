package vn.haohan.lunar.api.system.combat.skill;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Main-thread skill dispatcher backed by one Bukkit repeating task.
 * Callbacks are intentionally run synchronously because they may use Bukkit APIs.
 */
public final class SkillScheduler {

    private final JavaPlugin plugin;
    private final Consumer<String> warningLogger;
    private final long budgetNanos;
    private final Map<Long, ScheduledSkill> scheduled = new java.util.LinkedHashMap<>();
    private final AtomicLong nextTaskId = new AtomicLong();
    private BukkitTask bukkitTask;
    private long currentTick;

    public SkillScheduler(JavaPlugin plugin, Consumer<String> warningLogger, long budgetNanos) {
        this.plugin = plugin;
        this.warningLogger = Objects.requireNonNull(warningLogger, "Warning logger must not be null");
        if (budgetNanos < 0) {
            throw new IllegalArgumentException("Budget must not be negative");
        }
        this.budgetNanos = budgetNanos;
    }

    public SkillScheduler(JavaPlugin plugin, Consumer<String> warningLogger) {
        this(plugin, warningLogger, 2_000_000L);
    }

    /** Starts exactly one central Bukkit task. Must be called from the server main thread. */
    public void start() {
        if (plugin == null) {
            throw new IllegalStateException("A JavaPlugin is required to start the Bukkit scheduler");
        }
        if (bukkitTask == null) {
            bukkitTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public void stop() {
        if (bukkitTask != null) {
            bukkitTask.cancel();
            bukkitTask = null;
        }
        scheduled.clear();
    }

    public long schedule(UUID entityId, long delayTicks, long repeatIntervalTicks,
                         int executions, Runnable callback) {
        Objects.requireNonNull(entityId, "Entity UUID must not be null");
        Objects.requireNonNull(callback, "Skill callback must not be null");
        if (delayTicks < 0 || repeatIntervalTicks < 1 || executions < 1) {
            throw new IllegalArgumentException("Delay must be >= 0, repeat interval >= 1 and executions >= 1");
        }
        long taskId = nextTaskId.incrementAndGet();
        scheduled.put(taskId, new ScheduledSkill(taskId, entityId,
                Math.addExact(currentTick, delayTicks), repeatIntervalTicks, executions, callback));
        return taskId;
    }

    public boolean cancel(long taskId) {
        return scheduled.remove(taskId) != null;
    }

    public int cancelByEntity(UUID entityId) {
        Objects.requireNonNull(entityId, "Entity UUID must not be null");
        int removed = 0;
        Iterator<ScheduledSkill> iterator = scheduled.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().entityId().equals(entityId)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    public int scheduledCount() {
        return scheduled.size();
    }

    public long currentTick() {
        return currentTick;
    }

    /** Executes one dispatcher tick; this method must run on the Bukkit main thread. */
    public void tick() {
        long started = System.nanoTime();
        currentTick++;
        List<ScheduledSkill> due = new ArrayList<>();
        for (ScheduledSkill task : scheduled.values()) {
            if (task.nextTick() <= currentTick) {
                due.add(task);
            }
        }
        for (ScheduledSkill task : due) {
            if (!scheduled.containsKey(task.id())) {
                continue;
            }
            try {
                task.callback().run();
            } catch (Throwable exception) {
                warningLogger.accept("Skill task " + task.id() + " failed: " + exception.getMessage());
            }
            task.executionsRemaining--;
            if (task.executionsRemaining <= 0 || !scheduled.containsKey(task.id())) {
                scheduled.remove(task.id());
            } else {
                task.nextTick = Math.addExact(currentTick, task.repeatIntervalTicks());
            }
        }
        long elapsed = System.nanoTime() - started;
        if (budgetNanos > 0 && elapsed > budgetNanos) {
            warningLogger.accept("Skill scheduler exceeded tick budget: " + elapsed + "ns > " + budgetNanos + "ns");
        }
    }

    private static final class ScheduledSkill {
        private final long id;
        private final UUID entityId;
        private long nextTick;
        private final long repeatIntervalTicks;
        private int executionsRemaining;
        private final Runnable callback;

        private ScheduledSkill(long id, UUID entityId, long nextTick, long repeatIntervalTicks,
                               int executionsRemaining, Runnable callback) {
            this.id = id;
            this.entityId = entityId;
            this.nextTick = nextTick;
            this.repeatIntervalTicks = repeatIntervalTicks;
            this.executionsRemaining = executionsRemaining;
            this.callback = callback;
        }

        private long id() { return id; }
        private UUID entityId() { return entityId; }
        private long nextTick() { return nextTick; }
        private long repeatIntervalTicks() { return repeatIntervalTicks; }
        private Runnable callback() { return callback; }
    }
}
