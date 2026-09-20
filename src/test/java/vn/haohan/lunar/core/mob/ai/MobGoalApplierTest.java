package vn.haohan.lunar.core.mob.ai;

import vn.haohan.lunar.api.mob.ai.*;

import org.bukkit.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MobGoalApplierTest {

    @Test
    @DisplayName("AIGoalEntry parses goal and target syntax properly")
    void testAIGoalEntryParsing() {
        AIGoalEntry clear = AIGoalEntry.parse("clear");
        assertTrue(clear.isClear());
        assertEquals(AIGoalType.CLEAR, clear.type());

        AIGoalEntry move = AIGoalEntry.parse("movetotarget 1.25");
        assertEquals(AIGoalType.MOVE_TO_TARGET, move.type());
        assertEquals(1.25, move.getDoubleArg(0, 1.0), 0.001);

        AIGoalEntry melee = AIGoalEntry.parse("meleeattack 1.2");
        assertEquals(AIGoalType.MELEE_ATTACK, melee.type());
        assertEquals(1.2, melee.getDoubleArg(0, 1.0), 0.001);

        AIGoalEntry look = AIGoalEntry.parse("lookatplayers 8.0");
        assertEquals(AIGoalType.LOOK_AT_PLAYERS, look.type());
        assertEquals(8.0, look.getDoubleArg(0, 5.0), 0.001);

        AIGoalEntry patrol = AIGoalEntry.parse("patrol 1.0");
        assertEquals(AIGoalType.PATROL, patrol.type());
        assertEquals(1.0, patrol.getDoubleArg(0, 1.0), 0.001);

        AIGoalEntry fleeSun = AIGoalEntry.parse("flee_sun 1.5");
        assertEquals(AIGoalType.FLEE_SUN, fleeSun.type());
        assertEquals(1.5, fleeSun.getDoubleArg(0, 1.0), 0.001);

        AIGoalEntry damagers = AIGoalEntry.parse("targetdamagers");
        assertEquals(AIGoalType.TARGET_DAMAGERS, damagers.type());

        AIGoalEntry players = AIGoalEntry.parse("targetplayers");
        assertEquals(AIGoalType.TARGET_PLAYERS, players.type());
    }

    @Test
    @DisplayName("MobGoalApplier processes clear flag and registers goals with ascending priorities")
    void testMobGoalApplierExecution() {
        List<String> actions = new ArrayList<>();

        MobGoalApplier.GoalExecutor mockExecutor = new MobGoalApplier.GoalExecutor() {
            @Override
            public void clearGoals(Mob mob) {
                actions.add("clearGoals");
            }

            @Override
            public void clearTargets(Mob mob) {
                actions.add("clearTargets");
            }

            @Override
            public void addGoal(Mob mob, int priority, AIGoalEntry entry) {
                actions.add("addGoal:" + priority + ":" + entry.type());
            }

            @Override
            public void addTarget(Mob mob, int priority, AIGoalEntry entry) {
                actions.add("addTarget:" + priority + ":" + entry.type());
            }
        };

        MobGoalApplier applier = new MobGoalApplier(mockExecutor);

        Mob mockMob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return UUID.randomUUID();
                    return null;
                });

        List<String> goalSelectors = List.of(
                "clear",
                "movetotarget 1.25",
                "meleeattack 1.2",
                "lookatplayers 8.0"
        );

        List<String> targetSelectors = List.of(
                "clear",
                "targetdamagers",
                "targetplayers"
        );

        applier.apply(mockMob, goalSelectors, targetSelectors);

        // Verification order
        assertEquals("clearGoals", actions.get(0));
        assertEquals("addGoal:1:MOVE_TO_TARGET", actions.get(1));
        assertEquals("addGoal:2:MELEE_ATTACK", actions.get(2));
        assertEquals("addGoal:3:LOOK_AT_PLAYERS", actions.get(3));

        assertEquals("clearTargets", actions.get(4));
        assertEquals("addTarget:1:TARGET_DAMAGERS", actions.get(5));
        assertEquals("addTarget:2:TARGET_PLAYERS", actions.get(6));
    }
}
