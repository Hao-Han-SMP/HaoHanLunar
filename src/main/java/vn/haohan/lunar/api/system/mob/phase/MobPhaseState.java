package vn.haohan.lunar.api.system.mob.phase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Mutable per-entity phase state, isolated from Warden-specific behavior state. */
public final class MobPhaseState {
    private final MobPhase phase;
    private final long enteredAtTick;
    private final Set<String> completedPhases;

    public MobPhaseState(MobPhase phase, long enteredAtTick) {
        this(phase, enteredAtTick, Set.of());
    }

    public MobPhaseState(MobPhase phase, long enteredAtTick, Set<String> previousCompletedPhases) {
        this.phase = Objects.requireNonNull(phase, "Phase must not be null");
        this.enteredAtTick = enteredAtTick;
        Set<String> completed = new HashSet<>(previousCompletedPhases != null ? previousCompletedPhases : Set.of());
        completed.add(phase.id());
        this.completedPhases = Collections.unmodifiableSet(completed);
    }

    public MobPhase phase() { return phase; }
    public String phaseId() { return phase.id(); }
    public long enteredAtTick() { return enteredAtTick; }
    public Set<String> completedPhases() { return completedPhases; }
    public boolean hasCompletedPhase(String id) { return id != null && completedPhases.contains(id.toLowerCase()); }
}
