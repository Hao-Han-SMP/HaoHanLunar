package vn.haohan.lunar.core.system.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Paper/Spigot single-thread scheduler adapter using standard BukkitScheduler.
 */
public final class PaperSchedulerAdapter implements LunarPlatformScheduler {

    private final Plugin plugin;
    private final Set<TaskHandle> activeHandles = ConcurrentHashMap.newKeySet();

    public PaperSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin must not be null");
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Consumer<TaskHandle> task) {
        return runSync(task);
    }

    @Override
    public TaskHandle runAtLocation(Location location, Consumer<TaskHandle> task) {
        return runSync(task);
    }

    @Override
    public TaskHandle runTimerAtEntity(Entity entity, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        return runTimerSync(task, initialDelayTicks, periodTicks);
    }

    @Override
    public TaskHandle runTimerAtLocation(Location location, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        return runTimerSync(task, initialDelayTicks, periodTicks);
    }

    @Override
    public TaskHandle runSync(Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "Task must not be null");
        BukkitTaskHandle handle = new BukkitTaskHandle();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTask(plugin, () -> {
            if (!handle.isCancelled()) {
                task.accept(handle);
            }
            activeHandles.remove(handle);
        });
        handle.bind(bukkitTask);
        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerSync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "Task must not be null");
        BukkitTaskHandle handle = new BukkitTaskHandle();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!handle.isCancelled()) {
                task.accept(handle);
            }
        }, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        handle.bind(bukkitTask);
        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runAsync(Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "Task must not be null");
        BukkitTaskHandle handle = new BukkitTaskHandle();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!handle.isCancelled()) {
                task.accept(handle);
            }
            activeHandles.remove(handle);
        });
        handle.bind(bukkitTask);
        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerAsync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "Task must not be null");
        BukkitTaskHandle handle = new BukkitTaskHandle();
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!handle.isCancelled()) {
                task.accept(handle);
            }
        }, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        handle.bind(bukkitTask);
        activeHandles.add(handle);
        return handle;
    }

    @Override
    public void cancelAll() {
        for (TaskHandle handle : activeHandles) {
            handle.cancel();
        }
        activeHandles.clear();
    }

    private static final class BukkitTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile BukkitTask task;

        void bind(BukkitTask task) {
            this.task = task;
            if (cancelled.get() && task != null) {
                task.cancel();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                if (task != null) {
                    task.cancel();
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || (task != null && task.isCancelled());
        }
    }
}
