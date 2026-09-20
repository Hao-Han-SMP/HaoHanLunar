package vn.haohan.lunar.api.loot.pity;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing a player's pity counter for a specific drop pool or item.
 */
public record PityRecord(UUID playerUuid, String poolId, int count, long lastUpdated) {

    public PityRecord {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(poolId, "poolId must not be null");
        poolId = poolId.trim().toLowerCase();
        count = Math.max(0, count);
    }
}
