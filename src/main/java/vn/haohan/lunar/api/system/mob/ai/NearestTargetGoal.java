package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Tactical Target Goal selecting the nearest living entity satisfying a given filter predicate.
 * Paper 1.21.1 Native Goal.
 */
public class NearestTargetGoal implements Goal<Mob> {

    private final GoalKey<Mob> key;
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.TARGET);

    private final Mob mob;
    private final Predicate<LivingEntity> filter;
    private final double radius;
    private final int intervalTicks;
    private LivingEntity currentTarget;
    private int tickCounter = 0;

    public NearestTargetGoal(Mob mob, String keyName, Predicate<LivingEntity> filter, double radius, int intervalTicks) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.key = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", keyName != null ? keyName : "nearest_target"));
        this.filter = Objects.requireNonNull(filter, "Filter must not be null");
        this.radius = radius > 0 ? radius : 16.0;
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    public NearestTargetGoal(Mob mob, String keyName, Predicate<LivingEntity> filter) {
        this(mob, keyName, filter, 16.0, 10);
    }

    @Override
    public boolean shouldActivate() {
        if (!mob.isValid() || mob.isDead() || mob.getWorld() == null) return false;
        if (++tickCounter % intervalTicks != 0) {
            return false;
        }

        LivingEntity existing = mob.getTarget();
        if (existing != null && existing.isValid() && !existing.isDead()) {
            return false;
        }

        LivingEntity chosen = scanForTarget();
        if (chosen != null) {
            this.currentTarget = chosen;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldStayActive() {
        if (!mob.isValid() || currentTarget == null || !currentTarget.isValid() || currentTarget.isDead()) {
            return false;
        }
        if (!Objects.equals(mob.getWorld(), currentTarget.getWorld())) {
            return false;
        }
        return mob.getLocation().distanceSquared(currentTarget.getLocation()) <= (radius * radius * 1.5);
    }

    @Override
    public void start() {
        if (currentTarget != null) {
            try {
                mob.setTarget(currentTarget);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void stop() {
        this.currentTarget = null;
    }

    private LivingEntity scanForTarget() {
        Location center = mob.getLocation();
        double radiusSq = radius * radius;
        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity nearby : mob.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity living) || living.equals(mob) || !living.isValid() || living.isDead()) {
                continue;
            }

            if (living instanceof Player player) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
            }

            double distSq = center.distanceSquared(living.getLocation());
            if (distSq > radiusSq || distSq >= closestDistSq) {
                continue;
            }

            if (filter.test(living)) {
                closest = living;
                closestDistSq = distSq;
            }
        }

        return closest;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return TYPES;
    }
}
