package vn.haohan.lunar.api.mob.ai;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import org.bukkit.Bukkit;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Mob;

import java.util.List;
import java.util.Objects;

/**
 * Applies configured AI Goal and Target selectors to Bukkit/Paper mobs.
 */
public final class MobGoalApplier {

    public interface GoalExecutor {
        void clearGoals(Mob mob);
        void clearTargets(Mob mob);
        void addGoal(Mob mob, int priority, AIGoalEntry entry);
        void addTarget(Mob mob, int priority, AIGoalEntry entry);
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
        if (mob == null) return;

        if (goalSelectors != null && !goalSelectors.isEmpty()) {
            int priority = 1;
            for (String line : goalSelectors) {
                if (line == null || line.isBlank()) continue;
                AIGoalEntry entry = AIGoalEntry.parse(line);
                if (entry.isClear()) {
                    executor.clearGoals(mob);
                } else {
                    executor.addGoal(mob, priority++, entry);
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
                    executor.addTarget(mob, priority++, entry);
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
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals == null) return;

                switch (entry.type()) {
                    case MELEE_ATTACK -> {
                        if (mob instanceof Creature creature) {
                            mobGoals.addGoal(creature, priority, mobGoals.getGoal(creature, VanillaGoal.MELEE_ATTACK));
                        }
                    }
                    case FLEE_SUN -> {
                        if (mob instanceof Creature creature) {
                            mobGoals.addGoal(creature, priority, mobGoals.getGoal(creature, VanillaGoal.FLEE_SUN));
                        }
                    }
                    case PATROL -> {
                        if (mob instanceof Creature creature) {
                            mobGoals.addGoal(creature, priority, mobGoals.getGoal(creature, VanillaGoal.RANDOM_STROLL));
                        }
                    }
                    case LOOK_AT_PLAYERS -> mobGoals.addGoal(mob, priority, mobGoals.getGoal(mob, VanillaGoal.LOOK_AT_PLAYER));
                    default -> {}
                }
            } catch (Throwable ignored) {}
        }

        @Override
        public void addTarget(Mob mob, int priority, AIGoalEntry entry) {
            try {
                MobGoals mobGoals = Bukkit.getMobGoals();
                if (mobGoals == null) return;

                switch (entry.type()) {
                    case TARGET_DAMAGERS -> {
                        if (mob instanceof Creature creature) {
                            mobGoals.addGoal(creature, priority, mobGoals.getGoal(creature, VanillaGoal.HURT_BY));
                        }
                    }
                    case TARGET_PLAYERS -> mobGoals.addGoal(mob, priority, mobGoals.getGoal(mob, VanillaGoal.NEAREST_ATTACKABLE));
                    default -> {}
                }
            } catch (Throwable ignored) {}
        }
    }
}
