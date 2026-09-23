package vn.haohan.lunar.api.system.mob.ai;

import com.destroystokyo.paper.entity.RangedEntity;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.core.subsystem.mob.LunarMobManager;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Applies configured AI Goal and Target selectors to Bukkit/Paper mobs.
 */
public final class MobGoalApplier {

    public interface GoalExecutor {
        void clearGoals(Mob mob);
        void clearTargets(Mob mob);
        void addGoal(Mob mob, int priority, AIGoalEntry entry);
        default void addGoal(Mob mob, int priority, AIGoalEntry entry, Supplier<LunarMobManager> mobManagerSupplier) {
            addGoal(mob, priority, entry);
        }
        void addTarget(Mob mob, int priority, AIGoalEntry entry);
        default void addTarget(Mob mob, int priority, AIGoalEntry entry, ThreatTable threatTable) {
            addTarget(mob, priority, entry);
        }
        default void addTarget(Mob mob, int priority, AIGoalEntry entry, ThreatTable threatTable, Supplier<LunarMobManager> mobManagerSupplier) {
            addTarget(mob, priority, entry, threatTable);
        }
    }

    private GoalExecutor executor;

    public MobGoalApplier() {
        this(new PaperGoalExecutor());
    }

    public MobGoalApplier(GoalExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    public void setExecutor(GoalExecutor executor) {
        if (executor != null) {
            this.executor = executor;
        }
    }

    public GoalExecutor getExecutor() {
        return executor;
    }

    /**
     * Applies goal selectors and target selectors to the specified mob.
     */
    public void apply(Mob mob, List<String> goalSelectors, List<String> targetSelectors) {
        apply(mob, goalSelectors, targetSelectors, null, null);
    }

    public void apply(Mob mob, List<String> goalSelectors, List<String> targetSelectors, ThreatTable threatTable) {
        apply(mob, goalSelectors, targetSelectors, threatTable, null);
    }

    public void apply(Mob mob, List<String> goalSelectors, List<String> targetSelectors, ThreatTable threatTable, Supplier<LunarMobManager> mobManagerSupplier) {
        if (mob == null) return;

        if (goalSelectors != null && !goalSelectors.isEmpty()) {
            int priority = 1;
            for (String line : goalSelectors) {
                if (line == null || line.isBlank()) continue;
                AIGoalEntry entry = AIGoalEntry.parse(line);
                if (entry.isClear()) {
                    executor.clearGoals(mob);
                } else {
                    executor.addGoal(mob, priority++, entry, mobManagerSupplier);
                }
            }
        }

        if (targetSelectors != null && !targetSelectors.isEmpty()) {
            int priority = 1;
            for (String line : targetSelectors) {
                if (line == null || line.isBlank()) continue;
                AIGoalEntry entry = AIGoalEntry.parse(line);
                if (entry.isClear()) {
                    executor.clearTargets(mob);
                } else {
                    executor.addTarget(mob, priority++, entry, threatTable, mobManagerSupplier);
                }
            }
        }
    }

    /**
     * Production implementation utilizing Paper's MobGoals API.
     */
    public static final class PaperGoalExecutor implements GoalExecutor {

        @Override
        public void clearGoals(Mob mob) {
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals != null) {
                    for (Goal<Mob> goal : mobGoals.getAllGoalsWithout(mob, GoalType.TARGET)) {
                        mobGoals.removeGoal(mob, goal);
                    }
                }
            } catch (Throwable ignored) {}
        }

        @Override
        public void clearTargets(Mob mob) {
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals != null) {
                    mobGoals.removeAllGoals(mob, GoalType.TARGET);
                }
            } catch (Throwable ignored) {}
        }

        @Override
        public void addGoal(Mob mob, int priority, AIGoalEntry entry) {
            addGoal(mob, priority, entry, null);
        }

        @Override
        public void addGoal(Mob mob, int priority, AIGoalEntry entry, Supplier<LunarMobManager> mobManagerSupplier) {
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals == null) return;

                switch (entry.type()) {
                    case MELEE_ATTACK, MOVE_TO_TARGET -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.MELEE_ATTACK);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case FLEE_SUN -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.FLEE_SUN);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case RESTRICT_SUN -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.RESTRICT_SUN);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case PATROL -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.RANDOM_STROLL);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case LOOK_AT_PLAYERS -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.LOOK_AT_PLAYER);
                        if (g != null) mobGoals.addGoal(mob, priority, g);
                    }
                    case CIRCLE -> mobGoals.addGoal(mob, priority, new CircleTargetGoal(
                            mob,
                            entry.getParamOrArg("speed", 0, 1.0),
                            entry.getParamOrArg("radius", 1, 8.0)
                    ));
                    case FLEE_PLAYERS -> mobGoals.addGoal(mob, priority, new FleePlayersGoal(
                            mob,
                            entry.getParamOrArg("distance", 1, 8.0),
                            entry.getParamOrArg("speed", 0, 1.2)
                    ));
                    case FLEE_FACTION -> {
                        if (mobManagerSupplier != null) {
                            String faction = entry.getStringParamOrArg("faction", 0, "");
                            FleeFactionGoal.Mode mode = faction.isBlank() ? FleeFactionGoal.Mode.OTHER_FACTION : FleeFactionGoal.Mode.SPECIFIC_FACTION;
                            mobGoals.addGoal(mob, priority, new FleeFactionGoal(
                                    mob,
                                    mobManagerSupplier,
                                    mode,
                                    faction,
                                    entry.getParamOrArg("speed", 1, 1.2),
                                    entry.getParamOrArg("distance", 2, 10.0)
                            ));
                        }
                    }
                    case FLOAT -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.FLOAT);
                        if (g != null) {
                            mobGoals.addGoal(mob, priority, g);
                        } else {
                            mobGoals.addGoal(mob, priority, new FloatGoal(mob));
                        }
                    }
                    case LEAP_AT_TARGET -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.LEAP_AT);
                        if (g != null) {
                            mobGoals.addGoal(mob, priority, g);
                        } else {
                            mobGoals.addGoal(mob, priority, new LeapAtTargetGoal(
                                    mob,
                                    entry.getParamOrArg("mindistance", 0, 2.0),
                                    entry.getParamOrArg("maxdistance", 1, 8.0),
                                    entry.getParamOrArg("speed", 2, 1.2),
                                    entry.getParamOrArg("upward", 3, 0.45),
                                    (int) entry.getParamOrArg("cooldown", 4, 60.0)
                            ));
                        }
                    }
                    case OPEN_DOOR -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.OPEN_DOOR);
                        if (g != null) mobGoals.addGoal(mob, priority, g);
                    }
                    case BREAK_DOOR -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.BREAK_DOOR);
                        if (g != null) mobGoals.addGoal(mob, priority, g);
                    }
                    case PANIC -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.PANIC);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case BOW_ATTACK -> {
                        if (mob instanceof Monster monster) {
                            Goal<Monster> g = mobGoals.getGoal(monster, VanillaGoal.RANGED_BOW_ATTACK);
                            if (g != null) mobGoals.addGoal(monster, priority, g);
                        }
                    }
                    case CROSSBOW_ATTACK -> {
                        if (mob instanceof Monster monster) {
                            Goal<Monster> g = mobGoals.getGoal(monster, VanillaGoal.RANGED_CROSSBOW_ATTACK);
                            if (g != null) mobGoals.addGoal(monster, priority, g);
                        }
                    }
                    case RANGED_ATTACK -> {
                        if (mob instanceof RangedEntity ranged) {
                            Goal<RangedEntity> g = mobGoals.getGoal(ranged, VanillaGoal.RANGED_ATTACK);
                            if (g != null) mobGoals.addGoal(ranged, priority, g);
                        }
                    }
                    default -> {}
                }
            } catch (Throwable ignored) {}
        }

        @Override
        public void addTarget(Mob mob, int priority, AIGoalEntry entry) {
            addTarget(mob, priority, entry, null, null);
        }

        @Override
        public void addTarget(Mob mob, int priority, AIGoalEntry entry, ThreatTable threatTable) {
            addTarget(mob, priority, entry, threatTable, null);
        }

        @Override
        public void addTarget(Mob mob, int priority, AIGoalEntry entry, ThreatTable threatTable, Supplier<LunarMobManager> mobManagerSupplier) {
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals == null) return;

                switch (entry.type()) {
                    case TARGET_DAMAGERS -> {
                        if (mob instanceof Creature creature) {
                            Goal<Creature> g = mobGoals.getGoal(creature, VanillaGoal.HURT_BY);
                            if (g != null) mobGoals.addGoal(creature, priority, g);
                        }
                    }
                    case TARGET_PLAYERS -> {
                        Goal<Mob> g = mobGoals.getGoal(mob, VanillaGoal.NEAREST_ATTACKABLE);
                        if (g != null) {
                            mobGoals.addGoal(mob, priority, g);
                        } else {
                            mobGoals.addGoal(mob, priority, new NearestTargetGoal(
                                    mob,
                                    "target_players",
                                    e -> e instanceof Player p && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR,
                                    entry.getParamOrArg("radius", 0, 16.0),
                                    (int) entry.getParamOrArg("interval", 1, 10.0)
                            ));
                        }
                    }
                    case TARGET_THREAT -> {
                        if (threatTable != null) {
                            mobGoals.addGoal(mob, priority, new ThreatTargetGoal(mob, threatTable));
                        }
                    }
                    case TARGET_MONSTERS -> mobGoals.addGoal(mob, priority, new NearestTargetGoal(
                            mob,
                            "target_monsters",
                            e -> e instanceof Monster,
                            entry.getParamOrArg("radius", 0, 16.0),
                            (int) entry.getParamOrArg("interval", 1, 10.0)
                    ));
                    case TARGET_VILLAGERS -> mobGoals.addGoal(mob, priority, new NearestTargetGoal(
                            mob,
                            "target_villagers",
                            e -> e instanceof Villager,
                            entry.getParamOrArg("radius", 0, 16.0),
                            (int) entry.getParamOrArg("interval", 1, 10.0)
                    ));
                    case TARGET_OTHER_FACTION -> {
                        if (mobManagerSupplier != null) {
                            mobGoals.addGoal(mob, priority, new FactionTargetGoal(
                                    mob,
                                    mobManagerSupplier,
                                    FactionTargetGoal.Mode.OTHER_FACTION,
                                    "",
                                    entry.getParamOrArg("radius", 0, 16.0),
                                    entry.getBooleanParamOrArg("targetplayers", 1, true)
                            ));
                        }
                    }
                    case TARGET_SPECIFIC_FACTION -> {
                        if (mobManagerSupplier != null) {
                            String faction = entry.getStringParamOrArg("faction", 0, "");
                            mobGoals.addGoal(mob, priority, new FactionTargetGoal(
                                    mob,
                                    mobManagerSupplier,
                                    FactionTargetGoal.Mode.SPECIFIC_FACTION,
                                    faction,
                                    entry.getParamOrArg("radius", 1, 16.0),
                                    false
                            ));
                        }
                    }
                    default -> {}
                }
            } catch (Throwable ignored) {}
        }
    }
}
