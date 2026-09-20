package vn.haohan.lunar.core.subsystem.mob;

import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.ai.MobGoalApplier;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lifecycle manager for active custom mobs; it is event-driven and never scans every world per tick. */
public final class LunarMobManager implements vn.haohan.lunar.api.manager.LunarMobManager, Listener {

    private final Map<UUID, ActiveLunarMob> activeMobs = new ConcurrentHashMap<>();
    private final Consumer<LivingEntity> modelCleanup;
    private final MobGoalApplier goalApplier = new MobGoalApplier();

    public MobGoalApplier goalApplier() { return goalApplier; }

    public LunarMobManager() {
        this(LunarMobManager::destroyModel);
    }

    public LunarMobManager(Consumer<LivingEntity> modelCleanup) {
        this.modelCleanup = Objects.requireNonNull(modelCleanup, "Model cleanup callback must not be null");
    }

    public ActiveLunarMob register(ActiveLunarMob activeMob) {
        Objects.requireNonNull(activeMob, "Active mob must not be null");
        ActiveLunarMob previous = activeMobs.putIfAbsent(activeMob.entityId(), activeMob);
        if (activeMob.entity() instanceof org.bukkit.entity.Mob mob) {
            goalApplier.apply(mob, activeMob.definition().aiGoalSelectors(), activeMob.definition().aiTargetSelectors());
        }
        if (previous != null) {
            throw new IllegalStateException("Entity is already registered as an active mob: " + activeMob.entityId());
        }
        return activeMob;
    }

    public ActiveLunarMob register(LivingEntity entity, MobDefinition definition,
                                   String engineVersion, String spawnInstanceId) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(definition, "Mob definition must not be null");
        LunarMobIdentity identity = new LunarMobIdentity(definition.id().value(), engineVersion,
                java.util.Optional.ofNullable(spawnInstanceId));
        LunarMobIdentity.write(entity, identity);
        return register(new ActiveLunarMob(entity, definition, identity));
    }

    @Override
    public int activeCount() {
        return activeMobs.size();
    }

    public ActiveLunarMob get(UUID entityId) {
        return entityId == null ? null : activeMobs.get(entityId);
    }

    public Collection<ActiveLunarMob> snapshot() {
        return Collections.unmodifiableCollection(java.util.List.copyOf(activeMobs.values()));
    }

    public Collection<ActiveLunarMob> findByDefinitionId(String definitionId) {
        MobDefinitionId normalized = new MobDefinitionId(definitionId);
        return Collections.unmodifiableCollection(activeMobs.values().stream()
                .filter(active -> active.definitionId().equals(normalized))
                .toList());
    }

    @Override
    public ActiveLunarMob unregister(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        ActiveLunarMob removed = activeMobs.remove(entityId);
        if (removed != null) {
            removed.bossBars().removeAll();
            cleanupModel(removed.entity());
        }
        return removed;
    }

    /** Removes invalid entities from the manager map without scanning loaded worlds. */
    public int cleanupInvalidEntities() {
        int removed = 0;
        for (ActiveLunarMob activeMob : activeMobs.values()) {
            LivingEntity entity = activeMob.entity();
            if (!entity.isValid() || entity.isDead()) {
                if (activeMobs.remove(activeMob.entityId(), activeMob)) {
                    cleanupModel(entity);
                    removed++;
                }
            }
        }
        return removed;
    }

    public int cleanupAll() {
        int removed = 0;
        for (ActiveLunarMob activeMob : activeMobs.values()) {
            if (activeMobs.remove(activeMob.entityId(), activeMob)) {
                cleanupModel(activeMob.entity());
                removed++;
            }
        }
        return removed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event != null) {
            unregister(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (event == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                unregister(entity.getUniqueId());
            }
        }
    }

    private void cleanupModel(LivingEntity entity) {
        try {
            modelCleanup.accept(entity);
        } catch (RuntimeException ignored) {
            // Cleanup must continue for the remaining entities even if an optional model adapter fails.
        }
    }

    private static void destroyModel(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        var modeledEntity = ModelEngineAPI.getModeledEntity(entity);
        if (modeledEntity != null) {
            modeledEntity.destroy();
        }
    }

    // --- LunarMobManager API Implementation ---
    @Override
    public Optional<ActiveLunarMob> getMob(UUID entityUuid) {
        return Optional.ofNullable(get(entityUuid));
    }

    @Override
    public Collection<ActiveLunarMob> getActiveMobs() {
        return snapshot();
    }

    @Override
    public boolean isManaged(UUID entityUuid) {
        return entityUuid != null && activeMobs.containsKey(entityUuid);
    }
}
