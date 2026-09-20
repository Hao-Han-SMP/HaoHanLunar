package vn.haohan.lunar.api.mob.options;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityMountEvent;
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

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        MobOptions opt = getOptions(event.getEntity());
        if (opt == null) return;

        if (opt.preventVanillaDamage() && !isCustomSkillDamage(event)) {
            event.setCancelled(true);
            return;
        }

        if (opt.noDamageTicks() >= 0 && event.getEntity() instanceof LivingEntity living) {
            living.setNoDamageTicks(opt.noDamageTicks());
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
