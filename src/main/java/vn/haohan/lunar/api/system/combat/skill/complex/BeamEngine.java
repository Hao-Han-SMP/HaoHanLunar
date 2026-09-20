package vn.haohan.lunar.api.system.combat.skill.complex;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Sweeps a cylindrical continuous energy beam through the world, affecting all intersecting entities.
 */
public final class BeamEngine {

    public record BeamHit(LivingEntity entity, double distanceAlongBeam) {}

    public static List<BeamHit> fireBeam(
            Location origin,
            Vector direction,
            double maxRange,
            double beamWidth,
            boolean stopOnBlock,
            UUID casterId,
            BiConsumer<LivingEntity, Double> onHit
    ) {
        if (origin == null || direction == null || origin.getWorld() == null) {
            return List.of();
        }

        maxRange = Math.max(0.5, Math.min(maxRange, 100.0));
        beamWidth = Math.max(0.1, Math.min(beamWidth, 10.0));

        Vector dir = direction.clone().normalize();
        double effectiveRange = maxRange;
        World world = origin.getWorld();

        if (stopOnBlock) {
            try {
                RayTraceResult blockHit = world.rayTraceBlocks(origin, dir, maxRange, FluidCollisionMode.NEVER, true);
                if (blockHit != null && blockHit.getHitPosition() != null) {
                    effectiveRange = blockHit.getHitPosition().distance(origin.toVector());
                }
            } catch (Exception ignored) {}
        }

        List<BeamHit> hits = new ArrayList<>();
        Vector originVec = origin.toVector();

        // Check entities in the bounding sphere of the beam
        Location midPoint = origin.clone().add(dir.clone().multiply(effectiveRange * 0.5));
        double queryRadius = (effectiveRange * 0.5) + beamWidth + 2.0;

        for (Entity candidate : world.getNearbyEntities(midPoint, queryRadius, queryRadius, queryRadius)) {
            if (!(candidate instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (casterId != null && casterId.equals(living.getUniqueId())) {
                continue;
            }

            Vector entityVec = living.getLocation().toVector();
            Vector toEntity = entityVec.clone().subtract(originVec);
            double projection = toEntity.dot(dir);

            if (projection >= 0 && projection <= effectiveRange) {
                Vector pointOnLine = originVec.clone().add(dir.clone().multiply(projection));
                double distanceToLine = pointOnLine.distance(entityVec);

                if (distanceToLine <= beamWidth) {
                    BeamHit hit = new BeamHit(living, projection);
                    hits.add(hit);
                    if (onHit != null) {
                        try {
                            onHit.accept(living, projection);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        return hits;
    }
}
