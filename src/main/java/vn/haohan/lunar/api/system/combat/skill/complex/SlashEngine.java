package vn.haohan.lunar.api.system.combat.skill.complex;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Calculates a sweeping melee sword arc / cone sector hitbox in front of an entity.
 */
public final class SlashEngine {

    public record SlashHit(LivingEntity target, double distance, double angleDegrees) {}

    public static List<SlashHit> executeSlash(
            Location origin,
            Vector facingDirection,
            double slashRadius,
            double arcAngleDegrees,
            UUID casterId,
            BiConsumer<LivingEntity, SlashHit> onHit
    ) {
        if (origin == null || facingDirection == null || origin.getWorld() == null) {
            return List.of();
        }

        slashRadius = Math.max(0.5, Math.min(slashRadius, 32.0));
        arcAngleDegrees = Math.max(1.0, Math.min(arcAngleDegrees, 360.0));
        double halfArc = arcAngleDegrees / 2.0;
        double cosThreshold = Math.cos(Math.toRadians(halfArc));

        Vector facing = facingDirection.clone().normalize();
        World world = origin.getWorld();
        List<SlashHit> hits = new ArrayList<>();

        for (Entity candidate : world.getNearbyEntities(origin, slashRadius, slashRadius, slashRadius)) {
            if (!(candidate instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (casterId != null && casterId.equals(living.getUniqueId())) {
                continue;
            }

            Vector toTarget = living.getLocation().toVector().subtract(origin.toVector());
            double distance = toTarget.length();
            if (distance <= 0.0001 || distance > slashRadius) {
                continue;
            }

            double dot = facing.dot(toTarget.clone().normalize());
            if (dot >= cosThreshold) {
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
                SlashHit hit = new SlashHit(living, distance, angle);
                hits.add(hit);
                if (onHit != null) {
                    try {
                        onHit.accept(living, hit);
                    } catch (Exception ignored) {}
                }
            }
        }

        return hits;
    }
}
