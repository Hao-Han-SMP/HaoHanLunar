package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Tactical Goal causing the mob to swim/float up when submersed in water or lava.
 * Paper 1.21.1 Native Goal.
 */
public class FloatGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "float_goal"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.JUMP);

    private final Mob mob;

    public FloatGoal(Mob mob) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
    }

    @Override
    public boolean shouldActivate() {
        return mob.isValid() && !mob.isDead() && (mob.isInWater() || mob.isInLava());
    }

    @Override
    public boolean shouldStayActive() {
        return shouldActivate();
    }

    @Override
    public void tick() {
        if (mob.isInWater() || mob.isInLava()) {
            Vector vel = mob.getVelocity();
            if (vel.getY() < 0.1) {
                try {
                    mob.setVelocity(new Vector(vel.getX(), 0.12, vel.getZ()));
                } catch (Throwable ignored) {}
            }
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
}
