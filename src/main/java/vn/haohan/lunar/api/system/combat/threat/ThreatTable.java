package vn.haohan.lunar.api.system.combat.threat;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe threat table for tracking hostility values and managing stable target selection.
 */
public final class ThreatTable implements vn.haohan.lunar.api.system.combat.ThreatTable {

    private final UUID mobId;
    private final Map<UUID, Double> threatScores = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInteractions = new ConcurrentHashMap<>();

    private double switchThresholdMultiplier = 1.10; // Must exceed by at least 10%
    private long decayDelayTicks = 100L; // 5 seconds of inactivity before decay starts
    private double decayFractionPerSecond = 0.10; // 10% of current threat decays per second

    private volatile UUID currentTargetId;
    private volatile UUID forcedTauntTargetId;
    private volatile long tauntExpiryTick;

    public ThreatTable(UUID mobId) {
        this.mobId = Objects.requireNonNull(mobId, "Mob UUID must not be null");
    }

    public UUID mobId() {
        return mobId;
    }

    public void addThreat(UUID targetId, double amount, long currentTick) {
        if (targetId == null || amount <= 0.0) {
            return;
        }
        threatScores.merge(targetId, amount, Double::sum);
        lastInteractions.put(targetId, currentTick);
    }

    public void addThreat(UUID targetId, double amount) {
        addThreat(targetId, amount, System.currentTimeMillis() / 50L);
    }

    /** 1 damage = 1 threat point. */
    public void addDamageThreat(UUID targetId, double damage, long currentTick) {
        addThreat(targetId, Math.max(0.0, damage), currentTick);
    }

    /** 1 healing = 0.5 threat point. */
    public void addHealThreat(UUID healerId, double healing, long currentTick) {
        addThreat(healerId, Math.max(0.0, healing * 0.5), currentTick);
    }

    /** Proximity threat from staying close to boss. */
    public void addProximityThreat(UUID targetId, double points, long currentTick) {
        addThreat(targetId, Math.max(0.0, points), currentTick);
    }

    /**
     * Forces immediate taunt on the target entity.
     * Sets target threat to exceed top threat and locks target until duration ticks expire.
     */
    public void taunt(UUID targetId, double bonusAmount, long durationTicks, long currentTick) {
        if (targetId == null) {
            return;
        }
        double topThreat = threatScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double forcedThreat = Math.max(topThreat * 1.15, topThreat + Math.max(10.0, bonusAmount));
        threatScores.put(targetId, forcedThreat);
        lastInteractions.put(targetId, currentTick);

        this.currentTargetId = targetId;
        if (durationTicks > 0) {
            this.forcedTauntTargetId = targetId;
            this.tauntExpiryTick = currentTick + durationTicks;
        }
    }

    public void setThreat(UUID targetId, double amount, long currentTick) {
        if (targetId == null) {
            return;
        }
        if (amount <= 0.0) {
            removeTarget(targetId);
            return;
        }
        threatScores.put(targetId, amount);
        lastInteractions.put(targetId, currentTick);
    }

    public double getThreat(UUID targetId) {
        if (targetId == null) {
            return 0.0;
        }
        return threatScores.getOrDefault(targetId, 0.0);
    }

    public double totalThreat() {
        return threatScores.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public void removeTarget(UUID targetId) {
        if (targetId == null) {
            return;
        }
        threatScores.remove(targetId);
        lastInteractions.remove(targetId);
        if (targetId.equals(currentTargetId)) {
            currentTargetId = null;
        }
        if (targetId.equals(forcedTauntTargetId)) {
            forcedTauntTargetId = null;
            tauntExpiryTick = 0;
        }
    }

    public void clear() {
        threatScores.clear();
        lastInteractions.clear();
        currentTargetId = null;
        forcedTauntTargetId = null;
        tauntExpiryTick = 0;
    }

    public boolean isEmpty() {
        return threatScores.isEmpty();
    }

    public boolean hasTarget(UUID targetId) {
        return targetId != null && threatScores.containsKey(targetId);
    }

    public int size() {
        return threatScores.size();
    }

    public UUID currentTargetId() {
        return currentTargetId;
    }

    public void setCurrentTargetId(UUID currentTargetId) {
        this.currentTargetId = currentTargetId;
    }

    public double switchThresholdMultiplier() {
        return switchThresholdMultiplier;
    }

    public void setSwitchThresholdMultiplier(double multiplier) {
        this.switchThresholdMultiplier = Math.max(1.0, multiplier);
    }

    public long decayDelayTicks() {
        return decayDelayTicks;
    }

    public void setDecayDelayTicks(long ticks) {
        this.decayDelayTicks = Math.max(0L, ticks);
    }

    public double decayFractionPerSecond() {
        return decayFractionPerSecond;
    }

    public void setDecayFractionPerSecond(double fraction) {
        this.decayFractionPerSecond = Math.clamp(fraction, 0.0, 1.0);
    }

    public Optional<UUID> getTopThreatTarget() {
        return threatScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
    }

    public Optional<UUID> topTarget() {
        return getTopThreatTarget();
    }

    /**
     * Evaluates and returns the target entity UUID based on threat thresholds and taunt locks.
     * Prevents target oscillation by requiring alternative targets to exceed current target by switchThresholdMultiplier.
     */
    public Optional<UUID> evaluateTarget() {
        return evaluateTarget(System.currentTimeMillis());
    }

    public Optional<UUID> evaluateTarget(long currentTick) {
        if (threatScores.isEmpty()) {
            currentTargetId = null;
            forcedTauntTargetId = null;
            return Optional.empty();
        }

        // Active forced taunt check
        if (forcedTauntTargetId != null) {
            if (currentTick <= tauntExpiryTick && threatScores.containsKey(forcedTauntTargetId)) {
                currentTargetId = forcedTauntTargetId;
                return Optional.of(forcedTauntTargetId);
            } else {
                forcedTauntTargetId = null;
            }
        }

        UUID topTarget = getTopThreatTarget().orElse(null);
        if (topTarget == null) {
            currentTargetId = null;
            return Optional.empty();
        }

        if (currentTargetId == null || !threatScores.containsKey(currentTargetId)) {
            currentTargetId = topTarget;
            return Optional.of(topTarget);
        }

        if (topTarget.equals(currentTargetId)) {
            return Optional.of(currentTargetId);
        }

        double currentThreat = getThreat(currentTargetId);
        double topThreat = getThreat(topTarget);

        // Only switch if the top threat exceeds the current target by at least switchThresholdMultiplier (e.g. 110%)
        if (topThreat >= currentThreat * switchThresholdMultiplier) {
            currentTargetId = topTarget;
            return Optional.of(topTarget);
        }

        return Optional.of(currentTargetId);
    }

    /**
     * Decays inactive threats over time.
     *
     * @param currentTick  Current server tick.
     * @param elapsedTicks Ticks passed since last decay check (typically 20 for once-per-second).
     */
    public void tickDecay(long currentTick, long elapsedTicks) {
        if (threatScores.isEmpty() || elapsedTicks <= 0) {
            return;
        }

        double seconds = elapsedTicks / 20.0;
        double decayFactor = Math.pow(1.0 - decayFractionPerSecond, seconds);

        for (Map.Entry<UUID, Long> entry : lastInteractions.entrySet()) {
            UUID targetId = entry.getKey();
            long lastActive = entry.getValue();

            if (currentTick - lastActive >= decayDelayTicks) {
                Double currentScore = threatScores.get(targetId);
                if (currentScore != null) {
                    double newScore = currentScore * decayFactor;
                    if (newScore < 1.0) {
                        removeTarget(targetId);
                    } else {
                        threatScores.put(targetId, newScore);
                    }
                }
            }
        }
    }

    public Map<UUID, Double> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(threatScores));
    }

    // --- ThreatTable API Implementation ---
    @Override
    public Optional<UUID> getTopTarget() {
        return evaluateTarget();
    }

    @Override
    public void clearTarget(UUID targetId) {
        removeTarget(targetId);
    }

    @Override
    public void clearAll() {
        clear();
    }

    @Override
    public Map<UUID, Double> getSnapshot() {
        return snapshot();
    }

    @Override
    public void setTaunt(UUID targetId, long durationTicks, long currentTick) {
        taunt(targetId, 10.0, durationTicks, currentTick);
    }

    @Override
    public void tick(long currentTick) {
        tickDecay(currentTick, 20);
    }
}
