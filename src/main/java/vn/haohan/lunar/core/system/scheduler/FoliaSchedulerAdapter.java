package vn.haohan.lunar.core.system.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Folia regionized multi-threaded scheduler adapter.
 * Dispatches entity tasks to entity schedulers and location tasks to region schedulers.
 */
public final class FoliaSchedulerAdapter implements LunarPlatformScheduler {

    private final Plugin plugin;
    private final Set<TaskHandle> activeHandles = ConcurrentHashMap.newKeySet();

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin must not be null");
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public TaskHandle runAtEntity(Entity entity, Consumer<TaskHandle> task) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();

        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(entity);
            Method runMethod = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
                activeHandles.remove(handle);
            };

            Object scheduledTask = runMethod.invoke(entityScheduler, plugin, consumer, (Runnable) () -> activeHandles.remove(handle));
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            // Fallback to async/sync if reflection fails in non-folia test runtime
            return fallbackSync(task, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runAtLocation(Location location, Consumer<TaskHandle> task) {
        Objects.requireNonNull(location, "Location must not be null");
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();

        try {
            Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
            Object regionScheduler = getRegionScheduler.invoke(null);
            Method executeMethod = regionScheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);

            Runnable runnable = () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
                activeHandles.remove(handle);
            };

            executeMethod.invoke(regionScheduler, plugin, location, runnable);
        } catch (Throwable t) {
            return fallbackSync(task, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerAtEntity(Entity entity, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();

        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(entity);
            Method runAtFixedRate = entityScheduler.getClass().getMethod("runAtFixedRate",
                    Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            };

            Object scheduledTask = runAtFixedRate.invoke(entityScheduler, plugin, consumer, null,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            return fallbackSyncTimer(task, initialDelayTicks, periodTicks, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerAtLocation(Location location, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(location, "Location must not be null");
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();

        try {
            Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
            Object regionScheduler = getRegionScheduler.invoke(null);
            Method runAtFixedRate = regionScheduler.getClass().getMethod("runAtFixedRate",
                    Plugin.class, Location.class, Consumer.class, long.class, long.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            };

            Object scheduledTask = runAtFixedRate.invoke(regionScheduler, plugin, location, consumer,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            return fallbackSyncTimer(task, initialDelayTicks, periodTicks, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runSync(Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();
        try {
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object globalScheduler = getGlobalRegionScheduler.invoke(null);
            Method executeMethod = globalScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);

            Runnable runnable = () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
                activeHandles.remove(handle);
            };

            executeMethod.invoke(globalScheduler, plugin, runnable);
        } catch (Throwable t) {
            return fallbackSync(task, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerSync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();
        try {
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Object globalScheduler = getGlobalRegionScheduler.invoke(null);
            Method runAtFixedRate = globalScheduler.getClass().getMethod("runAtFixedRate",
                    Plugin.class, Consumer.class, long.class, long.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            };

            Object scheduledTask = runAtFixedRate.invoke(globalScheduler, plugin, consumer,
                    Math.max(1L, initialDelayTicks), Math.max(1L, periodTicks));
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            return fallbackSyncTimer(task, initialDelayTicks, periodTicks, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runAsync(Consumer<TaskHandle> task) {
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();
        try {
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            Object asyncScheduler = getAsyncScheduler.invoke(null);
            Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
                activeHandles.remove(handle);
            };

            Object scheduledTask = runNow.invoke(asyncScheduler, plugin, consumer);
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            return fallbackAsync(task, handle);
        }

        activeHandles.add(handle);
        return handle;
    }

    @Override
    public TaskHandle runTimerAsync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "Task must not be null");
        FoliaTaskHandle handle = new FoliaTaskHandle();
        try {
            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            Object asyncScheduler = getAsyncScheduler.invoke(null);
            Method runAtFixedRate = asyncScheduler.getClass().getMethod("runAtFixedRate",
                    Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

            Consumer<Object> consumer = foliaTask -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            };

            long initialMillis = Math.max(50L, initialDelayTicks * 50L);
            long periodMillis = Math.max(50L, periodTicks * 50L);
            Object scheduledTask = runAtFixedRate.invoke(asyncScheduler, plugin, consumer,
                    initialMillis, periodMillis, TimeUnit.MILLISECONDS);
            handle.bind(scheduledTask);
        } catch (Throwable t) {
            return fallbackAsyncTimer(task, initialDelayTicks, periodTicks, handle);
        }

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

    private TaskHandle fallbackSync(Consumer<TaskHandle> task, FoliaTaskHandle handle) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            });
        } catch (Throwable ignored) {
            task.accept(handle);
        }
        return handle;
    }

    private TaskHandle fallbackSyncTimer(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks, FoliaTaskHandle handle) {
        try {
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            }, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        } catch (Throwable ignored) {
            task.accept(handle);
        }
        return handle;
    }

    private TaskHandle fallbackAsync(Consumer<TaskHandle> task, FoliaTaskHandle handle) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            });
        } catch (Throwable ignored) {
            Thread.ofVirtual().start(() -> task.accept(handle));
        }
        return handle;
    }

    private TaskHandle fallbackAsyncTimer(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks, FoliaTaskHandle handle) {
        try {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (!handle.isCancelled()) {
                    task.accept(handle);
                }
            }, Math.max(0L, initialDelayTicks), Math.max(1L, periodTicks));
        } catch (Throwable ignored) {
            task.accept(handle);
        }
        return handle;
    }

    private static final class FoliaTaskHandle implements TaskHandle {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Object scheduledTask;

        void bind(Object task) {
            this.scheduledTask = task;
            if (cancelled.get() && task != null) {
                cancelUnderlying(task);
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                if (scheduledTask != null) {
                    cancelUnderlying(scheduledTask);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        private void cancelUnderlying(Object task) {
            try {
                Method cancelMethod = task.getClass().getMethod("cancel");
                cancelMethod.invoke(task);
            } catch (Throwable ignored) {
            }
        }
    }
}
