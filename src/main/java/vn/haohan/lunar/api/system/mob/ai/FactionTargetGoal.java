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
import vn.haohan.lunar.core.subsystem.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Tactical Target Goal selecting enemies based on Faction alignment.
 * Supports targeting opposing factions or a designated specific faction.
 * Paper 1.21.1 Native Goal.
 */
public class FactionTargetGoal implements Goal<Mob> {

    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("haohanlunar", "faction_target"));
    private static final EnumSet<GoalType> TYPES = EnumSet.of(GoalType.TARGET);

    public enum Mode {
        OTHER_FACTION,
        SPECIFIC_FACTION
    }

    private final Mob mob;
    private final Supplier<LunarMobManager> mobManagerSupplier;
    private final Mode mode;
    private final String targetFaction;
    private final double radius;
    private final boolean targetPlayers;
    private LivingEntity currentTarget;
    private int checkIntervalTicks = 10;
    private int tickCounter = 0;

    public FactionTargetGoal(Mob mob,
                             Supplier<LunarMobManager> mobManagerSupplier,
                             Mode mode,
                             String targetFaction,
                             double radius,
                             boolean targetPlayers) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.mobManagerSupplier = Objects.requireNonNull(mobManagerSupplier, "mobManagerSupplier must not be null");
        this.mode = Objects.requireNonNull(mode, "Mode must not be null");
        this.targetFaction = targetFaction != null ? targetFaction.trim() : "";
        this.radius = radius > 0 ? radius : 16.0;
        this.targetPlayers = targetPlayers;
    }

    @Override
    public boolean shouldActivate() {
        if (!mob.isValid() || mob.isDead() || mob.getWorld() == null) return false;
        if (++tickCounter % checkIntervalTicks != 0) {
            return false;
        }

        LivingEntity existing = mob.getTarget();
        if (existing != null && existing.isValid() && !existing.isDead()) {
            return false; // Already pursuing a valid target
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
        LunarMobManager manager = mobManagerSupplier.get();
        if (manager == null) return null;

        ActiveMob selfActiveMob = manager.get(mob.getUniqueId());
        String myFaction = selfActiveMob != null ? selfActiveMob.faction() : null;

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
                if (!targetPlayers) {
                    continue;
                }
            }

            double distSq = center.distanceSquared(living.getLocation());
            if (distSq > radiusSq || distSq >= closestDistSq) {
                continue;
            }

            ActiveMob otherActiveMob = manager.get(living.getUniqueId());
            String otherFaction = otherActiveMob != null ? otherActiveMob.faction() : null;

            boolean isMatch = false;
            if (mode == Mode.OTHER_FACTION) {
                // If other is a mob from a different faction
                if (otherFaction != null && !otherFaction.isBlank()) {
                    if (myFaction == null || !myFaction.equalsIgnoreCase(otherFaction)) {
                        isMatch = true;
                    }
                } else if (living instanceof Player && targetPlayers) {
                    // Hostile to players without same faction
                    isMatch = true;
                }
            } else if (mode == Mode.SPECIFIC_FACTION) {
                if (otherFaction != null && otherFaction.equalsIgnoreCase(targetFaction)) {
                    isMatch = true;
                }
            }

            if (isMatch) {
                closest = living;
                closestDistSq = distSq;
            }
        }

        return closest;
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
