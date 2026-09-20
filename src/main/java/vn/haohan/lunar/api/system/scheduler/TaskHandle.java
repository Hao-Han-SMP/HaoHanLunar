package vn.haohan.lunar.core.system.scheduler;

/**
 * Handle to an active scheduled task allowing cancellation and state inspection.
 */
public interface TaskHandle {

    /**
     * Cancels the scheduled task.
     */
    void cancel();

    /**
     * @return true if this task has been cancelled
     */
    boolean isCancelled();
}
