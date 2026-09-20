package vn.haohan.lunar.api.world.hazard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.skill.target.TargetRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Central geometric tracker for all persistent ground hazard zones and traps.
 * Computes proximity mathematically without creating laggy server entities.
 */
public final class HazardZoneTracker {

    private final Map<UUID, HazardZone> activeZones = new ConcurrentHashMap<>();

    // Entity provider allows mocking in unit tests or defaults to Bukkit world entity search
    private Function<HazardZone, Collection<? extends LivingEntity>> entityProvider = this::defaultFindEntities;

    public void setEntityProvider(Function<HazardZone, Collection<? extends LivingEntity>> provider) {
        this.entityProvider = provider != null ? provider : this::defaultFindEntities;
    }

    public HazardZone createZone(UUID casterId, Location center, HazardZoneDefinition def, long currentTick) {
        Objects.requireNonNull(def, "Hazard zone definition must not be null");
        if (center == null) return null;

        UUID id = UUID.randomUUID();
        HazardZone zone = new HazardZone(id, casterId, center, def, currentTick);
        activeZones.put(id, zone);
        return zone;
    }

    public void registerZone(HazardZone zone) {
        if (zone != null) {
            activeZones.put(zone.zoneId(), zone);
        }
    }

    public Optional<HazardZone> getZone(UUID zoneId) {
        if (zoneId == null) return Optional.empty();
        return Optional.ofNullable(activeZones.get(zoneId));
    }

    public Collection<HazardZone> activeZones() {
        return Collections.unmodifiableCollection(List.copyOf(activeZones.values()));
    }

    public void tick(long currentTick, BiConsumer<String, TargetRef> skillDispatcher) {
        List<UUID> toRemove = new ArrayList<>();

        for (HazardZone zone : List.copyOf(activeZones.values())) {
            Collection<? extends LivingEntity> candidates = entityProvider.apply(zone);
            Map<UUID, LivingEntity> candidateMap = new HashMap<>();
            if (candidates != null) {
                for (LivingEntity e : candidates) {
                    if (e != null) {
                        try {
                            candidateMap.put(e.getUniqueId(), e);
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (zone.isExpired(currentTick)) {
                // Trigger onExit for remaining entities upon zone expiration
                if (zone.definition().onExitSkill() != null && !zone.definition().onExitSkill().isBlank() && skillDispatcher != null) {
                    for (UUID id : zone.entitiesInside()) {
                        TargetRef targetRef = resolveTargetRef(id, candidateMap);
                        if (targetRef != null) {
                            skillDispatcher.accept(zone.definition().onExitSkill(), targetRef);
                        }
                    }
                }
                toRemove.add(zone.zoneId());
                continue;
            }

            Set<UUID> foundInside = new HashSet<>();

            if (candidates != null) {
                for (LivingEntity entity : candidates) {
                    if (entity == null) continue;
                    try {
                        if (entity.isDead() || !entity.isValid()) continue;
                        if (!zone.contains(entity.getLocation())) continue;
                    } catch (Throwable ignored) {
                        continue;
                    }

                    UUID entityId = entity.getUniqueId();
                    foundInside.add(entityId);

                    boolean justEntered = zone.addInside(entityId);
                    if (justEntered) {
                        // onEnterSkill
                        if (zone.definition().onEnterSkill() != null && !zone.definition().onEnterSkill().isBlank() && skillDispatcher != null) {
                            skillDispatcher.accept(zone.definition().onEnterSkill(), TargetRef.of(entity));
                        }
                    } else {
                        // onTickSkill
                        if ((currentTick - zone.spawnTick()) % zone.definition().tickInterval() == 0) {
                            if (zone.definition().onTickSkill() != null && !zone.definition().onTickSkill().isBlank() && skillDispatcher != null) {
                                skillDispatcher.accept(zone.definition().onTickSkill(), TargetRef.of(entity));
                            }
                        }
                    }
                }
            }

            // Detect exited entities
            List<UUID> exited = new ArrayList<>();
            for (UUID insideId : zone.entitiesInside()) {
                if (!foundInside.contains(insideId)) {
                    exited.add(insideId);
                }
            }

            for (UUID exitId : exited) {
                zone.removeInside(exitId);
                if (zone.definition().onExitSkill() != null && !zone.definition().onExitSkill().isBlank() && skillDispatcher != null) {
                    TargetRef ref = resolveTargetRef(exitId, candidateMap);
                    if (ref != null) {
                        skillDispatcher.accept(zone.definition().onExitSkill(), ref);
                    }
                }
            }
        }

        for (UUID expiredId : toRemove) {
            activeZones.remove(expiredId);
        }
    }

    public void clear() {
        activeZones.clear();
    }

    private TargetRef resolveTargetRef(UUID entityId, Map<UUID, LivingEntity> candidateMap) {
        if (candidateMap != null && candidateMap.containsKey(entityId)) {
            LivingEntity living = candidateMap.get(entityId);
            if (living != null) {
                return TargetRef.of(living);
            }
        }
        try {
            var entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity living) {
                return TargetRef.of(living);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Collection<? extends LivingEntity> defaultFindEntities(HazardZone zone) {
        Location c = zone.center();
        World w = c.getWorld();
        if (w == null) return List.of();
        try {
            double r = zone.definition().radius();
            return w.getNearbyLivingEntities(c, r, r, r);
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}
