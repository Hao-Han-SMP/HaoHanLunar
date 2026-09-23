package vn.haohan.lunar.api.system.mob.phase;

import org.bukkit.Bukkit;
import vn.haohan.lunar.api.event.MobPhaseChangeEvent;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
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
    public PhaseTransitionResult update(ActiveMob mob, double health, double maxHealth, long tick) {
        return update(PhaseContext.ofHealth(mob, health, maxHealth, tick));
    }

    private static int parsePhaseNumber(String id) {
        if (id == null) return 1;
        String digits = id.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                return Integer.parseInt(digits);
            }
            catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    /**
     * Advanced update method evaluating composite phase context.
     */
    public PhaseTransitionResult update(PhaseContext context) {
        Objects.requireNonNull(context, "PhaseContext must not be null");
        ActiveMob mob = context.mob();
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

        // Fire MobPhaseChangeEvent if Bukkit is available
        int prevIndex = parsePhaseNumber(previous != null ? previous.phaseId() : "1");
        int nextIndex = parsePhaseNumber(selected.id());
        try {
            MobPhaseChangeEvent event = new MobPhaseChangeEvent(mob, prevIndex, nextIndex);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return PhaseTransitionResult.unchanged(previous);
            }
        }
        catch (Throwable ignored) {
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

    public boolean forcePhase(ActiveMob mob, String phaseId, long tick) {
        if (mob == null || phaseId == null) return false;
        MobPhase found = phases.stream().filter(p -> p.id().equalsIgnoreCase(phaseId.trim())).findFirst().orElse(null);
        if (found == null) return false;
        MobPhaseState prev = states.get(mob.entityId());
        MobPhaseState next = new MobPhaseState(found, tick, prev != null ? prev.completedPhases() : null);
        states.put(mob.entityId(), next);
        mob.setStance(found.id());
        return true;
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

    public record PhaseSkill(ActiveMob mob, String skillId, boolean entering) { }

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
