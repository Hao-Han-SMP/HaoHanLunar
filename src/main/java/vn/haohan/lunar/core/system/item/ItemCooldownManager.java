package vn.haohan.lunar.core.system.item;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cooldown manager for custom item skills per player.
 */
public final class ItemCooldownManager {

    private final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    /**
     * Checks whether an item skill is currently on cooldown for a player.
     */
    public boolean isOnCooldown(UUID playerId, String itemId, long currentTick) {
        if (playerId == null || itemId == null) return false;
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return false;
        Long expiry = cooldowns.get(itemId);
        return expiry != null && expiry > currentTick;
    }

    /**
     * Gets the remaining cooldown in server ticks. Returns 0 if not on cooldown.
     */
    public long getRemainingTicks(UUID playerId, String itemId, long currentTick) {
        if (playerId == null || itemId == null) return 0L;
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns == null) return 0L;
        Long expiry = cooldowns.get(itemId);
        if (expiry == null || expiry <= currentTick) return 0L;
        return expiry - currentTick;
    }

    /**
     * Sets a cooldown for a player and item ID.
     */
    public void setCooldown(UUID playerId, String itemId, long durationTicks, long currentTick) {
        if (playerId == null || itemId == null || durationTicks <= 0) return;
        playerCooldowns.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                .put(itemId, currentTick + durationTicks);
    }

    /**
     * Resets/clears cooldown for a specific item.
     */
    public void resetCooldown(UUID playerId, String itemId) {
        if (playerId == null || itemId == null) return;
        Map<String, Long> cooldowns = playerCooldowns.get(playerId);
        if (cooldowns != null) {
            cooldowns.remove(itemId);
        }
    }

    /**
     * Clears all cooldowns for a player (e.g. on logout).
     */
    public void resetAll(UUID playerId) {
        if (playerId != null) {
            playerCooldowns.remove(playerId);
        }
    }

    /**
     * Removes expired cooldown entries to prevent memory leaks over time.
     */
    public void cleanup(long currentTick) {
        playerCooldowns.values().forEach(map -> map.entrySet().removeIf(entry -> entry.getValue() <= currentTick));
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public int activePlayerCount() {
        return playerCooldowns.size();
    }
}
