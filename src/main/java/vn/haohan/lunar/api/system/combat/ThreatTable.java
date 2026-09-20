package vn.haohan.lunar.api.system.combat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public interface representing a mob's threat table for tracking player/entity hostility.
 */
public interface ThreatTable {

    /**
     * @return The UUID of the mob that owns this threat table.
     */
    UUID mobId();

    /**
     * Adds raw threat points toward a target entity.
     *
     * @param targetId    Target entity UUID.
     * @param amount      Amount of threat to add.
     * @param currentTick Current server tick.
     */
    void addThreat(UUID targetId, double amount, long currentTick);

    /**
     * Adds raw threat points toward a target entity using current wall-clock tick approximation.
     */
    void addThreat(UUID targetId, double amount);

    /**
     * Adds threat generated from dealing damage (typically 1 damage = 1 threat).
     */
    void addDamageThreat(UUID targetId, double damage, long currentTick);

    /**
     * Adds threat generated from healing allies (typically 1 heal = 0.5 threat).
     */
    void addHealThreat(UUID healerId, double healing, long currentTick);

    /**
     * Adds proximity threat points for standing close to the mob.
     */
    void addProximityThreat(UUID targetId, double points, long currentTick);

    /**
     * @return Current accumulated threat for a given target entity.
     */
    double getThreat(UUID targetId);

    /**
     * Resolves the primary target with the highest threat score, factoring in switch threshold and taunts.
     */
    Optional<UUID> getTopTarget();

    /**
     * Resolves the primary target with the highest threat score, factoring in switch threshold and taunts.
     *
     * @return The primary target entity UUID, or empty if no targets exist.
     */
    default Optional<UUID> topTarget() {
        return getTopTarget();
    }

    /**
     * Removes all threat accumulated by a specific target entity.
     */
    void clearTarget(UUID targetId);

    /**
     * Clears all threat entries from this table.
     */
    void clearAll();

    /**
     * Returns an unmodifiable snapshot of all active threat scores.
     */
    Map<UUID, Double> getSnapshot();

    /**
     * Forces the mob to focus on a specific target for a given duration in ticks.
     */
    void setTaunt(UUID targetId, long durationTicks, long currentTick);

    /**
     * Performs threat decay calculation based on inactivity ticks.
     */
    void tick(long currentTick);

    /**
     * @return Total threat accumulated across all targets.
     */
    double totalThreat();

    /**
     * @return True if the threat table is empty.
     */
    boolean isEmpty();
}
