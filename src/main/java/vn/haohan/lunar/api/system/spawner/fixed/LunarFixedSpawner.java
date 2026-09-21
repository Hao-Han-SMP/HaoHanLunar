package vn.haohan.lunar.api.system.spawner.fixed;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Runtime controller for an active fixed-location mob spawner.
 * Handles cooldown, warmup, max mob limits, proximity activation, soft/hard leashing,
 * condition checks, and dynamic respawn waves.
 */
public final class LunarFixedSpawner {

    private final SpawnerDefinition definition;
    private final Set<UUID> trackedMobs = ConcurrentHashMap.newKeySet();

    private int currentWarmupTicks;
    private int currentCooldownTicks;
    private boolean active;

    private int currentWaveIndex = 0;
    private boolean waveInProgress = false;
    private Predicate<SpawnerDefinition> conditionEvaluator = def -> true;

    public LunarFixedSpawner(SpawnerDefinition definition) {
        this.definition = Objects.requireNonNull(definition, "Spawner definition must not be null");
        this.currentWarmupTicks = definition.warmupSeconds() * 20;
        this.currentCooldownTicks = definition.cooldownSeconds() * 20;
    }

    public LunarFixedSpawner(SpawnerDefinition definition, Object mobDefinitions, LunarMobManager mobManager) {
        this(definition);
    }

    public SpawnerDefinition definition() {
        return definition;
    }

    public String id() {
        return definition.id();
    }

    public Set<UUID> trackedMobs() {
        return Collections.unmodifiableSet(trackedMobs);
    }

    public int activeMobCount() {
        return trackedMobs.size();
    }

    public boolean isTracked(UUID mobId) {
        return mobId != null && trackedMobs.contains(mobId);
    }

    public void attachTrackedMob(UUID mobId) {
        if (mobId != null) {
            trackedMobs.add(mobId);
        }
    }

    public void untrackMob(UUID mobId) {
        if (mobId != null) {
            trackedMobs.remove(mobId);
        }
    }

    public boolean isActive() {
        return active;
    }

    public int currentWarmupTicks() {
        return currentWarmupTicks;
    }

    public int currentCooldownTicks() {
        return currentCooldownTicks;
    }

    public void setCooldownTicks(int ticks) {
        this.currentCooldownTicks = Math.max(0, ticks);
    }

    public void setWarmupTicks(int ticks) {
        this.currentWarmupTicks = Math.max(0, ticks);
    }

    public void resetCooldown() {
        this.currentCooldownTicks = definition.cooldownSeconds() * 20;
    }

    public void resetWarmup() {
        this.currentWarmupTicks = definition.warmupSeconds() * 20;
    }

    public int currentWaveIndex() {
        return currentWaveIndex;
    }

    public void setConditionEvaluator(Predicate<SpawnerDefinition> conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator != null ? conditionEvaluator : def -> true;
    }

    /**
     * Executes one tick of spawner logic.
     */
    public void tick(long tickNumber,
                     LunarMobManager mobManager,
                     SpawnerCallback spawnerCallback,
                     ProximityChecker proximityChecker) {
        // 1. Prune confirmed dead or invalid mobs
        if (mobManager != null) {
            trackedMobs.removeIf(uuid -> {
                ActiveMob mob = mobManager.get(uuid);
                return mob != null && (mob.entity().isDead() || !mob.entity().isValid());
            });
        }

        // 2. Leash check for living tracked mobs (Soft & Hard Leash)
        if (definition.hardLeashRadius() > 0 && mobManager != null) {
            double hardSq = definition.hardLeashRadius() * definition.hardLeashRadius();
            double softSq = definition.softLeashRadius() * definition.softLeashRadius();
            Location spawnLoc = definition.location();

            for (UUID uuid : trackedMobs) {
                ActiveMob mob = mobManager.get(uuid);
                if (mob == null) continue;
                LivingEntity entity = mob.entity();
                if (entity == null || entity.isDead() || !entity.isValid()) continue;

                Location loc = entity.getLocation();
                if (loc.getWorld() != null && loc.getWorld().equals(spawnLoc.getWorld())) {
                    double distSq = loc.distanceSquared(spawnLoc);

                    // Hard Leash breach: force teleport back, 100% full heal, clear threats
                    if (distSq > hardSq) {
                        entity.teleport(spawnLoc);
                        mob.setSoftLeashed(false);
                        try {
                            if (spawnLoc.getWorld() != null) {
                                spawnLoc.getWorld().spawnParticle(org.bukkit.Particle.REVERSE_PORTAL, spawnLoc, 20, 0.5, 1.0, 0.5, 0.05);
                                spawnLoc.getWorld().playSound(spawnLoc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                            }
                        }
                        catch (Throwable ignored) {
                        }
                        if (definition.healOnLeash()) {
                            try {
                                entity.setHealth(entity.getMaxHealth());
                            } catch (Throwable ignored) {
                            }
                        }
                        if (definition.resetThreatOnLeash()) {
                            if (entity instanceof Mob m) {
                                try {
                                    m.setTarget(null);
                                } catch (Throwable ignored) {
                                }
                            }
                            mob.threatTable().clear();
                        }
                    } else if (distSq > softSq) {
                        // Soft Leash breach: apply 90% resistance, refuse chase, return home
                        mob.setSoftLeashed(true);
                        if (entity instanceof Mob m) {
                            try {
                                m.setTarget(null);
                            } catch (Throwable ignored) {
                            }
                        }
                    } else {
                        // Safely within soft boundary
                        mob.setSoftLeashed(false);
                    }
                }
            }
        }

        // 3. Proximity activation check
        if (definition.activationRange() > 0 && proximityChecker != null) {
            this.active = proximityChecker.hasPlayerNearby(definition.location(), definition.activationRange());
            if (!this.active) {
                return; // Paused while player is too far
            }
        } else {
            this.active = true;
        }

        // 4. Environmental Condition check (e.g. lunar phase, time, etc.)
        if (!conditionEvaluator.test(definition)) {
            return; // Conditions not satisfied, pause countdown
        }

        // 5. Warmup countdown
        if (currentWarmupTicks > 0) {
            currentWarmupTicks--;
            return;
        }

        // 6. Cooldown countdown
        if (currentCooldownTicks > 0) {
            currentCooldownTicks--;
            return;
        }

        // 7. Multi-wave spawning logic
        if (definition.hasWaves()) {
            if (waveInProgress) {
                if (trackedMobs.isEmpty()) {
                    // Current wave eliminated! Advance wave
                    waveInProgress = false;
                    currentWaveIndex++;
                    if (currentWaveIndex >= definition.waves().size()) {
                        // All waves cleared! Reset sequence and enter cooldown
                        currentWaveIndex = 0;
                        resetCooldown();
                    }
                }
                return;
            }

            // Spawn current wave
            if (currentWaveIndex < definition.waves().size() && spawnerCallback != null) {
                SpawnerWave wave = definition.waves().get(currentWaveIndex);
                for (SpawnerWave.WaveEntry entry : wave.entries()) {
                    for (int i = 0; i < entry.count(); i++) {
                        spawnerCallback.spawn(entry.mobId(), definition.location().clone(), definition.id())
                                .ifPresent(this::attachTrackedMob);
                    }
                }
                waveInProgress = true;
            }
            return;
        }

        // 8. Standard single-mob spawn check
        int currentCount = trackedMobs.size();
        if (currentCount >= definition.maxMobs()) {
            return; // At capacity
        }

        int toSpawn = Math.min(definition.mobsPerSpawn(), definition.maxMobs() - currentCount);
        if (toSpawn > 0 && spawnerCallback != null) {
            for (int i = 0; i < toSpawn; i++) {
                spawnerCallback.spawn(definition.mobId(), definition.location().clone(), definition.id())
                        .ifPresent(this::attachTrackedMob);
            }
            resetCooldown();
        }
    }

    @FunctionalInterface
    public interface SpawnerCallback {
        Optional<UUID> spawn(String mobId, Location location, String spawnerId);
    }

    @FunctionalInterface
    public interface ProximityChecker {
        boolean hasPlayerNearby(Location location, double range);
    }
}
