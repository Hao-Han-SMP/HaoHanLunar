package vn.haohan.engine.api.system.mob.mount;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import vn.haohan.engine.core.mob.MobManager;
import vn.haohan.engine.core.mob.ActiveMob;

import java.util.Objects;

/**
 * Event listener coordinating vehicle hierarchy events: death dismount, berserk transition, and dismounting.
 */
public final class MountEventListener implements Listener {

    private final MountHierarchyManager hierarchyManager;
    private final MobManager mobManager;

    public MountEventListener(MountHierarchyManager hierarchyManager, MobManager mobManager) {
        this.hierarchyManager = Objects.requireNonNull(hierarchyManager, "Hierarchy manager must not be null");
        this.mobManager = Objects.requireNonNull(mobManager, "Mob manager must not be null");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        hierarchyManager.handleEntityDeath(event.getEntity(), mobManager);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        Entity vehicle = event.getDismounted();

        if (dismounted instanceof LivingEntity livingRider) {
            ActiveMob riderMob = mobManager.get(livingRider.getUniqueId());
            if (riderMob != null) {
                riderMob.setMountUUID(null);
            }
        }

        if (vehicle instanceof LivingEntity livingVehicle) {
            ActiveMob mountMob = mobManager.get(livingVehicle.getUniqueId());
            if (mountMob != null && dismounted instanceof LivingEntity livingRider) {
                mountMob.riderUUIDs().remove(livingRider.getUniqueId());
            }
        }
    }
}
