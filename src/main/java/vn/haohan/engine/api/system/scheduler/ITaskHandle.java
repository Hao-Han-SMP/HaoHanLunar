package vn.haohan.engine.api.system.scheduler;

/**
 * Handle returned by scheduling calls allowing cancellation and state inspection.
 */
public interface ITaskHandle {

    /**
     * Attempts to cancel execution of this scheduled task.
     */
    void cancel();

    /**
     * Checks if this task has been cancelled.
     *
     * @return true if cancelled
     */
    boolean isCancelled();
}
