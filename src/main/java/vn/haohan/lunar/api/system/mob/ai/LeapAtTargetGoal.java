package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Tactical Goal causing the mob to leap toward its target when within range.
 * Paper 1.21.1 Native Goal.
 */
public class LeapAtTargetGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "leap_at_target"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.MOVE, GoalType.JUMP);

    private final Mob mob;
    private final double minDistanceSq;
    private final double maxDistanceSq;
    private final double horizontalSpeed;
    private final double upwardVelocity;
    private final int cooldownTicks;
    private int currentCooldown = 0;

    public LeapAtTargetGoal(Mob mob, double minDistance, double maxDistance, double horizontalSpeed, double upwardVelocity, int cooldownTicks) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        double min = Math.max(1.0, minDistance);
        double max = Math.max(min, maxDistance);
        this.minDistanceSq = min * min;
        this.maxDistanceSq = max * max;
        this.horizontalSpeed = horizontalSpeed > 0 ? horizontalSpeed : 1.2;
        this.upwardVelocity = upwardVelocity > 0 ? upwardVelocity : 0.45;
        this.cooldownTicks = Math.max(10, cooldownTicks);
    }

    public LeapAtTargetGoal(Mob mob) {
        this(mob, 2.0, 8.0, 1.2, 0.45, 60);
    }

    @Override
    public boolean shouldActivate() {
        if (currentCooldown > 0) {
            currentCooldown--;
            return false;
        }
        if (!mob.isValid() || mob.isDead() || !mob.isOnGround()) {
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isValid() || target.isDead() || !Objects.equals(mob.getWorld(), target.getWorld())) {
            return false;
        }
        double distSq = mob.getLocation().distanceSquared(target.getLocation());
        return distSq >= minDistanceSq && distSq <= maxDistanceSq;
    }

    @Override
    public boolean shouldStayActive() {
        return false; // Single impulse action on activate
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        Location mobLoc = mob.getLocation();
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.toVector().subtract(mobLoc.toVector()).setY(0);
        double length = direction.length();
        if (length > 0.0001) {
            direction.normalize().multiply(horizontalSpeed);
        } else {
            direction = mobLoc.getDirection().setY(0).normalize().multiply(horizontalSpeed);
        }
        direction.setY(upwardVelocity);

        try {
            mob.setVelocity(direction);
        } catch (Throwable ignored) {}

        this.currentCooldown = cooldownTicks;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return TYPES;
    }
}
