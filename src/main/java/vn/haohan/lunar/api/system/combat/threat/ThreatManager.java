package vn.haohan.lunar.api.system.combat.threat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.haohan.lunar.api.event.MobTargetChangeEvent;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates and synchronizes threat tables with Bukkit Mob AI targeting.
 */
public final class ThreatManager implements Listener {

    private final LunarMobManager mobManager;
    private final Map<UUID, ThreatTable> tables = new ConcurrentHashMap<>();

    public ThreatManager(LunarMobManager mobManager) {
        this.mobManager = mobManager;
    }

    public ThreatManager() {
        this(null);
    }

    public ThreatTable getOrCreate(UUID mobId) {
        if (mobId == null) {
            throw new IllegalArgumentException("Mob ID must not be null");
        }
        if (mobManager != null) {
            ActiveMob activeMob = mobManager.get(mobId);
            if (activeMob != null) {
                return activeMob.threatTable();
            }
        }
        return tables.computeIfAbsent(mobId, ThreatTable::new);
    }

    public Optional<ThreatTable> get(UUID mobId) {
        if (mobId == null) {
            return Optional.empty();
        }
        if (mobManager != null) {
            ActiveMob activeMob = mobManager.get(mobId);
            if (activeMob != null) {
                return Optional.of(activeMob.threatTable());
            }
        }
        return Optional.ofNullable(tables.get(mobId));
    }

    public void remove(UUID mobId) {
        if (mobId != null) {
            tables.remove(mobId);
            if (mobManager != null) {
                ActiveMob activeMob = mobManager.get(mobId);
                if (activeMob != null) {
                    activeMob.threatTable().clear();
                }
            }
        }
    }

    public void clearAll() {
        tables.clear();
        if (mobManager != null) {
            for (ActiveMob activeMob : mobManager.snapshot()) {
                activeMob.threatTable().clear();
            }
        }
    }

    public int activeTableCount() {
        if (mobManager != null) {
            return mobManager.activeCount();
        }
        return tables.size();
    }

    public void recordDamage(LivingEntity victim, Entity damager, double damage, long currentTick) {
        if (victim == null || damager == null || damage <= 0.0) {
            return;
        }
        UUID victimId = victim.getUniqueId();
        if (mobManager != null && mobManager.get(victimId) == null) {
            return;
        }

        LivingEntity attacker = resolveAttacker(damager);
        if (attacker == null || attacker.getUniqueId().equals(victimId)) {
            return;
        }

        ThreatTable table = getOrCreate(victimId);
        table.addDamageThreat(attacker.getUniqueId(), damage, currentTick);
    }

    public void recordHeal(LivingEntity healer, LivingEntity healed, double amount, long currentTick) {
        if (healer == null || amount <= 0.0) {
            return;
        }
        UUID healerId = healer.getUniqueId();
        // Add heal threat to any mob that has the healed entity as target or on its threat table
        if (mobManager != null) {
            for (ActiveMob mob : mobManager.snapshot()) {
                ThreatTable table = mob.threatTable();
                if (healed != null && table.getThreat(healed.getUniqueId()) > 0) {
                    table.addHealThreat(healerId, amount, currentTick);
                }
            }
        }
        for (ThreatTable table : tables.values()) {
            if (healed != null && table.getThreat(healed.getUniqueId()) > 0) {
                table.addHealThreat(healerId, amount, currentTick);
            }
        }
    }

    public void recordProximity(UUID mobId, UUID targetId, double points, long currentTick) {
        if (mobId == null || targetId == null || points <= 0.0) {
            return;
        }
        ThreatTable table = getOrCreate(mobId);
        table.addProximityThreat(targetId, points, currentTick);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        double damage = event.getFinalDamage() > 0 ? event.getFinalDamage() : event.getDamage();
        recordDamage(victim, event.getDamager(), damage, resolveCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event == null || event.getEntity() == null) {
            return;
        }
        UUID deadId = event.getEntity().getUniqueId();
        // If the dead entity was a mob, remove its table
        remove(deadId);

        // If the dead entity was a target, remove from all tables
        if (mobManager != null) {
            for (ActiveMob mob : mobManager.snapshot()) {
                mob.threatTable().removeTarget(deadId);
            }
        }
        for (ThreatTable table : tables.values()) {
            table.removeTarget(deadId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        UUID playerUuid = event.getPlayer().getUniqueId();
        if (mobManager != null) {
            for (ActiveMob mob : mobManager.snapshot()) {
                mob.threatTable().removeTarget(playerUuid);
            }
        }
        for (ThreatTable table : tables.values()) {
            table.removeTarget(playerUuid);
        }
    }

    /**
     * Ticks threat decay and synchronizes AI target selection.
     */
    public void tick(long currentTick) {
        if (mobManager != null) {
            for (ActiveMob activeMob : mobManager.snapshot()) {
                tickMob(activeMob.entityId(), activeMob.threatTable(), currentTick, activeMob);
            }
        }
        for (Map.Entry<UUID, ThreatTable> entry : tables.entrySet()) {
            UUID mobId = entry.getKey();
            ThreatTable table = entry.getValue();
            ActiveMob activeMob = mobManager != null ? mobManager.get(mobId) : null;
            tickMob(mobId, table, currentTick, activeMob);
        }
    }

    private void tickMob(UUID mobId, ThreatTable table, long currentTick, ActiveMob activeMob) {
        table.tickDecay(currentTick, 20L);
        Optional<UUID> targetOpt = table.evaluateTarget(currentTick);

        if (activeMob == null || !(activeMob.entity() instanceof Mob bukkitMob) || !bukkitMob.isValid()) {
            return;
        }

        if (targetOpt.isEmpty()) {
            try {
                bukkitMob.setTarget(null);
            } catch (Throwable ignored) {
            }
            return;
        }

        UUID targetId = targetOpt.get();
        try {
            Entity targetEntity = Bukkit.getEntity(targetId);
            if (targetEntity instanceof LivingEntity living && living.isValid() && !living.isDead()
                    && living.getWorld().equals(bukkitMob.getWorld())) {
                LivingEntity currentBukkitTarget = bukkitMob.getTarget();
                if (currentBukkitTarget == null || !currentBukkitTarget.getUniqueId().equals(targetId)) {
                    // Fire cancellable MobTargetChangeEvent
                    double threat = table.getThreat(targetId);
                    MobTargetChangeEvent event = new MobTargetChangeEvent(
                            activeMob, currentBukkitTarget, living, threat, TargetChangeReason.DAMAGE_THREAT
                    );
                    try {
                        Bukkit.getPluginManager().callEvent(event);
                    } catch (Throwable ignored) {
                    }

                    if (!event.isCancelled()) {
                        LivingEntity toSet = event.newTarget().orElse(null);
                        bukkitMob.setTarget(toSet);
                    } else {
                        // Revert to old target
                        if (currentBukkitTarget != null) {
                            table.setCurrentTargetId(currentBukkitTarget.getUniqueId());
                        }
                    }
                }
            } else {
                // Target has died or left the world
                table.removeTarget(targetId);
                bukkitMob.setTarget(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }

    private static long resolveCurrentTick() {
        try {
            return Bukkit.getCurrentTick();
        } catch (Throwable ignored) {
            return System.currentTimeMillis() / 50L;
        }
    }
}
