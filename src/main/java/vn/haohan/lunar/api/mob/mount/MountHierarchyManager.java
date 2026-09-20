package vn.haohan.lunar.api.mob.mount;

import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Manages hierarchical mount, passenger, and vehicle relationships for ActiveLunarMob instances.
 * Handles automatic mounting on spawn, safe dismount velocity, and berserk state on rider death.
 */
public final class MountHierarchyManager {

    private final Vector dismountEjectionVelocity;

    public MountHierarchyManager(Vector dismountEjectionVelocity) {
        this.dismountEjectionVelocity = dismountEjectionVelocity != null ? dismountEjectionVelocity : new Vector(0, 0.35, 0);
    }

    public MountHierarchyManager() {
        this(new Vector(0, 0.35, 0));
    }

    /**
     * Automatically spawns and binds mounts and riders defined in the mob's YAML configuration.
     *
     * @param mob the primary mob instance
     * @param spawner callback capable of spawning child ActiveLunarMob instances by mob ID
     */
    public void linkMountAndRiders(ActiveLunarMob mob, Function<String, ActiveLunarMob> spawner) {
        if (mob == null || spawner == null) return;

        // 1. Check if mob configures a Mount (this mob rides the mount)
        mob.definition().mountId().ifPresent(mountId -> {
            ActiveLunarMob mountMob = spawner.apply(mountId);
            if (mountMob != null) {
                mount(mob, mountMob);
            }
        });

        // 2. Check if mob configures Riders (other mobs ride on top of this mob)
        for (String riderId : mob.definition().riderIds()) {
            ActiveLunarMob riderMob = spawner.apply(riderId);
            if (riderMob != null) {
                mount(riderMob, mob);
            }
        }
    }

    /**
     * Binds a rider onto a mount.
     */
    public boolean mount(ActiveLunarMob rider, ActiveLunarMob mount) {
        if (rider == null || mount == null || rider.entityId().equals(mount.entityId())) {
            return false;
        }

        try {
            mount.entity().addPassenger(rider.entity());
        } catch (Throwable ignored) {}

        rider.setMountUUID(mount.entityId());
        mount.riderUUIDs().add(rider.entityId());
        return true;
    }

    /**
     * Safely dismounts a rider from its current vehicle.
     */
    public boolean dismount(ActiveLunarMob rider, LunarMobManager mobManager) {
        if (rider == null) return false;

        UUID mountId = rider.mountUUID();
        if (mountId != null && mobManager != null) {
            ActiveLunarMob mount = mobManager.get(mountId);
            if (mount != null) {
                try {
                    mount.entity().removePassenger(rider.entity());
                } catch (Throwable ignored) {}
                mount.riderUUIDs().remove(rider.entityId());
            }
        }

        try {
            rider.entity().leaveVehicle();
            rider.entity().setVelocity(dismountEjectionVelocity.clone());
        } catch (Throwable ignored) {}

        rider.setMountUUID(null);
        return true;
    }

    /**
     * Ejects all passengers currently riding the vehicle.
     *
     * @return number of passengers ejected
     */
    public int ejectPassengers(ActiveLunarMob vehicle, LunarMobManager mobManager) {
        if (vehicle == null) return 0;

        int count = 0;
        Set<UUID> riders = vehicle.riderUUIDs();
        for (UUID riderId : riders) {
            if (mobManager != null) {
                ActiveLunarMob rider = mobManager.get(riderId);
                if (rider != null) {
                    rider.setMountUUID(null);
                    try {
                        rider.entity().setVelocity(dismountEjectionVelocity.clone());
                    } catch (Throwable ignored) {}
                }
            }
            count++;
        }
        riders.clear();

        try {
            vehicle.entity().eject();
        } catch (Throwable ignored) {}

        return count;
    }

    /**
     * Handles lifecycle death synchronization:
     * - If Mount dies: Passengers dismount safely with ejection velocity.
     * - If Rider dies: Mount loses rider; if all riders dead, enters Berserk mode.
     */
    public void handleEntityDeath(LivingEntity deadEntity, LunarMobManager mobManager) {
        if (deadEntity == null || mobManager == null) return;
        ActiveLunarMob deadMob = mobManager.get(deadEntity.getUniqueId());
        if (deadMob == null) return;

        // Case 1: Dead mob is a Mount
        if (!deadMob.riderUUIDs().isEmpty()) {
            for (UUID riderId : deadMob.riderUUIDs()) {
                ActiveLunarMob rider = mobManager.get(riderId);
                if (rider != null) {
                    rider.setMountUUID(null);
                    try {
                        deadEntity.removePassenger(rider.entity());
                        rider.entity().setVelocity(dismountEjectionVelocity.clone());
                    } catch (Throwable ignored) {}
                }
            }
            deadMob.riderUUIDs().clear();
        }

        // Case 2: Dead mob is a Rider
        UUID mountId = deadMob.mountUUID();
        if (mountId != null) {
            ActiveLunarMob mount = mobManager.get(mountId);
            if (mount != null && !mount.entity().isDead()) {
                mount.riderUUIDs().remove(deadMob.entityId());
                if (mount.riderUUIDs().isEmpty()) {
                    mount.setBerserk(true);
                    mount.setStance("berserk");
                }
            }
            deadMob.setMountUUID(null);
        }
    }
}
