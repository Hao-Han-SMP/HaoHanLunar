package vn.haohan.lunar.api.spawner.random;

import org.bukkit.World;
import org.bukkit.event.entity.CreatureSpawnEvent;
import vn.haohan.lunar.api.combat.skill.condition.Condition;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable configuration for a random mob spawn rule.
 */
public record RandomSpawnRule(String id,
                              String mobId,
                              Set<String> worlds,
                              Set<String> biomes,
                              double chance,
                              int priority,
                              SpawnAction action,
                              double minY,
                              double maxY,
                              double minPlayerDistance,
                              double maxPlayerDistance,
                              boolean enabled,
                              List<Condition> conditions,
                              Set<CreatureSpawnEvent.SpawnReason> spawnReasons) {

    public RandomSpawnRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rule ID must not be blank");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        if (mobId == null || mobId.isBlank()) {
            throw new IllegalArgumentException("Mob ID must not be blank");
        }
        mobId = mobId.trim().toLowerCase(Locale.ROOT);
        chance = Math.clamp(chance, 0.0, 1.0);
        action = action != null ? action : SpawnAction.REPLACE;

        worlds = worlds != null
                ? Collections.unmodifiableSet(worlds.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                : Set.of();
        biomes = biomes != null
                ? Collections.unmodifiableSet(biomes.stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                : Set.of();
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
        spawnReasons = spawnReasons != null && !spawnReasons.isEmpty()
                ? Collections.unmodifiableSet(spawnReasons)
                : Set.of(CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN);
    }

    public RandomSpawnRule(String id,
                           String mobId,
                           Set<String> worlds,
                           Set<String> biomes,
                           double chance,
                           int priority,
                           SpawnAction action,
                           double minY,
                           double maxY,
                           double minPlayerDistance,
                           double maxPlayerDistance,
                           boolean enabled,
                           List<Condition> conditions) {
        this(id, mobId, worlds, biomes, chance, priority, action, minY, maxY, minPlayerDistance, maxPlayerDistance, enabled, conditions,
                Set.of(CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN));
    }

    public boolean matchesWorld(World world) {
        if (worlds.isEmpty()) {
            return true;
        }
        if (world == null) {
            return false;
        }
        String name = world.getName().toLowerCase(Locale.ROOT);
        for (String w : worlds) {
            if (name.equals(w) || name.endsWith(":" + w) || w.endsWith(":" + name)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesBiome(String biomeKey) {
        if (biomes.isEmpty()) {
            return true;
        }
        if (biomeKey == null || biomeKey.isBlank()) {
            return false;
        }
        String normalized = biomeKey.trim().toLowerCase(Locale.ROOT);
        for (String b : biomes) {
            if (normalized.equals(b) || normalized.endsWith(":" + b) || b.endsWith(":" + normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesElevation(double y) {
        return y >= minY && y <= maxY;
    }

    public boolean matchesReason(CreatureSpawnEvent.SpawnReason reason) {
        if (spawnReasons.isEmpty()) {
            return true;
        }
        return reason != null && spawnReasons.contains(reason);
    }

    public boolean rollChance(Random random) {
        if (chance >= 1.0) return true;
        if (chance <= 0.0) return false;
        return (random != null ? random.nextDouble() : Math.random()) < chance;
    }

    public static Builder builder(String id, String mobId) {
        return new Builder(id, mobId);
    }

    public static final class Builder {
        private final String id;
        private final String mobId;
        private Set<String> worlds = Set.of();
        private Set<String> biomes = Set.of();
        private double chance = 1.0;
        private int priority = 0;
        private SpawnAction action = SpawnAction.REPLACE;
        private double minY = -64.0;
        private double maxY = 320.0;
        private double minPlayerDistance = 24.0;
        private double maxPlayerDistance = 48.0;
        private boolean enabled = false;
        private List<Condition> conditions = List.of();
        private Set<CreatureSpawnEvent.SpawnReason> spawnReasons = Set.of();

        private Builder(String id, String mobId) {
            this.id = id;
            this.mobId = mobId;
        }

        public Builder worlds(Set<String> worlds) { this.worlds = worlds; return this; }
        public Builder biomes(Set<String> biomes) { this.biomes = biomes; return this; }
        public Builder chance(double chance) { this.chance = chance; return this; }
        public Builder priority(int priority) { this.priority = priority; return this; }
        public Builder action(SpawnAction action) { this.action = action; return this; }
        public Builder elevation(double minY, double maxY) { this.minY = minY; this.maxY = maxY; return this; }
        public Builder playerDistance(double min, double max) { this.minPlayerDistance = min; this.maxPlayerDistance = max; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }
        public Builder conditions(List<Condition> conditions) { this.conditions = conditions; return this; }
        public Builder spawnReasons(Set<CreatureSpawnEvent.SpawnReason> spawnReasons) { this.spawnReasons = spawnReasons; return this; }

        public RandomSpawnRule build() {
            return new RandomSpawnRule(id, mobId, worlds, biomes, chance, priority, action,
                    minY, maxY, minPlayerDistance, maxPlayerDistance, enabled, conditions, spawnReasons);
        }
    }
}
