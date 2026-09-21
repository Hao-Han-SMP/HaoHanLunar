package vn.haohan.lunar.api.mob.options;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Objects;

/**
 * Event listener enforcing all 18+ MobOptions cleanly on active Lunar mobs.
 */
public final class MobOptionsListener implements Listener {

    private final LunarMobManager mobManager;

    public MobOptionsListener(LunarMobManager mobManager) {
        this.mobManager = Objects.requireNonNull(mobManager, "LunarMobManager must not be null");
    }

    private ActiveMob getMob(Entity entity) {
        if (entity == null) return null;
        return mobManager.get(entity.getUniqueId());
    }

    private MobOptions getOptions(Entity entity) {
        ActiveMob mob = getMob(entity);
        if (mob == null) return null;
        return mob.options();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        // Prevent sunburn: combust without block or entity source is sunlight
        if (event instanceof EntityCombustByBlockEvent || event instanceof EntityCombustByEntityEvent) {
            return;
        }
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventSunburn()) {
            event.setCancelled(true);
        }
    }

    /**
     * Evaluates mob options against incoming damage.
     * Returns true if damage should be cancelled, and modifies damage[0] if capped.
     */
    public boolean processDamage(LivingEntity entity, EntityDamageEvent.DamageCause cause, double[] damage, boolean isCustomSkill) {
        MobOptions opt = getOptions(entity);
        if (opt == null) return false;

        if (opt.preventVanillaDamage() && !isCustomSkill) {
            return true;
        }

        // Safeguards & anti-exploit
        if (opt.preventSuffocation() && cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
            return true;
        }
        if (opt.preventFallDamage() && cause == EntityDamageEvent.DamageCause.FALL) {
            return true;
        }
        if (opt.preventDrowning() && cause == EntityDamageEvent.DamageCause.DROWNING) {
            return true;
        }
        if (opt.voidProtection() && cause == EntityDamageEvent.DamageCause.VOID) {
            ActiveMob mob = getMob(entity);
            if (mob != null && mob.entity() != null) {
                Location loc = mob.entity().getLocation();
                if (loc.getWorld() != null) {
                    Location safe = loc.getWorld().getHighestBlockAt(loc).getLocation().add(0, 1, 0);
                    mob.entity().teleport(safe);
                }
            }
            return true;
        }

        // Damage cap per hit
        if (opt.maxDamagePerHit() > 0.0 && damage != null && damage.length > 0 && damage[0] > opt.maxDamagePerHit()) {
            damage[0] = opt.maxDamagePerHit();
        }

        if (opt.noDamageTicks() >= 0) {
            try {
                entity.setNoDamageTicks(opt.noDamageTicks());
            }
            catch (Throwable ignored) {
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        double[] dmg = new double[]{event.getDamage()};
        boolean cancelled = processDamage(living, event.getCause(), dmg, isCustomSkillDamage(event));
        if (cancelled) {
            event.setCancelled(true);
            return;
        }
        if (dmg[0] != event.getDamage()) {
            event.setDamage(dmg[0]);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onKnockback(EntityDamageByEntityEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventKnockback()) {
            // Cancel vertical / horizontal velocity knockback
            event.getEntity().setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        MobOptions opt = getOptions(event.getRightClicked());
        if (opt == null) return;

        Player player = event.getPlayer();
        var mainItem = player.getInventory().getItemInMainHand();
        var offItem = player.getInventory().getItemInOffHand();

        if (opt.preventRename()) {
            if (mainItem.getType() == Material.NAME_TAG || offItem.getType() == Material.NAME_TAG) {
                event.setCancelled(true);
                return;
            }
        }

        if (opt.preventLeashing()) {
            if (mainItem.getType() == Material.LEAD || offItem.getType() == Material.LEAD) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventEndermanTeleport()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventItemPickup()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventExploding()) {
            event.setCancelled(true);
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt != null && opt.preventTransformation()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        MobOptions opt = getOptions(event.getMount());
        if (opt != null && opt.preventMounts()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt == null) return;

        if (opt.preventOtherDrops()) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        if (opt.preventMobKillDrops()) {
            EntityDamageEvent lastDamage = event.getEntity().getLastDamageCause();
            if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
                if (!(byEntity.getDamager() instanceof Player)) {
                    event.getDrops().clear();
                    event.setDroppedExp(0);
                }
            }
        }
    }

    private boolean isCustomSkillDamage(EntityDamageEvent event) {
        return false;
    }
}
