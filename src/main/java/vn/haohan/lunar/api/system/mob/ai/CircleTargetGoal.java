package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Tactical Goal causing the mob to orbit/circle around its target at a set radius and speed.
 */
public class CircleTargetGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "circle_target"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.MOVE, GoalType.LOOK);

    private final Mob mob;
    private final double radius;
    private final double speed;
    private double currentAngle;
    private int tickCounter = 0;

    public CircleTargetGoal(Mob mob, double speed, double radius) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.speed = speed > 0 ? speed : 1.0;
        this.radius = radius > 0 ? radius : 8.0;
        this.currentAngle = Math.random() * Math.PI * 2;
    }

    @Override
    public boolean shouldActivate() {
        LivingEntity target = mob.getTarget();
        return mob.isValid() && target != null && target.isValid() && !target.isDead()
                && Objects.equals(mob.getWorld(), target.getWorld());
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        currentAngle += 0.08;
        if (currentAngle > Math.PI * 2) {
            currentAngle -= Math.PI * 2;
        }

        if (++tickCounter % 2 == 0) {
            Location tLoc = target.getLocation();
            double x = tLoc.getX() + Math.cos(currentAngle) * radius;
            double z = tLoc.getZ() + Math.sin(currentAngle) * radius;
            Location dest = new Location(tLoc.getWorld(), x, tLoc.getY(), z);
            try {
                if (mob.getPathfinder() != null) {
                    mob.getPathfinder().moveTo(dest, speed);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return TYPES;
    }

    public double getRadius() {
        return radius;
    }

    public double getSpeed() {
        return speed;
    }
}
