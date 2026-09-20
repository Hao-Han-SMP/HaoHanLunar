package vn.haohan.lunar.api.spawner.cluster;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Collection;
import java.util.UUID;

/**
 * Coordinates pack-wide aggro signals when any member of a cluster/pack is engaged.
 */
public final class PackAggroCoordinator {

    private final double defaultPackThreat;

    public PackAggroCoordinator(double defaultPackThreat) {
        this.defaultPackThreat = Math.max(10.0, defaultPackThreat);
    }

    public PackAggroCoordinator() {
        this(150.0);
    }

    /**
     * Broadcasts an aggro signal from the damaged/engaged pack member to all related pack members.
     *
     * @param sourceMob  the mob that was attacked
     * @param attacker   the hostile attacker
     * @param candidates collection of candidate mobs (e.g., from mob manager or local area)
     * @return number of pack members alerted
     */
    public int broadcastAggro(ActiveMob sourceMob, LivingEntity attacker, Collection<? extends ActiveMob> candidates) {
        if (sourceMob == null || attacker == null || candidates == null || candidates.isEmpty()) {
            return 0;
        }

        // Determine leader UUID of the pack
        UUID leaderId = sourceMob.parentUUID() != null ? sourceMob.parentUUID() : sourceMob.entityId();

        int alertedCount = 0;
        for (ActiveMob member : candidates) {
            if (member == null || member.entity() == null || member.entity().isDead() || !member.entity().isValid()) {
                continue;
            }

            // A mob is part of the pack if its ID is the leader, or its parent is the leader
            boolean isSamePack = member.entityId().equals(leaderId)
                    || (member.parentUUID() != null && member.parentUUID().equals(leaderId));

            if (!isSamePack) {
                continue;
            }

            // Add threat to attacker
            member.threatTable().addThreat(attacker.getUniqueId(), defaultPackThreat);

            // Update vanilla target if applicable
            if (member.entity() instanceof Mob vanillaMob) {
                try {
                    vanillaMob.setTarget(attacker);
                } catch (Throwable ignored) {
                }
            }

            alertedCount++;
        }

        return alertedCount;
    }
}
