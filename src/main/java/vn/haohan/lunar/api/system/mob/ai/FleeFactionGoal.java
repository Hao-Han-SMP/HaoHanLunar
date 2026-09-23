package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.subsystem.mob.LunarMobManager;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Tactical Goal causing the mob to flee from entities belonging to an opposing or specified faction.
 */
public class FleeFactionGoal implements Goal<Mob> {

    public enum Mode {
        OTHER_FACTION,
        SPECIFIC_FACTION
    }

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "flee_faction"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.MOVE);

    private final Mob mob;
    private final Supplier<LunarMobManager> mobManagerSupplier;
    private final Mode mode;
    private final String targetFaction;
    private final double fleeDistance;
    private final double speed;

    private LivingEntity threatEntity;
    private int tickCounter = 0;

    public FleeFactionGoal(Mob mob,
                           Supplier<LunarMobManager> mobManagerSupplier,
                           Mode mode,
                           String targetFaction,
                           double speed,
                           double fleeDistance) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.mobManagerSupplier = Objects.requireNonNull(mobManagerSupplier, "mobManagerSupplier must not be null");
        this.mode = mode != null ? mode : Mode.OTHER_FACTION;
        this.targetFaction = targetFaction != null ? targetFaction.trim() : "";
        this.speed = speed > 0 ? speed : 1.2;
        this.fleeDistance = fleeDistance > 0 ? fleeDistance : 10.0;
    }

    @Override
    public boolean shouldActivate() {
        if (!mob.isValid() || mob.getWorld() == null) return false;
        LunarMobManager manager = mobManagerSupplier.get();
        if (manager == null) return false;

        ActiveMob selfActiveMob = manager.get(mob.getUniqueId());
        String selfFaction = selfActiveMob != null ? selfActiveMob.faction() : "";

        double distSq = fleeDistance * fleeDistance;
        for (Entity entity : mob.getNearbyEntities(fleeDistance, fleeDistance, fleeDistance)) {
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                continue;
            }
            if (entity.getUniqueId().equals(mob.getUniqueId())) continue;

            ActiveMob targetActiveMob = manager.get(entity.getUniqueId());
            if (isThreat(selfFaction, targetActiveMob)) {
                if (mob.getLocation().distanceSquared(living.getLocation()) <= distSq) {
                    this.threatEntity = living;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isThreat(String selfFaction, ActiveMob targetActiveMob) {
        if (mode == Mode.SPECIFIC_FACTION) {
            return targetActiveMob != null && targetActiveMob.faction().equalsIgnoreCase(targetFaction);
        } else {
            // Mode.OTHER_FACTION
            if (targetActiveMob == null) return false;
            String targetFac = targetActiveMob.faction();
            return !targetFac.isBlank() && !targetFac.equalsIgnoreCase(selfFaction);
        }
    }

    @Override
    public boolean shouldStayActive() {
        if (!mob.isValid() || threatEntity == null || !threatEntity.isValid() || threatEntity.isDead()) {
            return false;
        }
        return mob.getLocation().distanceSquared(threatEntity.getLocation()) <= (fleeDistance * fleeDistance * 1.5);
    }

    @Override
    public void tick() {
        if (threatEntity == null) return;
        if (++tickCounter % 3 == 0) {
            Vector dir = mob.getLocation().toVector().subtract(threatEntity.getLocation().toVector()).setY(0).normalize();
            if (dir.lengthSquared() == 0 || Double.isNaN(dir.getX())) {
                dir = new Vector(1, 0, 0);
            }
            Location fleeDest = mob.getLocation().add(dir.multiply(fleeDistance));
            try {
                if (mob.getPathfinder() != null) {
                    mob.getPathfinder().moveTo(fleeDest, speed);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void stop() {
        this.threatEntity = null;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return TYPES;
    }

    public double getFleeDistance() {
        return fleeDistance;
    }

    public double getSpeed() {
        return speed;
    }

    public Mode getMode() {
        return mode;
    }

    public String getTargetFaction() {
        return targetFaction;
    }
}
