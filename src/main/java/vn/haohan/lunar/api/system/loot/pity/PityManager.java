package vn.haohan.lunar.api.system.loot.pity;

import vn.haohan.lunar.api.system.loot.pity.storage.PityStorage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe Pity Manager ensuring players receive guaranteed rare drops
 * after reaching the maximum pity threshold without a rare drop.
 * Combines zero-latency RAM cache with asynchronous storage write queue.
 */
public final class PityManager implements AutoCloseable {

    // (PlayerUUID + ":" + DropTableOrItemId) -> current miss counter
    private final Map<String, Integer> pityCounters = new ConcurrentHashMap<>();
    private final Map<String, PityRecord> dirtyQueue = new ConcurrentHashMap<>();
    private final Set<UUID> loadedPlayers = ConcurrentHashMap.newKeySet();
    private final PityStorage storage;

    public PityManager() {
        this(null);
    }

    public PityManager(PityStorage storage) {
        this.storage = storage;
        if (this.storage != null) {
            try {
                this.storage.init();
            } catch (Exception ignored) {}
        }
    }

    private static String key(UUID playerUuid, String targetId) {
        Objects.requireNonNull(playerUuid, "Player UUID must not be null");
        Objects.requireNonNull(targetId, "Target ID must not be null");
        return playerUuid + ":" + targetId.trim().toLowerCase();
    }

    /**
     * Pre-populates player's pity counters from storage into RAM cache on join.
     */
    public void loadPlayer(UUID playerUuid) {
        if (playerUuid == null || storage == null) return;
        Map<String, Integer> loaded = storage.loadAll(playerUuid);
        for (Map.Entry<String, Integer> e : loaded.entrySet()) {
            pityCounters.put(key(playerUuid, e.getKey()), e.getValue());
        }
        loadedPlayers.add(playerUuid);
    }

    public boolean isLoaded(UUID playerUuid) {
        return playerUuid != null && (storage == null || loadedPlayers.contains(playerUuid));
    }

    public int getPity(UUID playerUuid, String targetId) {
        return pityCounters.getOrDefault(key(playerUuid, targetId), 0);
    }

    public void setPity(UUID playerUuid, String targetId, int count) {
        int val = Math.max(0, count);
        pityCounters.put(key(playerUuid, targetId), val);
        queueDirty(playerUuid, targetId, val);
    }

    /**
     * Increments the pity counter for a player on a specific drop.
     *
     * @return updated pity count
     */
    public int recordMiss(UUID playerUuid, String targetId) {
        int updated = pityCounters.compute(key(playerUuid, targetId), (k, current) -> (current == null ? 0 : current) + 1);
        queueDirty(playerUuid, targetId, updated);
        return updated;
    }

    /**
     * Resets pity counter after a successful drop or pity trigger.
     */
    public void recordSuccess(UUID playerUuid, String targetId) {
        pityCounters.put(key(playerUuid, targetId), 0);
        queueDirty(playerUuid, targetId, 0);
    }

    private void queueDirty(UUID playerUuid, String targetId, int count) {
        if (storage == null) return;
        String normalizedTarget = targetId.trim().toLowerCase();
        dirtyQueue.put(key(playerUuid, targetId),
                new PityRecord(playerUuid, normalizedTarget, count, System.currentTimeMillis()));
    }

    /**
     * Flushes all pending dirty records to underlying storage.
     */
    public synchronized void flush() {
        if (storage == null || dirtyQueue.isEmpty()) return;
        Collection<PityRecord> records = dirtyQueue.values();
        storage.saveAll(records);
        dirtyQueue.clear();
    }

    public int getPendingDirtyCount() {
        return dirtyQueue.size();
    }

    /**
     * Checks whether the player has reached or exceeded max pity.
     */
    public boolean isPityTriggered(UUID playerUuid, String targetId, int maxPity) {
        if (maxPity <= 0) return false;
        return getPity(playerUuid, targetId) >= maxPity;
    }

    public void clear() {
        pityCounters.clear();
        dirtyQueue.clear();
        loadedPlayers.clear();
    }

    @Override
    public void close() {
        flush();
        if (storage != null) {
            storage.close();
        }
    }
}
