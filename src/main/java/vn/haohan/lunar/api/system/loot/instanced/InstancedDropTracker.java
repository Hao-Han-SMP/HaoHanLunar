package vn.haohan.lunar.api.loot.instanced;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks instanced dropped items on the ground with owner protection.
 * Prevents non-contributing players from stealing loot for a protected duration (default 60s).
 */
public final class InstancedDropTracker {

    public record InstancedDrop(UUID itemEntityId, UUID ownerUuid, long protectUntilTick) {
        public InstancedDrop {
            Objects.requireNonNull(itemEntityId, "itemEntityId must not be null");
            Objects.requireNonNull(ownerUuid, "ownerUuid must not be null");
        }

        public boolean isProtected(long currentTick) {
            return currentTick < protectUntilTick;
        }
    }

    public static final long DEFAULT_PROTECTION_TICKS = 1200L; // 60 seconds

    private final Map<UUID, InstancedDrop> trackedItems = new ConcurrentHashMap<>();

    public void protect(UUID itemEntityId, UUID ownerUuid, long currentTick) {
        protect(itemEntityId, ownerUuid, currentTick, DEFAULT_PROTECTION_TICKS);
    }

    public void protect(UUID itemEntityId, UUID ownerUuid, long currentTick, long protectionDurationTicks) {
        if (itemEntityId == null || ownerUuid == null) return;
        long until = currentTick + Math.max(0, protectionDurationTicks);
        trackedItems.put(itemEntityId, new InstancedDrop(itemEntityId, ownerUuid, until));
    }

    /**
     * Checks whether a player can pick up the specified item entity at the current tick.
     *
     * @return true if pickup is allowed; false if protected by another player
     */
    public boolean canPickup(UUID playerUuid, UUID itemEntityId, long currentTick) {
        if (itemEntityId == null) return true;
        InstancedDrop drop = trackedItems.get(itemEntityId);
        if (drop == null) {
            return true; // Not an instanced protected item
        }

        if (!drop.isProtected(currentTick)) {
            return true; // Protection period expired
        }

        return playerUuid != null && playerUuid.equals(drop.ownerUuid());
    }

    public Optional<InstancedDrop> getDrop(UUID itemEntityId) {
        if (itemEntityId == null) return Optional.empty();
        return Optional.ofNullable(trackedItems.get(itemEntityId));
    }

    public void remove(UUID itemEntityId) {
        if (itemEntityId != null) {
            trackedItems.remove(itemEntityId);
        }
    }

    public void cleanupExpired(long currentTick) {
        trackedItems.entrySet().removeIf(entry -> !entry.getValue().isProtected(currentTick));
    }

    public void clear() {
        trackedItems.clear();
    }

    public int activeCount() {
        return trackedItems.size();
    }
}
