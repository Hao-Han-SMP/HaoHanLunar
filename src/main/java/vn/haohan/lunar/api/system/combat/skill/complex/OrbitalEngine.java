package vn.haohan.lunar.api.system.combat.skill.complex;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Calculates revolving orbital nodes around a central anchor and checks proximity collision.
 */
public final class OrbitalEngine {

    public record OrbitalPoint(int index, Location location) {}

    public static List<OrbitalPoint> computeOrbitalLocations(
            Location center,
            double radius,
            int orbitalCount,
            long tick,
            double angularVelocityRadPerTick
    ) {
        if (center == null || center.getWorld() == null || orbitalCount <= 0) {
            return List.of();
        }

        radius = Math.max(0.5, Math.min(radius, 32.0));
        orbitalCount = Math.max(1, Math.min(orbitalCount, 16));

        List<OrbitalPoint> points = new ArrayList<>(orbitalCount);
        double angleStep = (2 * Math.PI) / orbitalCount;
        double baseAngle = tick * angularVelocityRadPerTick;

        for (int i = 0; i < orbitalCount; i++) {
            double angle = baseAngle + (i * angleStep);
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            Location loc = new Location(center.getWorld(), x, center.getY(), z);
            points.add(new OrbitalPoint(i, loc));
        }

        return points;
    }

    public static List<LivingEntity> checkOrbitalCollisions(
            List<OrbitalPoint> orbitals,
            double hitboxRadius,
            UUID casterId,
            BiConsumer<LivingEntity, OrbitalPoint> onCollision
    ) {
        if (orbitals.isEmpty()) return List.of();

        hitboxRadius = Math.max(0.2, Math.min(hitboxRadius, 5.0));
        List<LivingEntity> hitEntities = new ArrayList<>();

        for (OrbitalPoint orbital : orbitals) {
            Location loc = orbital.location();
            World world = loc.getWorld();
            if (world == null) continue;

            for (Entity entity : world.getNearbyEntities(loc, hitboxRadius, hitboxRadius, hitboxRadius)) {
                if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                    if (casterId != null && casterId.equals(living.getUniqueId())) {
                        continue;
                    }
                    hitEntities.add(living);
                    if (onCollision != null) {
                        try {
                            onCollision.accept(living, orbital);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        return hitEntities;
    }
}
