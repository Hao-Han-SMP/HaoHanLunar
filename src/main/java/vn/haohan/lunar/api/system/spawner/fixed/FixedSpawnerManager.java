package vn.haohan.lunar.api.system.spawner.fixed;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.lunar.api.manager.SpawnerManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centrally manages all active fixed spawners on the server.
 */
public final class FixedSpawnerManager implements SpawnerManager, Listener {

    private static final NamespacedKey SPAWNER_ID_KEY = new NamespacedKey("haohan", "spawner_id");

    private final MobDefinitionRegistry mobDefinitions;
    private final LunarMobManager mobManager;
    private final Map<String, LunarFixedSpawner> spawners = new ConcurrentHashMap<>();

    private LunarFixedSpawner.SpawnerCallback customSpawnerCallback;
    private LunarFixedSpawner.ProximityChecker customProximityChecker;

    public FixedSpawnerManager(MobDefinitionRegistry mobDefinitions, LunarMobManager mobManager) {
        this.mobDefinitions = mobDefinitions;
        this.mobManager = mobManager;
    }

    public FixedSpawnerManager() {
        this(null, null);
    }

    public void register(SpawnerDefinition definition) {
        Objects.requireNonNull(definition, "Spawner definition must not be null");
        spawners.put(definition.id(), new LunarFixedSpawner(definition, mobDefinitions, mobManager));
    }

    public void register(LunarFixedSpawner spawner) {
        Objects.requireNonNull(spawner, "Spawner must not be null");
        spawners.put(spawner.id(), spawner);
    }

    public void replaceAll(java.util.Collection<SpawnerDefinition> definitions) {
        Objects.requireNonNull(definitions, "Spawner definitions must not be null");
        Map<String, LunarFixedSpawner> newSpawners = new ConcurrentHashMap<>();
        for (SpawnerDefinition def : definitions) {
            newSpawners.put(def.id(), new LunarFixedSpawner(def, mobDefinitions, mobManager));
        }
        spawners.clear();
        spawners.putAll(newSpawners);
    }

    public boolean hasSpawner(String id) {
        if (id == null) return false;
        return spawners.containsKey(id.trim().toLowerCase(Locale.ROOT));
    }

    public Optional<LunarFixedSpawner> unregister(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(spawners.remove(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Optional<LunarFixedSpawner> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(spawners.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    public Map<String, LunarFixedSpawner> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(spawners));
    }

    public Map<String, LunarFixedSpawner.SpawnerState> captureAllStates() {
        Map<String, LunarFixedSpawner.SpawnerState> states = new LinkedHashMap<>();
        spawners.forEach((id, spawner) -> states.put(id, spawner.captureState()));
        return Collections.unmodifiableMap(states);
    }

    public void restoreAllStates(Map<String, LunarFixedSpawner.SpawnerState> states) {
        if (states == null || states.isEmpty()) return;
        states.forEach((id, state) -> {
            LunarFixedSpawner spawner = spawners.get(id.toLowerCase(Locale.ROOT));
            if (spawner != null) {
                spawner.restoreState(state);
            }
        });
    }

    public void setCustomSpawnerCallback(LunarFixedSpawner.SpawnerCallback callback) {
        this.customSpawnerCallback = callback;
    }

    public void setCustomProximityChecker(LunarFixedSpawner.ProximityChecker checker) {
        this.customProximityChecker = checker;
    }

    /**
     * Ticks all registered fixed spawners centrally on the server main thread.
     */
    public void tickAll(long tickNumber) {
        LunarFixedSpawner.SpawnerCallback callback = customSpawnerCallback != null
                ? customSpawnerCallback
                : this::defaultSpawn;

        LunarFixedSpawner.ProximityChecker proximity = customProximityChecker != null
                ? customProximityChecker
                : this::defaultProximityCheck;

        for (LunarFixedSpawner spawner : spawners.values()) {
            try {
                spawner.tick(tickNumber, mobManager, callback, proximity);
            } catch (Throwable ignored) {
            }
        }
    }

    private Optional<UUID> defaultSpawn(String mobId, Location location, String spawnerId) {
        if (mobDefinitions == null || mobManager == null || location == null) {
            return Optional.empty();
        }
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        MobDefinition definition = mobDefinitions.get(mobId).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }

        try {
            Entity entity = world.spawn(location, definition.entityType().getEntityClass(), spawned -> {
                if (spawned instanceof LivingEntity living) {
                    PersistentDataContainer pdc = living.getPersistentDataContainer();
                    pdc.set(SPAWNER_ID_KEY, PersistentDataType.STRING, spawnerId != null ? spawnerId : "");
                }
            });

            if (entity instanceof LivingEntity living) {
                ActiveMob activeMob = mobManager.register(living, definition, "1.0.0", spawnerId);
                return Optional.of(activeMob.entityId());
            }
        } catch (Throwable ignored) {
        }

        return Optional.empty();
    }

    private boolean defaultProximityCheck(Location center, double radius) {
        if (center == null || center.getWorld() == null || radius <= 0.0) {
            return true;
        }
        double radiusSq = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (!player.isValid() || player.isDead()) continue;
            GameMode mode = player.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID entityId = entity.getUniqueId();

        String spawnerId = null;
        try {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            spawnerId = pdc.get(SPAWNER_ID_KEY, PersistentDataType.STRING);
        } catch (Throwable ignored) {
        }

        if (spawnerId != null) {
            get(spawnerId).ifPresent(s -> s.untrackMob(entityId));
        }
    }

    // --- SpawnerManager API Implementation ---
    @Override
    public int activeSpawnerCount() {
        return spawners.size();
    }

    @Override
    public void tick() {
        tickAll(System.currentTimeMillis() / 50L);
    }
}
