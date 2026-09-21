package vn.haohan.lunar.api.system.spawner.random;

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
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Controller and rate-limiter for random mob spawning and replacement in the Lunar dimension.
 * Defaults to disabled for server stability and test safety.
 */
public final class RandomSpawnManager implements Listener {

    private final MobDefinitionRegistry mobDefinitions;
    private final LunarMobManager mobManager;
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

    public RandomSpawnManager(MobDefinitionRegistry mobDefinitions, LunarMobManager mobManager) {
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

        totalAttempts.incrementAndGet();

        // 1. Check mob cap
        if (isMobCapReached(world)) {
            mobCapSkips.incrementAndGet();
            return;
        }

        // 2. Evaluate rules
        String biomeKey = resolveBiomeKey(loc);

        for (RandomSpawnRule rule : getActiveRules(null)) {
            if (!rule.matchesWorld(world)) {
                continue;
            }
            if (!rule.matchesBiome(biomeKey)) {
                continue;
            }
            if (!rule.matchesElevation(loc.getY())) {
                continue;
            }
            if (!rule.matchesReason(reason)) {
                continue;
            }

            // Roll chance
            if (!rule.rollChance(random)) {
                continue;
            }

            switch (rule.action()) {
                case DENY -> {
                    event.setCancelled(true);
                    deniedSpawns.incrementAndGet();
                    return;
                }
                case REPLACE -> {
                    event.setCancelled(true);
                    deniedSpawns.incrementAndGet();
                    spawnCustomMob(rule.mobId(), loc, null);
                    successfulSpawns.incrementAndGet();
                    return;
                }
                case ADD -> {
                    spawnCustomMob(rule.mobId(), loc, null);
                    successfulSpawns.incrementAndGet();
                    return;
                }
            }
        }
    }

    public boolean isMobCapReached(World world) {
        if (mobManager != null && mobManager.activeCount() >= mobCapLimit) {
            return true;
        }
        if (world != null) {
            try {
                return world.getLivingEntities().size() >= mobCapLimit;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
    private void spawnCustomMob(String mobId, Location location, Vector velocity) {
        if (mobId == null || location == null) {
            return;
        }

        if (customSpawner != null) {
            try {
                customSpawner.accept(mobId, location);
                return;
            } catch (Throwable ignored) {
            }
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
