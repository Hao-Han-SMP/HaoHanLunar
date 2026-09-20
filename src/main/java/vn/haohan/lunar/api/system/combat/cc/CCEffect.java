package vn.haohan.lunar.api.system.combat.cc;

import java.util.Objects;
import java.util.UUID;

/**
 * An active crowd-control instance applied to an entity.
 */
public record CCEffect(
        CCState state,
        long expiryTick,
        UUID sourceEntityId,
        int priority,
        double intensity
) {
    public CCEffect {
        Objects.requireNonNull(state, "CCState must not be null");
    }

    public CCEffect(CCState state, long expiryTick, UUID sourceEntityId, int priority) {
        this(state, expiryTick, sourceEntityId, priority, 1.0);
    }

    public boolean isExpired(long currentTick) {
        return currentTick >= expiryTick;
    }
}
