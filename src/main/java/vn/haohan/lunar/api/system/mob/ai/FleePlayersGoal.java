package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Tactical Goal causing the mob to flee from nearby players when within danger distance.
 */
public class FleePlayersGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "flee_players"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.MOVE);

    private final Mob mob;
    private final double fleeDistance;
    private final double speed;
    private Player threatPlayer;
    private int tickCounter = 0;

    public FleePlayersGoal(Mob mob, double speed, double fleeDistance) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.speed = speed > 0 ? speed : 1.2;
        this.fleeDistance = fleeDistance > 0 ? fleeDistance : 8.0;
    }

    @Override
    public boolean shouldActivate() {
        if (!mob.isValid() || mob.getWorld() == null) return false;
        double distSq = fleeDistance * fleeDistance;
        for (Player player : mob.getWorld().getPlayers()) {
            if (player.isValid() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
                if (mob.getLocation().distanceSquared(player.getLocation()) <= distSq) {
                    this.threatPlayer = player;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean shouldStayActive() {
        if (!mob.isValid() || threatPlayer == null || !threatPlayer.isValid() || threatPlayer.isDead()) {
            return false;
        }
        return mob.getLocation().distanceSquared(threatPlayer.getLocation()) <= (fleeDistance * fleeDistance * 1.5);
    }

    @Override
    public void tick() {
        if (threatPlayer == null) return;
        if (++tickCounter % 3 == 0) {
            Vector dir = mob.getLocation().toVector().subtract(threatPlayer.getLocation().toVector()).normalize();
            if (dir.lengthSquared() == 0) {
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
        this.threatPlayer = null;
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
}
