package vn.haohan.lunar.api.system.combat.cc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Thread-safe crowd-control state tracker for an active mob.
 */
public final class CrowdControlTracker {

    private final Map<CCState, CCEffect> activeEffects = new ConcurrentHashMap<>();
    private final Set<CCState> immuneStates = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final LongSupplier tickSupplier;

    public CrowdControlTracker() {
        this(System::currentTimeMillis);
    }

    public CrowdControlTracker(LongSupplier tickSupplier) {
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "Tick supplier must not be null");
    }

    /**
     * Adds immunity to specified CC states.
     */
    public void addImmunity(CCState state) {
        if (state != null) {
            immuneStates.add(state);
            activeEffects.remove(state);
        }
    }

    public void removeImmunity(CCState state) {
        if (state != null) {
            immuneStates.remove(state);
        }
    }

    public boolean isImmune(CCState state) {
        return state != null && immuneStates.contains(state);
    }

    public Set<CCState> immuneStates() {
        return Collections.unmodifiableSet(immuneStates);
    }

    /**
     * Attempts to apply a CC effect.
     *
     * @return true if effect was applied or refreshed, false if blocked by immunity or lower priority.
     */
    public boolean apply(CCState state, int durationTicks, UUID sourceEntityId, int priority, double intensity) {
        Objects.requireNonNull(state, "CCState must not be null");
        if (durationTicks <= 0 || isImmune(state)) {
            return false;
        }

        long currentTick = tickSupplier.getAsLong();
        long newExpiry = currentTick + durationTicks;

        CCEffect existing = activeEffects.get(state);
        if (existing != null && !existing.isExpired(currentTick)) {
            if (priority < existing.priority()) {
                // Lower priority cannot override active effect
                return false;
            } else if (priority == existing.priority()) {
                // Same priority refreshes / extends expiry
                long mergedExpiry = Math.max(existing.expiryTick(), newExpiry);
                double mergedIntensity = Math.max(existing.intensity(), intensity);
                activeEffects.put(state, new CCEffect(state, mergedExpiry, sourceEntityId != null ? sourceEntityId : existing.sourceEntityId(), priority, mergedIntensity));
                return true;
            }
        }

        activeEffects.put(state, new CCEffect(state, newExpiry, sourceEntityId, priority, intensity));
        return true;
    }

    public boolean apply(CCState state, int durationTicks, UUID sourceEntityId, int priority) {
        return apply(state, durationTicks, sourceEntityId, priority, 1.0);
    }

    public boolean hasEffect(CCState state) {
        return hasEffect(state, tickSupplier.getAsLong());
    }

    public boolean hasEffect(CCState state, long currentTick) {
        if (state == null) {
            return false;
        }
        CCEffect effect = activeEffects.get(state);
        if (effect == null) {
            return false;
        }
        if (effect.isExpired(currentTick)) {
            activeEffects.remove(state, effect);
            return false;
        }
        return true;
    }

    public Optional<CCEffect> getEffect(CCState state) {
        return getEffect(state, tickSupplier.getAsLong());
    }

    public Optional<CCEffect> getEffect(CCState state, long currentTick) {
        if (state == null) {
            return Optional.empty();
        }
        CCEffect effect = activeEffects.get(state);
        if (effect != null && effect.isExpired(currentTick)) {
            activeEffects.remove(state, effect);
            return Optional.empty();
        }
        return Optional.ofNullable(effect);
    }

    public boolean remove(CCState state) {
        return state != null && activeEffects.remove(state) != null;
    }

    public void clear() {
        activeEffects.clear();
    }

    public void cleanupExpired() {
        long currentTick = tickSupplier.getAsLong();
        activeEffects.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTick));
    }

    // --- State Queries ---

    public boolean isStunned() {
        return hasEffect(CCState.STUN);
    }

    public boolean isRooted() {
        return hasEffect(CCState.ROOT);
    }

    public boolean isSilenced() {
        return hasEffect(CCState.SILENCE);
    }

    public boolean isDisarmed() {
        return hasEffect(CCState.DISARM);
    }

    public boolean isInvulnerable() {
        return hasEffect(CCState.INVULNERABLE);
    }

    public boolean isSlowed() {
        return hasEffect(CCState.SLOW);
    }

    public boolean isFeared() {
        return hasEffect(CCState.FEAR);
    }

    /**
     * Whether the mob is currently permitted to move.
     */
    public boolean canMove() {
        long now = tickSupplier.getAsLong();
        return !hasEffect(CCState.STUN, now) && !hasEffect(CCState.ROOT, now);
    }

    /**
     * Whether the mob is currently permitted to cast skills.
     */
    public boolean canCast() {
        long now = tickSupplier.getAsLong();
        return !hasEffect(CCState.STUN, now) && !hasEffect(CCState.SILENCE, now);
    }

    /**
     * Whether the mob is currently permitted to make basic attacks.
     */
    public boolean canAttack() {
        long now = tickSupplier.getAsLong();
        return !hasEffect(CCState.STUN, now) && !hasEffect(CCState.DISARM, now);
    }

    public Map<CCState, CCEffect> snapshot() {
        long now = tickSupplier.getAsLong();
        cleanupExpired();
        return Collections.unmodifiableMap(new EnumMap<>(activeEffects));
    }
}
