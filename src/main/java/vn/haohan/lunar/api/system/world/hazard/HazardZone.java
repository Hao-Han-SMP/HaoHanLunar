package vn.haohan.lunar.api.world.hazard;

import org.bukkit.Location;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geometric, non-entity representation of an active ground hazard zone or trap.
 */
public final class HazardZone {

    private final UUID zoneId;
    private final UUID casterId;
    private final Location center;
    private final HazardZoneDefinition definition;
    private final long spawnTick;
    private final long expireTick;
    private final double radiusSquared;
    private final Set<UUID> entitiesInside = ConcurrentHashMap.newKeySet();

    public HazardZone(
            UUID zoneId,
            UUID casterId,
            Location center,
            HazardZoneDefinition definition,
            long spawnTick
    ) {
        this.zoneId = zoneId != null ? zoneId : UUID.randomUUID();
        this.casterId = casterId;
        this.center = Objects.requireNonNull(center, "Center location must not be null").clone();
        this.definition = Objects.requireNonNull(definition, "Hazard zone definition must not be null");
        this.spawnTick = spawnTick;
        this.expireTick = spawnTick + definition.durationTicks();
        this.radiusSquared = definition.radius() * definition.radius();
    }

    public UUID zoneId() {
        return zoneId;
    }

    public UUID casterId() {
        return casterId;
    }

    public Location center() {
        return center.clone();
    }

    public HazardZoneDefinition definition() {
        return definition;
    }

    public long spawnTick() {
        return spawnTick;
    }

    public long expireTick() {
        return expireTick;
    }

    public boolean isExpired(long currentTick) {
        return currentTick >= expireTick;
    }

    public Set<UUID> entitiesInside() {
        return Collections.unmodifiableSet(entitiesInside);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || center.getWorld() == null) return false;
        if (!loc.getWorld().equals(center.getWorld())) return false;
        return loc.distanceSquared(center) <= radiusSquared;
    }

    public boolean addInside(UUID entityId) {
        return entitiesInside.add(entityId);
    }

    public boolean removeInside(UUID entityId) {
        return entitiesInside.remove(entityId);
    }

    public boolean isInside(UUID entityId) {
        return entitiesInside.contains(entityId);
    }
}
