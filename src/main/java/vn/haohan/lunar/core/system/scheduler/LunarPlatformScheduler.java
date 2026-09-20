package vn.haohan.lunar.core.system.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

/**
 * Cross-platform scheduler seam abstracting Paper BukkitScheduler and Folia RegionizedScheduler.
 */
public interface LunarPlatformScheduler {

    /**
     * @return true if running under a Folia-based regionized server environment
     */
    boolean isFolia();

    /**
     * Executes a task on the thread owning the specified entity.
     */
    TaskHandle runAtEntity(Entity entity, Consumer<TaskHandle> task);

    /**
     * Executes a task on the thread owning the specified spatial region location.
     */
    TaskHandle runAtLocation(Location location, Consumer<TaskHandle> task);

    /**
     * Schedules a repeating task on the thread owning the specified entity.
     */
    TaskHandle runTimerAtEntity(Entity entity, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Schedules a repeating task on the thread owning the specified spatial region location.
     */
    TaskHandle runTimerAtLocation(Location location, Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Executes a synchronous task on the server main / global thread.
     */
    TaskHandle runSync(Consumer<TaskHandle> task);

    /**
     * Schedules a repeating synchronous task on the server main / global thread.
     */
    TaskHandle runTimerSync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Executes a task asynchronously off the main thread.
     */
    TaskHandle runAsync(Consumer<TaskHandle> task);

    /**
     * Schedules a repeating asynchronous task off the main thread.
     */
    TaskHandle runTimerAsync(Consumer<TaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Cancels all tasks owned by this scheduler.
     */
    void cancelAll();
}
