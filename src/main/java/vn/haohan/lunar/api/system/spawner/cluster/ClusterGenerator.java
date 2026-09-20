package vn.haohan.lunar.api.spawner.cluster;

import org.bukkit.Location;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Cluster generator producing an Alpha/Leader mob and surrounding pack minions.
 * Guarantees parent-child link and respects local mob caps.
 */
public final class ClusterGenerator {

    @FunctionalInterface
    public interface MobSpawnDelegate {
        ActiveMob spawn(String mobId, Location location);
    }

    private final Random random;

    public ClusterGenerator(Random random) {
        this.random = random != null ? random : new Random();
    }

    public ClusterGenerator() {
        this(new Random());
    }

    /**
     * Attempts to spawn a cluster at the given center location.
     *
     * @param definition         cluster settings
     * @param center             center coordinates
     * @param localMobCap        maximum allowed mobs in the local area/chunk
     * @param currentEntityCount current number of living entities in the local area
     * @param spawner            delegate responsible for instantiating the ActiveMob
     * @return ClusterSpawnResult
     */
    public ClusterSpawnResult generate(
            ClusterDefinition definition,
            Location center,
            int localMobCap,
            int currentEntityCount,
            MobSpawnDelegate spawner
    ) {
        Objects.requireNonNull(definition, "Definition must not be null");
        Objects.requireNonNull(center, "Center location must not be null");
        Objects.requireNonNull(spawner, "Spawner delegate must not be null");

        if (!definition.enabled()) {
            return ClusterSpawnResult.failed("Cluster definition is disabled");
        }

        if (definition.chance() < 1.0 && random.nextDouble() > definition.chance()) {
            return ClusterSpawnResult.failed("Chance roll failed");
        }

        int minionCount = definition.minMinions();
        if (definition.maxMinions() > definition.minMinions()) {
            minionCount += random.nextInt(definition.maxMinions() - definition.minMinions() + 1);
        }

        int totalToSpawn = 1 + minionCount;
        if (currentEntityCount + totalToSpawn > localMobCap) {
            // Trim minions if possible, or fail if even leader cannot spawn
            int availableCapacity = localMobCap - currentEntityCount;
            if (availableCapacity <= 0) {
                return ClusterSpawnResult.failed("Local mob cap reached (" + currentEntityCount + "/" + localMobCap + ")");
            }
            minionCount = Math.max(0, availableCapacity - 1);
        }

        // 1. Spawn Leader
        ActiveMob leader = spawner.spawn(definition.leaderMobId(), center.clone());
        if (leader == null) {
            return ClusterSpawnResult.failed("Failed to spawn cluster leader: " + definition.leaderMobId());
        }

        // 2. Spawn Minions around leader
        List<ActiveMob> minions = new ArrayList<>(minionCount);
        double radius = definition.radius();

        for (int i = 0; i < minionCount; i++) {
            double angle = (2.0 * Math.PI / Math.max(1, minionCount)) * i + (random.nextDouble() * 0.4 - 0.2);
            double dist = radius * (0.4 + 0.6 * random.nextDouble());
            double offsetX = Math.cos(angle) * dist;
            double offsetZ = Math.sin(angle) * dist;

            Location minionLoc = center.clone().add(offsetX, 0, offsetZ);
            ActiveMob minion = spawner.spawn(definition.minionMobId(), minionLoc);
            if (minion != null) {
                minion.setParentUUID(leader.entityId());
                minions.add(minion);
            }
        }

        return ClusterSpawnResult.successful(leader, minions);
    }
}
