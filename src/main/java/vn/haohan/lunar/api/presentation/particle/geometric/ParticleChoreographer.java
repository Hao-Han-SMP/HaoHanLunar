package vn.haohan.lunar.core.presentation.particle.geometric;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Orchestrates geometric particle effects with strict per-tick particle budget limits
 * to prevent server TPS degradation and client FPS drop.
 */
public final class ParticleChoreographer {

    public static final int DEFAULT_MAX_PARTICLES_PER_TICK = 200;

    private int maxParticlesPerTick = DEFAULT_MAX_PARTICLES_PER_TICK;
    private long lastTick = -1;
    private int spawnedThisTick = 0;
    private ParticleSpawner particleSpawner = this::defaultSpawn;

    @FunctionalInterface
    public interface ParticleSpawner {
        void spawn(Location location, Particle particle, int count, double offX, double offY, double offZ, double extra);
    }

    public ParticleChoreographer() {}

    public ParticleChoreographer(int maxParticlesPerTick) {
        this.maxParticlesPerTick = Math.max(10, maxParticlesPerTick);
    }

    public void setParticleSpawner(ParticleSpawner spawner) {
        this.particleSpawner = spawner != null ? spawner : this::defaultSpawn;
    }

    public int getMaxParticlesPerTick() {
        return maxParticlesPerTick;
    }

    public void setMaxParticlesPerTick(int max) {
        this.maxParticlesPerTick = Math.max(10, max);
    }

    public int getSpawnedThisTick() {
        return spawnedThisTick;
    }

    private int checkAndAllocateBudget(int requestedCount, long currentTick) {
        if (currentTick != lastTick) {
            lastTick = currentTick;
            spawnedThisTick = 0;
        }

        int remaining = maxParticlesPerTick - spawnedThisTick;
        if (remaining <= 0) {
            return 0;
        }

        int granted = Math.min(requestedCount, remaining);
        spawnedThisTick += granted;
        return granted;
    }

    public int spawnHelix(Location origin, Particle particle, double radius, double height, int points, double rotations, long currentTick) {
        if (origin == null || particle == null) return 0;
        List<Vector> offsets = CurveMath.helix(radius, height, points, rotations);
        return spawnOffsetList(origin, particle, offsets, currentTick);
    }

    public int spawnRing(Location origin, Particle particle, double radius, int points, long currentTick) {
        if (origin == null || particle == null) return 0;
        List<Vector> offsets = CurveMath.ring(radius, points);
        return spawnOffsetList(origin, particle, offsets, currentTick);
    }

    public int spawnPolygon(Location origin, Particle particle, int sides, double radius, int pointsPerSide, long currentTick) {
        if (origin == null || particle == null) return 0;
        List<Vector> offsets = CurveMath.polygon(sides, radius, pointsPerSide);
        return spawnOffsetList(origin, particle, offsets, currentTick);
    }

    public int spawnLine(Location origin, Vector direction, Particle particle, double length, int points, long currentTick) {
        if (origin == null || particle == null) return 0;
        List<Vector> offsets = CurveMath.line(direction, length, points);
        return spawnOffsetList(origin, particle, offsets, currentTick);
    }

    public int spawnArc(Location origin, Location destination, Particle particle, double arcHeight, int points, long currentTick) {
        if (origin == null || destination == null || particle == null) return 0;
        Vector endOffset = destination.toVector().subtract(origin.toVector());
        List<Vector> offsets = CurveMath.arc(endOffset, arcHeight, points);
        return spawnOffsetList(origin, particle, offsets, currentTick);
    }

    private int spawnOffsetList(Location origin, Particle particle, List<Vector> offsets, long currentTick) {
        if (offsets == null || offsets.isEmpty()) return 0;

        int totalPoints = offsets.size();
        int budget = checkAndAllocateBudget(totalPoints, currentTick);
        if (budget <= 0) {
            return 0;
        }

        if (budget >= totalPoints) {
            for (Vector off : offsets) {
                Location loc = origin.clone().add(off);
                particleSpawner.spawn(loc, particle, 1, 0, 0, 0, 0);
            }
            return totalPoints;
        }

        // Downsample uniformly to respect budget without distorting the curve
        double step = (double) totalPoints / budget;
        for (int i = 0; i < budget; i++) {
            int idx = (int) Math.floor(i * step);
            if (idx >= totalPoints) idx = totalPoints - 1;
            Vector off = offsets.get(idx);
            Location loc = origin.clone().add(off);
            particleSpawner.spawn(loc, particle, 1, 0, 0, 0, 0);
        }
        return budget;
    }

    private void defaultSpawn(Location location, Particle particle, int count, double offX, double offY, double offZ, double extra) {
        if (location == null || location.getWorld() == null || particle == null) return;
        try {
            location.getWorld().spawnParticle(particle, location, count, offX, offY, offZ, extra);
        } catch (Throwable ignored) {}
    }
}
