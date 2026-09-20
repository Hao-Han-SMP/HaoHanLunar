package vn.haohan.lunar.api.system.combat.skill.aura;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable definition of an aura effect.
 */
public record AuraDefinition(
        String id,
        long durationTicks,
        long intervalTicks,
        double radius,
        int maxStacks,
        StackMode stackMode,
        List<AuraComponent> components
) {
    public AuraDefinition {
        Objects.requireNonNull(id, "Aura ID must not be null");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Aura ID must not be blank");
        if (durationTicks <= 0) throw new IllegalArgumentException("Duration must be positive");
        intervalTicks = Math.max(1L, intervalTicks);
        radius = Math.max(0.0, radius);
        maxStacks = Math.max(1, maxStacks);
        stackMode = stackMode != null ? stackMode : StackMode.REFRESH;
        components = components != null ? List.copyOf(components) : List.of();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private long durationTicks = 200L;
        private long intervalTicks = 10L;
        private double radius = 5.0;
        private int maxStacks = 1;
        private StackMode stackMode = StackMode.REFRESH;
        private final List<AuraComponent> components = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder durationTicks(long duration) {
            this.durationTicks = duration;
            return this;
        }

        public Builder intervalTicks(long interval) {
            this.intervalTicks = interval;
            return this;
        }

        public Builder radius(double radius) {
            this.radius = radius;
            return this;
        }

        public Builder maxStacks(int maxStacks) {
            this.maxStacks = maxStacks;
            return this;
        }

        public Builder stackMode(StackMode stackMode) {
            this.stackMode = stackMode;
            return this;
        }

        public Builder component(AuraComponent component) {
            if (component != null) this.components.add(component);
            return this;
        }

        public AuraDefinition build() {
            return new AuraDefinition(id, durationTicks, intervalTicks, radius, maxStacks, stackMode, components);
        }
    }
}
