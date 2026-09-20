package vn.haohan.lunar.api.mob.phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable phase definition supporting health thresholds, composite conditions,
 * enter/exit skill sequences, once-only enforcement, and transition cooldowns.
 */
public record MobPhase(
        String id,
        int priority,
        double minimumHealthRatio,
        List<PhaseCondition> conditions,
        List<String> onEnterSkills,
        List<String> onExitSkills,
        boolean resetCooldowns,
        boolean onceOnly,
        long transitionCooldownTicks
) {
    public MobPhase {
        Objects.requireNonNull(id, "Phase ID must not be null");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Phase ID must not be blank");
        if (!Double.isFinite(minimumHealthRatio) || minimumHealthRatio < 0 || minimumHealthRatio > 1) {
            throw new IllegalArgumentException("Phase health ratio must be between 0 and 1");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        onEnterSkills = onEnterSkills == null ? List.of() : List.copyOf(onEnterSkills);
        onExitSkills = onExitSkills == null ? List.of() : List.copyOf(onExitSkills);
    }

    /**
     * Backward-compatible constructor for existing callers and simple single-skill transitions.
     */
    public MobPhase(String id, double minimumHealthRatio, String enterSkill,
                    String exitSkill, boolean resetCooldowns) {
        this(id, 0, minimumHealthRatio,
                List.of(PhaseCondition.healthLessThanOrEqual(minimumHealthRatio)),
                enterSkill != null && !enterSkill.isBlank() ? List.of(enterSkill.trim().toLowerCase(Locale.ROOT)) : List.of(),
                exitSkill != null && !exitSkill.isBlank() ? List.of(exitSkill.trim().toLowerCase(Locale.ROOT)) : List.of(),
                resetCooldowns, false, 0L);
    }

    public Optional<String> enterSkillOptional() {
        return onEnterSkills.isEmpty() ? Optional.empty() : Optional.of(onEnterSkills.getFirst());
    }

    public Optional<String> exitSkillOptional() {
        return onExitSkills.isEmpty() ? Optional.empty() : Optional.of(onExitSkills.getFirst());
    }

    public boolean matches(PhaseContext context) {
        if (conditions.isEmpty()) {
            return context.healthRatio() <= minimumHealthRatio;
        }
        for (PhaseCondition condition : conditions) {
            if (!condition.matches(context)) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private int priority = 0;
        private double minimumHealthRatio = 1.0;
        private final List<PhaseCondition> conditions = new ArrayList<>();
        private final List<String> onEnterSkills = new ArrayList<>();
        private final List<String> onExitSkills = new ArrayList<>();
        private boolean resetCooldowns = false;
        private boolean onceOnly = false;
        private long transitionCooldownTicks = 0L;

        private Builder(String id) {
            this.id = id;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder minimumHealthRatio(double ratio) {
            this.minimumHealthRatio = ratio;
            this.conditions.add(PhaseCondition.healthLessThanOrEqual(ratio));
            return this;
        }

        public Builder condition(PhaseCondition condition) {
            if (condition != null) this.conditions.add(condition);
            return this;
        }

        public Builder onEnterSkill(String skillId) {
            if (skillId != null && !skillId.isBlank()) this.onEnterSkills.add(skillId.trim().toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder onExitSkill(String skillId) {
            if (skillId != null && !skillId.isBlank()) this.onExitSkills.add(skillId.trim().toLowerCase(Locale.ROOT));
            return this;
        }

        public Builder resetCooldowns(boolean resetCooldowns) {
            this.resetCooldowns = resetCooldowns;
            return this;
        }

        public Builder onceOnly(boolean onceOnly) {
            this.onceOnly = onceOnly;
            return this;
        }

        public Builder transitionCooldownTicks(long ticks) {
            this.transitionCooldownTicks = Math.max(0L, ticks);
            return this;
        }

        public MobPhase build() {
            return new MobPhase(id, priority, minimumHealthRatio, conditions, onEnterSkills, onExitSkills,
                    resetCooldowns, onceOnly, transitionCooldownTicks);
        }
    }
}
