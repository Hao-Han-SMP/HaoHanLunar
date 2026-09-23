package vn.haohan.lunar.core.mob.ai;

import org.bukkit.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.api.system.mob.ai.AIGoalEntry;
import vn.haohan.lunar.api.system.mob.ai.AIGoalType;
import vn.haohan.lunar.api.system.mob.ai.MobGoalApplier;

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

        AIGoalEntry threat = AIGoalEntry.parse("threat");
        assertEquals(AIGoalType.TARGET_THREAT, threat.type());

        AIGoalEntry circle = AIGoalEntry.parse("circle{radius=10;speed=1.5}");
        assertEquals(AIGoalType.CIRCLE, circle.type());
        assertEquals(10.0, circle.getParamOrArg("radius", 1, 8.0), 0.001);
        assertEquals(1.5, circle.getParamOrArg("speed", 0, 1.0), 0.001);

        AIGoalEntry fleePlayers = AIGoalEntry.parse("fleeplayers{distance=12.5;speed=1.8}");
        assertEquals(AIGoalType.FLEE_PLAYERS, fleePlayers.type());
        assertEquals(12.5, fleePlayers.getParamOrArg("distance", 1, 8.0), 0.001);
        assertEquals(1.8, fleePlayers.getParamOrArg("speed", 0, 1.2), 0.001);
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

            @Override
            public void addTarget(Mob mob, int priority, AIGoalEntry entry, ThreatTable threatTable) {
                actions.add("addTargetWithThreat:" + priority + ":" + entry.type() + ":" + (threatTable != null));
            }
        };

        MobGoalApplier applier = new MobGoalApplier(mockExecutor);

        Mob mockMob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return UUID.randomUUID();
                    return null;
                });

        ThreatTable threatTable = new ThreatTable(UUID.randomUUID());

        List<String> goalSelectors = List.of(
                "clear",
                "movetotarget 1.25",
                "circle{radius=10;speed=1.5}",
                "fleeplayers{distance=12;speed=1.8}"
        );

        List<String> targetSelectors = List.of(
                "clear",
                "targetdamagers",
                "targetthreat"
        );

        applier.apply(mockMob, goalSelectors, targetSelectors, threatTable);

        // Verification order
        assertEquals("clearGoals", actions.get(0));
        assertEquals("addGoal:1:MOVE_TO_TARGET", actions.get(1));
        assertEquals("addGoal:2:CIRCLE", actions.get(2));
        assertEquals("addGoal:3:FLEE_PLAYERS", actions.get(3));

        assertEquals("clearTargets", actions.get(4));
        assertEquals("addTargetWithThreat:1:TARGET_DAMAGERS:true", actions.get(5));
        assertEquals("addTargetWithThreat:2:TARGET_THREAT:true", actions.get(6));
    }
}
