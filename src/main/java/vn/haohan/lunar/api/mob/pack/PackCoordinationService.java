package vn.haohan.lunar.api.mob.pack;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Coordinates pack tactics:
 * 1. Distress Call: Broadcasts assistance requests and shares threat when a pack member is low on health.
 * 2. Flocking Separation: Computes gentle separation vectors between mob entities to prevent hitbox overlap.
 */
public final class PackCoordinationService {

    private final double separationDistance;

    public PackCoordinationService(double separationDistance) {
        this.separationDistance = Math.max(0.5, separationDistance);
    }

    public PackCoordinationService() {
        this(1.5);
    }

    /**
     * Executes a distress call from the source mob to friendly/allied mobs within radius.
     * Shares threat against the attacker with allies.
     *
     * @param caller       the mob triggering the distress call
     * @param attacker     the threat source to share
     * @param radius       search radius
     * @param threatShare  fraction of threat (e.g. 0.8 = 80%)
     * @param mobManager   the mob registry
     * @return number of allies alerted
     */
    public int broadcastDistressCall(ActiveLunarMob caller, LivingEntity attacker, double radius,
                                     double threatShare, LunarMobManager mobManager) {
        if (caller == null || attacker == null || mobManager == null || caller.entity() == null) {
            return 0;
        }

        Location callerLoc = caller.entity().getLocation();
        World world = callerLoc.getWorld();
        if (world == null) return 0;

        double radiusSq = radius * radius;
        UUID attackerId = attacker.getUniqueId();
        double callerThreat = caller.threatTable().getThreat(attackerId);
        if (callerThreat <= 0.0) {
            callerThreat = 100.0;
        }
        double sharedThreat = callerThreat * Math.max(0.1, Math.min(1.0, threatShare));

        int alertedCount = 0;
        for (ActiveLunarMob candidate : mobManager.snapshot()) {
            if (candidate == null || candidate.entityId().equals(caller.entityId())) {
                continue;
            }
            if (candidate.entity() == null || !candidate.entity().isValid() || candidate.entity().isDead()) {
                continue;
            }
            if (!world.equals(candidate.entity().getWorld())) {
                continue;
            }
            if (candidate.entity().getLocation().distanceSquared(callerLoc) > radiusSq) {
                continue;
            }

            // Candidate shares the threat
            candidate.threatTable().addThreat(attackerId, sharedThreat);
            alertedCount++;
        }

        return alertedCount;
    }

    /**
     * Computes a flocking separation push vector for a mob against its nearby pack neighbors.
     * If neighbors are within separationDistance, applies an inversely proportional repelling vector.
     *
     * @param subject   the mob being nudged
     * @param neighbors other nearby active mobs
     * @return separation vector (zero if no close neighbors)
     */
    public Vector computeSeparationVector(ActiveLunarMob subject, Collection<ActiveLunarMob> neighbors) {
        if (subject == null || subject.entity() == null || neighbors == null || neighbors.isEmpty()) {
            return new Vector(0, 0, 0);
        }

        Location subjectLoc = subject.entity().getLocation();
        World world = subjectLoc.getWorld();
        if (world == null) return new Vector(0, 0, 0);

        Vector force = new Vector(0, 0, 0);
        int neighborsCount = 0;

        for (ActiveLunarMob neighbor : neighbors) {
            if (neighbor == null || neighbor.entityId().equals(subject.entityId())) continue;
            if (neighbor.entity() == null || !neighbor.entity().isValid() || neighbor.entity().isDead()) continue;

            Location neighborLoc = neighbor.entity().getLocation();
            if (!world.equals(neighborLoc.getWorld())) continue;

            double dist = subjectLoc.distance(neighborLoc);
            if (dist > 0.001 && dist < separationDistance) {
                // Vector pointing away from neighbor
                Vector diff = subjectLoc.toVector().subtract(neighborLoc.toVector());
                // Inverse distance weighting
                diff.normalize().multiply((separationDistance - dist) / separationDistance);
                force.add(diff);
                neighborsCount++;
            }
        }

        if (neighborsCount > 0) {
            force.multiply(1.0 / neighborsCount);
            // Flatten Y axis slightly so mobs don't fly up unless needed
            force.setY(Math.max(0.0, force.getY() * 0.2));
            // Clamp maximum separation push speed to 0.5 to prevent jarring displacement
            if (force.length() > 0.5) {
                force.normalize().multiply(0.5);
            }
        }

        return force;
    }
}
