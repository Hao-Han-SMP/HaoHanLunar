package vn.haohan.lunar.api.spawner.random;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.Vector;
import vn.haohan.lunar.core.subsystem.mob.MobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Controller and rate-limiter for random mob spawning and replacement in the Lunar dimension.
 * Defaults to disabled for server stability and test safety.
 */
public final class RandomSpawnManager implements Listener {

    private final MobDefinitionRegistry mobDefinitions;
    private final MobManager mobManager;
    private final Map<String, RandomSpawnRule> rules = new ConcurrentHashMap<>();

    private boolean globalEnabled = false;
    private int maxAttemptsPerTick = 5;
    private int mobCapLimit = 70;
    private Random random = new Random();

    private final AtomicLong totalAttempts = new AtomicLong(0);
    private final AtomicLong successfulSpawns = new AtomicLong(0);
    private final AtomicLong deniedSpawns = new AtomicLong(0);
    private final AtomicLong mobCapSkips = new AtomicLong(0);

    private BiConsumer<String, Location> customSpawner;

    public RandomSpawnManager(MobDefinitionRegistry mobDefinitions, MobManager mobManager) {
        this.mobDefinitions = mobDefinitions;
        this.mobManager = mobManager;
    }

    public RandomSpawnManager() {
        this(null, null);
    }

    public void registerRule(RandomSpawnRule rule) {
        Objects.requireNonNull(rule, "Rule must not be null");
        rules.put(rule.id(), rule);
    }

    public Optional<RandomSpawnRule> unregisterRule(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(rules.remove(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<RandomSpawnRule> getRule(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(rules.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public List<RandomSpawnRule> getActiveRules(SpawnAction action) {
        return rules.values().stream()
                .filter(RandomSpawnRule::enabled)
                .filter(r -> action == null || r.action() == action)
                .sorted(Comparator.comparingInt(RandomSpawnRule::priority).reversed())
                .toList();
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean globalEnabled) {
        this.globalEnabled = globalEnabled;
    }

    public int getMobCapLimit() {
        return mobCapLimit;
    }

    public void setMobCapLimit(int mobCapLimit) {
        this.mobCapLimit = Math.max(1, mobCapLimit);
    }

    public int getMaxAttemptsPerTick() {
        return maxAttemptsPerTick;
    }

    public void setMaxAttemptsPerTick(int maxAttemptsPerTick) {
        this.maxAttemptsPerTick = Math.max(1, maxAttemptsPerTick);
    }

    public void setRandom(Random random) {
        this.random = random != null ? random : new Random();
    }

    public void setCustomSpawner(BiConsumer<String, Location> customSpawner) {
        this.customSpawner = customSpawner;
    }

    public long getTotalAttempts() {
        return totalAttempts.get();
    }

    public long getSuccessfulSpawns() {
        return successfulSpawns.get();
    }

    public long getDeniedSpawns() {
        return deniedSpawns.get();
    }

    public long getMobCapSkips() {
        return mobCapSkips.get();
    }

    public void resetMetrics() {
        totalAttempts.set(0);
        successfulSpawns.set(0);
        deniedSpawns.set(0);
        mobCapSkips.set(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!globalEnabled) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        // Ignore artificial spawn reasons
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }

        Location loc = event.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        String biomeKey = resolveBiomeKey(loc);
        double y = loc.getY();

        // 1. Check DENY rules first (e.g. forbid vanilla mobs in lunar dimensions or forbidden biomes)
        List<RandomSpawnRule> denyRules = getActiveRules(SpawnAction.DENY);
        for (RandomSpawnRule rule : denyRules) {
            if (!rule.matchesWorld(world)) continue;
            if (!rule.matchesBiome(biomeKey)) continue;
            if (!rule.matchesElevation(y)) continue;
            if (!rule.matchesReason(reason)) continue;

            if (rule.rollChance(random)) {
                totalAttempts.incrementAndGet();
                deniedSpawns.incrementAndGet();
                event.setCancelled(true);
                return;
            }
        }

        // 2. Check REPLACE rules
        List<RandomSpawnRule> replaceRules = getActiveRules(SpawnAction.REPLACE);
        for (RandomSpawnRule rule : replaceRules) {
            if (!rule.matchesWorld(world)) continue;
            if (!rule.matchesBiome(biomeKey)) continue;
            if (!rule.matchesElevation(y)) continue;
            if (!rule.matchesReason(reason)) continue;

            totalAttempts.incrementAndGet();

            if (isMobCapReached(world)) {
                mobCapSkips.incrementAndGet();
                return;
            }

            if (rule.rollChance(random)) {
                event.setCancelled(true);

                // Preserve yaw, pitch, velocity if available
                Location spawnLoc = loc.clone();
                Vector velocity = null;
                Entity vanilla = event.getEntity();
                if (vanilla != null) {
                    spawnLoc.setYaw(vanilla.getLocation().getYaw());
                    spawnLoc.setPitch(vanilla.getLocation().getPitch());
                    try {
                        velocity = vanilla.getVelocity();
                    } catch (Throwable ignored) {}
                }

                spawnMob(rule.mobId(), spawnLoc, velocity);
                successfulSpawns.incrementAndGet();
                return;
            }
        }
    }

    public boolean isMobCapReached(World world) {
        if (world == null) return false;
        try {
            int count = world.getLivingEntities().size();
            return count >= mobCapLimit;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void spawnMob(String mobId, Location location, Vector velocity) {
        if (customSpawner != null) {
            customSpawner.accept(mobId, location);
            return;
        }

        if (mobDefinitions == null || mobManager == null || location.getWorld() == null) {
            return;
        }

        MobDefinition def = mobDefinitions.get(mobId).orElse(null);
        if (def == null) {
            return;
        }

        try {
            Entity entity = location.getWorld().spawn(location, def.entityType().getEntityClass());
            if (entity instanceof LivingEntity living) {
                if (velocity != null) {
                    living.setVelocity(velocity);
                }
                mobManager.register(living, def, "1.0.0", null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String resolveBiomeKey(Location loc) {
        try {
            Block block = loc.getBlock();
            if (block != null) {
                Biome biome = block.getBiome();
                if (biome != null) {
                    try {
                        return biome.getKey().toString();
                    } catch (Throwable ignored) {
                        return String.valueOf(biome);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "plains";
    }
}
