package vn.haohan.lunar.api.mob.phase;

import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.skill.CooldownRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Generic phase state machine supporting deterministic health thresholds, composite conditions,
 * enter/exit skill sequences, once-only enforcement, and transition cooldowns.
 */
public final class MobPhaseMachine {

    private final List<MobPhase> phases;
    private final Map<UUID, MobPhaseState> states = new ConcurrentHashMap<>();
    private final CooldownRegistry cooldowns;
    private final Consumer<PhaseSkill> skillDispatcher;

    public MobPhaseMachine(List<MobPhase> phases, CooldownRegistry cooldowns, Consumer<PhaseSkill> skillDispatcher) {
        if (phases == null || phases.isEmpty()) throw new IllegalArgumentException("At least one phase is required");
        // Sort phases: higher priority first, then lower health ratio first
        this.phases = phases.stream().sorted(
                Comparator.comparingInt(MobPhase::priority).reversed()
                        .thenComparingDouble(MobPhase::minimumHealthRatio)
        ).toList();
        this.cooldowns = Objects.requireNonNull(cooldowns, "Cooldown registry must not be null");
        this.skillDispatcher = Objects.requireNonNull(skillDispatcher, "Skill dispatcher must not be null");
    }

    /**
     * Legacy update method based on health ratios.
     */
    public PhaseTransitionResult update(ActiveLunarMob mob, double health, double maxHealth, long tick) {
        return update(PhaseContext.ofHealth(mob, health, maxHealth, tick));
    }

    /**
     * Advanced update method evaluating composite phase context.
     */
    public PhaseTransitionResult update(PhaseContext context) {
        Objects.requireNonNull(context, "PhaseContext must not be null");
        ActiveLunarMob mob = context.mob();
        double health = context.health();
        double maxHealth = context.maxHealth();

        if (!Double.isFinite(health) || !Double.isFinite(maxHealth) || maxHealth <= 0) {
            return PhaseTransitionResult.invalid("Health values must be finite and max health must be positive");
        }

        MobPhaseState previous = states.get(mob.entityId());

        // Check transition cooldown if currently in a phase with cooldown
        if (previous != null && previous.phase().transitionCooldownTicks() > 0) {
            long ticksInPhase = context.currentTick() - previous.enteredAtTick();
            if (ticksInPhase < previous.phase().transitionCooldownTicks()) {
                return PhaseTransitionResult.unchanged(previous);
            }
        }

        // Find best matching phase
        MobPhase selected = null;
        for (MobPhase phase : phases) {
            if (phase.onceOnly() && previous != null && previous.hasCompletedPhase(phase.id())) {
                continue;
            }
            if (phase.matches(context)) {
                selected = phase;
                break;
            }
        }

        if (selected == null) {
            // Default fallback to the lowest priority / highest health phase (default phase)
            selected = phases.getLast();
        }

        if (previous != null && previous.phaseId().equals(selected.id())) {
            return PhaseTransitionResult.unchanged(previous);
        }

        // Dispatch exit skills
        if (previous != null) {
            for (String exitSkill : previous.phase().onExitSkills()) {
                skillDispatcher.accept(new PhaseSkill(mob, exitSkill, false));
            }
        }

        // Apply new phase state
        MobPhaseState next = new MobPhaseState(selected, context.currentTick(),
                previous != null ? previous.completedPhases() : null);
        states.put(mob.entityId(), next);

        // Update stance on mob if appropriate
        mob.setStance(selected.id());

        if (selected.resetCooldowns()) {
            cooldowns.clear(mob.entityId());
        }

        // Dispatch enter skills
        for (String enterSkill : selected.onEnterSkills()) {
            skillDispatcher.accept(new PhaseSkill(mob, enterSkill, true));
        }

        return new PhaseTransitionResult(true, previous, next, null);
    }

    public Optional<MobPhaseState> state(UUID entityId) { return Optional.ofNullable(states.get(entityId)); }

    public void remove(UUID entityId) {
        if (entityId != null) states.remove(entityId);
    }

    public int cleanupAll() {
        int count = states.size();
        states.clear();
        return count;
    }

    public record PhaseSkill(ActiveLunarMob mob, String skillId, boolean entering) { }

    public record PhaseTransitionResult(boolean changed, MobPhaseState previous,
                                        MobPhaseState current, String error) {
        public static PhaseTransitionResult unchanged(MobPhaseState state) {
            return new PhaseTransitionResult(false, state, state, null);
        }
        public static PhaseTransitionResult invalid(String error) {
            return new PhaseTransitionResult(false, null, null, error);
        }
    }
}
