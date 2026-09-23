package vn.haohan.lunar.core.subsystem.mob;

import com.ticxo.modelengine.api.ModelEngineAPI;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.manager.MobManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.ai.MobGoalApplier;
import vn.haohan.lunar.api.system.mob.ai.antistuck.AntiStuckController;
import vn.haohan.lunar.api.system.mob.equipment.EquipmentApplier;
import vn.haohan.lunar.api.system.mob.equipment.ItemProviderRegistry;
import vn.haohan.lunar.api.system.mob.scaling.MobLevelApplier;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.system.throttle.DynamicThrottlingEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Lifecycle manager for active custom mobs; it is event-driven and never scans every world per tick. */
public class LunarMobManager implements Listener, MobManager {

    private final Map<UUID, ActiveMob> activeMobs = new ConcurrentHashMap<>();
    private final Consumer<LivingEntity> modelCleanup;
    private final MobGoalApplier goalApplier = new MobGoalApplier();
    private final List<Consumer<ActiveMob>> unregisterCallbacks = new CopyOnWriteArrayList<>();
    private ItemProviderRegistry itemRegistry;

    private static void destroyModel(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        try {
            Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            var modeEntity = ModelEngineAPI.getModeledEntity(entity.getUniqueId());
            if (modeEntity != null) {
                modeEntity.destroy();
            }
        }
        catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // ModelEngine is optional; do nothing if it is not present
        }
    }

    public ItemProviderRegistry itemRegistry() {
        return itemRegistry;
    }

    public void setItemRegistry(ItemProviderRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public MobGoalApplier goalApplier() { return goalApplier; }

    public LunarMobManager() {
        this(LunarMobManager::destroyModel);
    }

    public LunarMobManager(Consumer<LivingEntity> modelCleanup) {
        this.modelCleanup = Objects.requireNonNull(modelCleanup, "Model cleanup callback must not be null");
    }

    public void addUnregisterCallback(Consumer<ActiveMob> callback) {
        if (callback != null) unregisterCallbacks.add(callback);
    }

    public ActiveMob register(LivingEntity entity, MobDefinition definition,
                                   String engineVersion, String spawnInstanceId) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(definition, "Mob definition must not be null");
        LunarMobIdentity identity = new LunarMobIdentity(definition.id().value(), engineVersion,
                java.util.Optional.ofNullable(spawnInstanceId));
        LunarMobIdentity.write(entity, identity);
        return register(new ActiveMob(entity, definition, identity));
    }

    @Override
    public int activeCount() {
        return activeMobs.size();
    }

    public ActiveMob get(UUID entityId) {
        return entityId == null ? null : activeMobs.get(entityId);
    }

    public Collection<ActiveMob> snapshot() {
        return Collections.unmodifiableCollection(java.util.List.copyOf(activeMobs.values()));
    }

    public Collection<ActiveMob> findByDefinitionId(String definitionId) {
        MobDefinitionId normalized = new MobDefinitionId(definitionId);
        return Collections.unmodifiableCollection(activeMobs.values().stream()
                .filter(active -> active.definitionId().equals(normalized))
                .toList());
    }

    public ActiveMob register(ActiveMob activeMob) {
        Objects.requireNonNull(activeMob, "Active mob must not be null");
        ActiveMob previous = activeMobs.putIfAbsent(activeMob.entityId(), activeMob);
        if (activeMob.entity() instanceof org.bukkit.entity.Mob mob) {
            goalApplier.apply(mob, activeMob.definition().aiGoalSelectors(), activeMob.definition().aiTargetSelectors(), activeMob.threatTable(), () -> this);
        }
        if (activeMob.definition().equipment() != null && !activeMob.definition().equipment().isEmpty()) {
            try {
                EquipmentApplier.applyEquipment(activeMob.entity(), activeMob.definition().equipment(), itemRegistry);
            }
            catch (Throwable ignored) {
            }
        }
        if (activeMob.definition().levelScaling() != null && activeMob.definition().levelScaling().isPresent()) {
            try {
                var scaling = activeMob.definition().levelScaling().get();
                int level = scaling.rollLevel();
                MobLevelApplier.applyScaling(activeMob.entity(), scaling, level);
            }
            catch (Throwable ignored) {
            }
        }
        if (previous != null) {
            throw new IllegalStateException("Entity is already registered as an active mob: " + activeMob.entityId());
        }
        return activeMob;
    }

    @Override
    public ActiveMob unregister(UUID entityId) {
        if (entityId == null) {
            return null;
        }
        ActiveMob removed = activeMobs.remove(entityId);
        if (removed != null) {
            cleanupModel(removed.entity());
            if (removed.bossBars() != null) {
                removed.bossBars().removeAll();
            }
        }
        return removed;
    }

    // --- LunarMobManager API Implementation ---
    @Override
    public Optional<ActiveMob> getMob(UUID entityUuid) {
        return Optional.ofNullable(get(entityUuid));
    }

    @Override
    public Collection<ActiveMob> getActiveMobs() {
        return snapshot();
    }

    @Override
    public boolean isManaged(UUID entityUuid) {
        return entityUuid != null && activeMobs.containsKey(entityUuid);
    }

    /**
     * Refreshes active definitions for living mobs without desyncing their UUIDs, health, or threat tables.
     */
    public int refreshActiveDefinitions(MobDefinitionRegistry registry) {
        if (registry == null) return 0;
        int refreshed = 0;
        for (ActiveMob mob : activeMobs.values()) {
            Optional<MobDefinition> newDef = registry.get(mob.definitionId().value());
            if (newDef.isPresent()) {
                mob.updateDefinition(newDef.get());
                refreshed++;
            }
        }
        return refreshed;
    }

    /** Removes invalid entities from the manager map without scanning loaded worlds. */
    public int cleanupInvalidEntities() {
        int removed = 0;
        for (ActiveMob activeMob : activeMobs.values()) {
            LivingEntity entity = activeMob.entity();
            if (!entity.isValid() || entity.isDead()) {
                if (activeMobs.remove(activeMob.entityId(), activeMob)) {
                    cleanupModel(entity);
                    if (activeMob.bossBars() != null) {
                        activeMob.bossBars().removeAll();
                    }
                    for (Consumer<ActiveMob> cb : unregisterCallbacks) {
                        try {
                            cb.accept(activeMob);
                        }
                        catch (Throwable ignored) {
                        }
                    }
                    removed++;
                }
            }
        }
        return removed;
    }

    public int cleanupAll() {
        int removed = 0;
        for (ActiveMob activeMob : activeMobs.values()) {
            if (activeMobs.remove(activeMob.entityId(), activeMob)) {
                cleanupModel(activeMob.entity());
                if (activeMob.bossBars() != null) {
                    activeMob.bossBars().removeAll();
                }
                removed++;
            }
        }
        return removed;
    }

    /**
     * Periodic tick for all active mobs.
     * Uses Level of Detail (LOD) via DynamicThrottlingEngine to throttle distant or non-combat mobs.
     */
    public void tick(long currentTick, DynamicThrottlingEngine throttlingEngine) {
        for (ActiveMob mob : activeMobs.values()) {
            LivingEntity entity = mob.entity();
            if (entity == null || !entity.isValid() || entity.isDead()) {
                continue;
            }

            // Boss leash check & reset altar / spawn point
            if (mob.options() != null && mob.options().leashRange() > 0.0 && mob.spawnLocation() != null) {
                Location spawn = mob.spawnLocation();
                if (spawn.getWorld() != null && entity.getWorld() != null) {
                    if (!Objects.equals(spawn.getWorld(), entity.getWorld())) {
                        mob.resetToSpawn();
                        continue;
                    }
                    double distSq = entity.getLocation().distanceSquared(spawn);
                    double hardSq = mob.options().leashRange() * mob.options().leashRange();
                    if (distSq > hardSq) {
                        mob.resetToSpawn();
                        continue;
                    } else {
                        double softRadius = mob.options().softLeashRadius();
                        if (softRadius > 0.0 && distSq > softRadius * softRadius) {
                            mob.setSoftLeashed(true);
                        } else if (mob.isSoftLeashed()) {
                            mob.setSoftLeashed(false);
                        }
                    }
                }
            }

            // CC processing: cleanup expired and restrain movement if stunned or rooted
            if (mob.crowdControl() != null) {
                mob.crowdControl().cleanupExpired();
                if (mob.crowdControl().isStunned() || mob.crowdControl().isRooted()) {
                    try {
                        Vector vel = entity.getVelocity();
                        if (vel.getX() != 0 || vel.getZ() != 0) {
                            entity.setVelocity(new Vector(0, vel.getY() > 0 ? 0 : vel.getY(), 0));
                        }
                    }
                    catch (Throwable ignored) {
                    }
                }
            }

            // Periodic threat decay (once per second / 20 ticks)
            if (mob.threatTable() != null && currentTick % 20 == 0) {
                mob.threatTable().tickDecay(currentTick, 20L);
                var topOpt = mob.threatTable().evaluateTarget(currentTick);
                if (topOpt.isPresent()) {
                    UUID targetId = topOpt.get();
                    try {
                        Entity targetEnt = Bukkit.getEntity(targetId);
                        if (targetEnt == null || !targetEnt.isValid() || targetEnt.isDead()) {
                            mob.threatTable().removeTarget(targetId);
                            if (entity instanceof Mob m && m.getTarget() != null && targetId.equals(m.getTarget().getUniqueId())) {
                                m.setTarget(null);
                            }
                        } else if (!Objects.equals(targetEnt.getWorld(), entity.getWorld()) || entity.getLocation().distanceSquared(targetEnt.getLocation()) > 2304.0) {
                            // Target departed world or fled > 48 blocks away: soft de-aggro
                            mob.threatTable().removeTarget(targetId);
                            if (entity instanceof Mob m && m.getTarget() != null && targetId.equals(m.getTarget().getUniqueId())) {
                                m.setTarget(null);
                            }
                        }
                    }
                    catch (Throwable ignored) {
                    }
                }
            }

            boolean inCombat = mob.threatTable() != null && !mob.threatTable().isEmpty();
            if (throttlingEngine != null) {
                if (!throttlingEngine.shouldTickMobAI(inCombat, 16.0, currentTick)) {
                    continue;
                }
            }

            // Anti-Stuck navigation during combat
            if (inCombat && mob.antiStuckController() != null) {
                Location targetLoc = null;
                if (entity instanceof Mob m && m.getTarget() != null) {
                    targetLoc = m.getTarget().getLocation();
                } else if (mob.threatTable() != null) {
                    var topThreatId = mob.threatTable().topTarget();
                    if (topThreatId.isPresent()) {
                        Entity targetEnt = Bukkit.getEntity(topThreatId.get());
                        if (targetEnt != null) targetLoc = targetEnt.getLocation();
                    }
                }
                if (targetLoc != null) {
                    var actionOpt = mob.antiStuckController().tick(entity.getLocation(), targetLoc, currentTick);
                    if (actionOpt.isPresent()) {
                        applyAntiStuckAction(entity, actionOpt.get());
                    }
                }
            }

            if (mob.bossBars() != null && !mob.bossBars().allBars().isEmpty()) {
                mob.bossBars().tick(entity.getLocation(), entity.getWorld().getPlayers());
            }
        }
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

    private void applyAntiStuckAction(LivingEntity entity, AntiStuckController.AntiStuckAction action) {
        if (entity == null || !entity.isValid()) return;
        switch (action.level()) {
            case LEVEL_1_JUMP -> {
                Vector vel = action.suggestedVelocity();
                if (vel != null) {
                    entity.setVelocity(vel);
                } else {
                    entity.setVelocity(new Vector(0, 0.45, 0));
                }
            }
            case LEVEL_2_RECALCULATE -> {
                if (entity instanceof Mob m && action.targetLocation() != null) {
                    try {
                        m.getPathfinder().moveTo(action.targetLocation(), 1.25);
                    }
                    catch (Throwable ignored) {
                    }
                }
            }
            case LEVEL_3_CLEAR_OBSTRUCTION -> {
                Vector vel = action.suggestedVelocity();
                if (vel != null) {
                    entity.setVelocity(vel.clone().setY(0.4));
                }
            }
            case LEVEL_4_TELEPORT -> {
                Location tpLoc = action.suggestedTeleportLocation();
                if (tpLoc != null && tpLoc.getWorld() != null) {
                    entity.teleport(tpLoc);
                }
            }
        }
    }
}
