package vn.haohan.lunar.api.system.combat.skill.complex;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Executes a bouncing chain lightning / projectile strike from one target to subsequent unique targets.
 */
public final class ChainEngine {

    public record ChainResult(List<LivingEntity> hitOrder, int bounceCount) {}

    public static ChainResult executeChain(
            LivingEntity initialTarget,
            double bounceRadius,
            int maxBounces,
            double baseDamage,
            double damageDecay,
            LivingEntity caster,
            BiConsumer<LivingEntity, Double> onHit
    ) {
        if (initialTarget == null || !initialTarget.isValid() || initialTarget.isDead()) {
            return new ChainResult(List.of(), 0);
        }

        bounceRadius = Math.max(1.0, Math.min(bounceRadius, 32.0));
        maxBounces = Math.max(1, Math.min(maxBounces, 20));
        damageDecay = Math.max(0.0, Math.min(damageDecay, 1.0));

        List<LivingEntity> hitOrder = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        LivingEntity current = initialTarget;
        double currentDamage = baseDamage;

        while (current != null && hitOrder.size() < maxBounces) {
            hitOrder.add(current);
            visited.add(current.getUniqueId());

            if (onHit != null) {
                try {
                    onHit.accept(current, currentDamage);
                } catch (Exception ignored) {}
            }

            currentDamage *= damageDecay;
            current = findNextTarget(current, bounceRadius, visited, caster);
        }

        return new ChainResult(hitOrder, hitOrder.size());
    }

    private static LivingEntity findNextTarget(LivingEntity current, double radius, Set<UUID> visited, LivingEntity caster) {
        Location loc = current.getLocation();
        if (loc == null || loc.getWorld() == null) return null;

        UUID casterId = caster != null ? caster.getUniqueId() : null;
        LivingEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Entity candidate : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(candidate instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (visited.contains(living.getUniqueId())) {
                continue;
            }
            if (casterId != null && casterId.equals(living.getUniqueId())) {
                continue;
            }

            double distSq = loc.distanceSquared(living.getLocation());
            if (distSq <= radius * radius && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = living;
            }
        }
        return nearest;
    }
}
