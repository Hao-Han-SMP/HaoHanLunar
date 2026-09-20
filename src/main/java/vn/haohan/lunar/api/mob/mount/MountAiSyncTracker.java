package vn.haohan.lunar.api.mob.mount;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.api.system.combat.cc.CCState;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Optional;
import java.util.UUID;

/**
 * Synchronizes AI navigation, ThreatTable targeting, and Crowd Control states
 * between rider and mount to form a unified combat unit.
 */
public final class MountAiSyncTracker {

    private final LunarMobManager mobManager;

    public MountAiSyncTracker(LunarMobManager mobManager) {
        this.mobManager = mobManager;
    }

    /**
     * Executes an AI synchronization tick between an active rider and its mount.
     */
    public void sync(ActiveMob rider) {
        if (rider == null || mobManager == null) return;
        UUID mountId = rider.mountUUID();
        if (mountId == null) return;

        ActiveMob mount = mobManager.get(mountId);
        if (mount == null || mount.entity().isDead()) return;

        syncTargetAndThreat(mount, rider);
        syncCrowdControl(mount, rider);
    }

    /**
     * Synchronizes the Rider's top threat target into the Mount so the Mount steers toward it.
     */
    public void syncTargetAndThreat(ActiveMob mount, ActiveMob rider) {
        if (mount == null || rider == null) return;

        Optional<UUID> riderTargetId = rider.threatTable().getTopTarget();
        if (riderTargetId.isPresent()) {
            UUID targetId = riderTargetId.get();
            double riderThreat = rider.threatTable().getThreat(targetId);

            // Mirror threat on the mount
            mount.threatTable().addDamageThreat(targetId, Math.max(1.0, riderThreat * 0.8), System.currentTimeMillis() / 50L);

            // If entities are CraftBukkit Mobs, steer the mount's pathfinding target
            if (mount.entity() instanceof Mob mountMob && rider.entity() instanceof Mob riderMob) {
                LivingEntity currentRiderTarget = riderMob.getTarget();
                if (currentRiderTarget != null && !currentRiderTarget.equals(mountMob.getTarget())) {
                    try {
                        mountMob.setTarget(currentRiderTarget);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    /**
     * Propagates crowd-control effects between Rider and Mount:
     * If the Rider is stunned or rooted, the Mount stops or is rooted.
     */
    public void syncCrowdControl(ActiveMob mount, ActiveMob rider) {
        if (mount == null || rider == null) return;

        // If rider is stunned or rooted, halt/root mount
        if (rider.crowdControl().isStunned() || rider.crowdControl().isRooted()) {
            if (!mount.crowdControl().isStunned() && !mount.crowdControl().isRooted()) {
                mount.crowdControl().apply(CCState.ROOT, 20, rider.entityId(), 1);
                if (mount.entity() instanceof Mob mountMob) {
                    try {
                        mountMob.setTarget(null);
                    } catch (Throwable ignored) {}
                }
            }
        }
    }
}
