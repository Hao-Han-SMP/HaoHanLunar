package vn.haohan.lunar.api.world.totem;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.haohan.lunar.api.skill.target.TargetRef;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Manages the lifecycle, pulse ticks, damage handling, and cleanup of Destructible Totems.
 */
public final class TotemManager implements Listener {

    private final Map<UUID, ActiveTotem> totemsById = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveTotem> totemsByEntityId = new ConcurrentHashMap<>();

    // Entity spawner hook allowing test mock injection
    private BiFunction<Location, TotemDefinition, Entity> entitySpawner = this::defaultSpawnEntity;

    public void setEntitySpawner(BiFunction<Location, TotemDefinition, Entity> spawner) {
        this.entitySpawner = spawner != null ? spawner : this::defaultSpawnEntity;
    }

    public ActiveTotem spawnTotem(UUID casterId, Location location, TotemDefinition definition, long currentTick) {
        Objects.requireNonNull(definition, "Totem definition must not be null");
        if (location == null) return null;

        Entity entity = null;
        try {
            entity = entitySpawner.apply(location, definition);
        } catch (Throwable ignored) {}

        UUID totemId = UUID.randomUUID();
        ActiveTotem totem = new ActiveTotem(totemId, casterId, definition, location, entity, currentTick);
        totemsById.put(totemId, totem);
        if (entity != null) {
            try {
                totemsByEntityId.put(entity.getUniqueId(), totem);
            } catch (Throwable ignored) {}
        }
        return totem;
    }

    public void registerTotem(ActiveTotem totem) {
        if (totem == null) return;
        totemsById.put(totem.totemId(), totem);
        if (totem.entity() != null) {
            try {
                totemsByEntityId.put(totem.entity().getUniqueId(), totem);
            } catch (Throwable ignored) {}
        }
    }

    public Optional<ActiveTotem> getTotem(UUID totemId) {
        if (totemId == null) return Optional.empty();
        return Optional.ofNullable(totemsById.get(totemId));
    }

    public Optional<ActiveTotem> getTotemByEntity(UUID entityId) {
        if (entityId == null) return Optional.empty();
        return Optional.ofNullable(totemsByEntityId.get(entityId));
    }

    public Collection<ActiveTotem> activeTotems() {
        return Collections.unmodifiableCollection(totemsById.values());
    }

    public void tick(long currentTick, BiConsumer<String, TargetRef> skillDispatcher) {
        for (ActiveTotem totem : totemsById.values()) {
            totem.tick(currentTick, skillDispatcher);
            if (!totem.isActive()) {
                removeTotem(totem);
            }
        }
    }

    public boolean damageTotem(UUID totemIdOrEntityId, double amount, LivingEntity damager, BiConsumer<String, TargetRef> skillDispatcher) {
        ActiveTotem totem = totemsById.get(totemIdOrEntityId);
        if (totem == null) {
            totem = totemsByEntityId.get(totemIdOrEntityId);
        }
        if (totem == null || !totem.isActive()) return false;

        boolean destroyed = totem.damage(amount, damager, skillDispatcher);
        if (destroyed || !totem.isActive()) {
            removeTotem(totem);
        }
        return destroyed;
    }

    private void removeTotem(ActiveTotem totem) {
        totemsById.remove(totem.totemId());
        if (totem.entity() != null) {
            try {
                totemsByEntityId.remove(totem.entity().getUniqueId());
            } catch (Throwable ignored) {}
        }
    }

    public void clear() {
        for (ActiveTotem totem : totemsById.values()) {
            totem.removeEntity();
        }
        totemsById.clear();
        totemsByEntityId.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        ActiveTotem totem = totemsByEntityId.get(event.getEntity().getUniqueId());
        if (totem != null && totem.isActive()) {
            event.setCancelled(true);
            LivingEntity damager = null;
            if (event instanceof EntityDamageByEntityEvent byEntityEvent && byEntityEvent.getDamager() instanceof LivingEntity living) {
                damager = living;
            }
            damageTotem(totem.totemId(), event.getFinalDamage(), damager, null);
        }
    }

    private Entity defaultSpawnEntity(Location loc, TotemDefinition def) {
        if (loc.getWorld() == null) return null;
        try {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            stand.setVisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(false);
            stand.setSmall(false);
            return stand;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
