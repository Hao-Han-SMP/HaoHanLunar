package vn.haohan.lunar.api.system.combat.skill.interrupt;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe cancellation token representing the lifecycle of an active skill execution or channeling state.
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<InterruptReason> reason = new AtomicReference<>(null);

    public boolean isCancelled() {
        return cancelled.get();
    }

    public InterruptReason reason() {
        return reason.get();
    }

    /**
     * Cancels the execution with an explicit interrupt reason.
     * @return true if this call transitioned the token from active to cancelled.
     */
    public boolean cancel(InterruptReason reason) {
        if (cancelled.compareAndSet(false, true)) {
            this.reason.set(reason != null ? reason : InterruptReason.COMMAND);
            return true;
        }
        return false;
    }

    public boolean cancel() {
        return cancel(InterruptReason.COMMAND);
    }
}
