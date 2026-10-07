package vn.haohan.engine.api.system.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

/**
 * Cross-platform scheduler seam abstracting Paper BukkitScheduler and Folia RegionizedScheduler.
 */
public interface ILunarPlatformScheduler {

    /**
     * @return true if running under a Folia-based regionized server environment
     */
    boolean isFolia();

    /**
     * Executes a task on the thread owning the specified entity.
     */
    ITaskHandle runAtEntity(Entity entity, Consumer<ITaskHandle> task);

    /**
     * Executes a task on the thread owning the specified spatial region location.
     */
    ITaskHandle runAtLocation(Location location, Consumer<ITaskHandle> task);

    /**
     * Schedules a repeating task on the thread owning the specified entity.
     */
    ITaskHandle runTimerAtEntity(Entity entity, Consumer<ITaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Schedules a repeating task on the thread owning the specified spatial region location.
     */
    ITaskHandle runTimerAtLocation(Location location, Consumer<ITaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Executes a synchronous task on the server main / global thread.
     */
    ITaskHandle runSync(Consumer<ITaskHandle> task);

    /**
     * Schedules a repeating synchronous task on the server main / global thread.
     */
    ITaskHandle runTimerSync(Consumer<ITaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Executes a task asynchronously off the main thread.
     */
    ITaskHandle runAsync(Consumer<ITaskHandle> task);

    /**
     * Schedules a repeating asynchronous task off the main thread.
     */
    ITaskHandle runTimerAsync(Consumer<ITaskHandle> task, long initialDelayTicks, long periodTicks);

    /**
     * Cancels all tasks owned by this scheduler.
     */
    void cancelAll();
}
