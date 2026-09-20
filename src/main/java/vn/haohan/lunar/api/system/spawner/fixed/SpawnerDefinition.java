package vn.haohan.lunar.api.system.spawner.fixed;

import org.bukkit.Location;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable configuration defining a fixed location mob spawner with soft/hard leashes,
 * environmental conditions, and multi-wave capabilities.
 */
public record SpawnerDefinition(String id,
                                String mobId,
                                Location location,
                                int maxMobs,
                                int mobsPerSpawn,
                                int cooldownSeconds,
                                int warmupSeconds,
                                double activationRange,
                                double leashRange,
                                boolean healOnLeash,
                                boolean resetThreatOnLeash,
                                double softLeashRadius,
                                double hardLeashRadius,
                                List<String> conditions,
                                List<SpawnerWave> waves) {

    public SpawnerDefinition(String id,
                             String mobId,
                             Location location,
                             int maxMobs,
                             int mobsPerSpawn,
                             int cooldownSeconds,
                             int warmupSeconds,
                             double activationRange,
                             double leashRange,
                             boolean healOnLeash,
                             boolean resetThreatOnLeash) {
        this(id, mobId, location, maxMobs, mobsPerSpawn, cooldownSeconds, warmupSeconds,
                activationRange, leashRange, healOnLeash, resetThreatOnLeash,
                leashRange > 0.0 ? leashRange * 0.6 : 30.0,
                leashRange > 0.0 ? leashRange : 50.0,
                List.of(), List.of());
    }

    public SpawnerDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Spawner ID must not be blank");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        if (mobId == null || mobId.isBlank()) {
            throw new IllegalArgumentException("Mob ID must not be blank");
        }
        mobId = mobId.trim().toLowerCase(Locale.ROOT);
        Objects.requireNonNull(location, "Spawner location must not be null");

        maxMobs = Math.max(1, maxMobs);
        mobsPerSpawn = Math.max(1, mobsPerSpawn);
        cooldownSeconds = Math.max(1, cooldownSeconds);
        warmupSeconds = Math.max(0, warmupSeconds);
        activationRange = Math.max(0.0, activationRange);
        leashRange = Math.max(0.0, leashRange);

        softLeashRadius = softLeashRadius > 0.0 ? softLeashRadius : (leashRange > 0.0 ? leashRange * 0.6 : 30.0);
        hardLeashRadius = hardLeashRadius > 0.0 ? hardLeashRadius : (leashRange > 0.0 ? leashRange : 50.0);
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
        waves = waves != null ? List.copyOf(waves) : List.of();
    }

    public boolean hasWaves() {
        return !waves.isEmpty();
    }

    public static Builder builder(String id, String mobId, Location location) {
        return new Builder(id, mobId, location);
    }

    public static final class Builder {
        private final String id;
        private final String mobId;
        private final Location location;
        private int maxMobs = 1;
        private int mobsPerSpawn = 1;
        private int cooldownSeconds = 30;
        private int warmupSeconds = 0;
        private double activationRange = 40.0;
        private double leashRange = 60.0;
        private boolean healOnLeash = true;
        private boolean resetThreatOnLeash = true;
        private double softLeashRadius = -1.0;
        private double hardLeashRadius = -1.0;
        private List<String> conditions = List.of();
        private List<SpawnerWave> waves = List.of();

        private Builder(String id, String mobId, Location location) {
            this.id = id;
            this.mobId = mobId;
            this.location = location;
        }

        public Builder maxMobs(int maxMobs) { this.maxMobs = maxMobs; return this; }
        public Builder mobsPerSpawn(int mobsPerSpawn) { this.mobsPerSpawn = mobsPerSpawn; return this; }
        public Builder cooldownSeconds(int cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; return this; }
        public Builder warmupSeconds(int warmupSeconds) { this.warmupSeconds = warmupSeconds; return this; }
        public Builder activationRange(double activationRange) { this.activationRange = activationRange; return this; }
        public Builder leashRange(double leashRange) { this.leashRange = leashRange; return this; }
        public Builder healOnLeash(boolean healOnLeash) { this.healOnLeash = healOnLeash; return this; }
        public Builder resetThreatOnLeash(boolean resetThreatOnLeash) { this.resetThreatOnLeash = resetThreatOnLeash; return this; }
        public Builder softLeashRadius(double softLeashRadius) { this.softLeashRadius = softLeashRadius; return this; }
        public Builder hardLeashRadius(double hardLeashRadius) { this.hardLeashRadius = hardLeashRadius; return this; }
        public Builder conditions(List<String> conditions) { this.conditions = conditions; return this; }
        public Builder waves(List<SpawnerWave> waves) { this.waves = waves; return this; }

        public SpawnerDefinition build() {
            double soft = softLeashRadius > 0.0 ? softLeashRadius : (leashRange > 0.0 ? leashRange * 0.6 : 30.0);
            double hard = hardLeashRadius > 0.0 ? hardLeashRadius : (leashRange > 0.0 ? leashRange : 50.0);
            return new SpawnerDefinition(id, mobId, location, maxMobs, mobsPerSpawn,
                    cooldownSeconds, warmupSeconds, activationRange, leashRange, healOnLeash, resetThreatOnLeash,
                    soft, hard, conditions, waves);
        }
    }
}
