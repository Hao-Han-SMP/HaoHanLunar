package vn.haohan.lunar.core.mob.ai;

import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.threat.ThreatTable;
import vn.haohan.lunar.api.system.mob.ai.ThreatTargetGoal;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ThreatTargetGoalTest {

    @Test
    @DisplayName("ThreatTargetGoal key and goal types are properly initialized")
    void testGoalContract() {
        UUID mobId = UUID.randomUUID();
        Mob mockMob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return mobId;
                    if (m.getName().equals("isValid")) return true;
                    if (m.getName().equals("isDead")) return false;
                    return null;
                });

        ThreatTable table = new ThreatTable(mobId);
        ThreatTargetGoal goal = new ThreatTargetGoal(mockMob, table);

        assertNotNull(goal.getKey());
        assertEquals("threat_target", goal.getKey().getNamespacedKey().getKey());
        assertTrue(goal.getTypes().contains(GoalType.TARGET));
        assertSame(mockMob, goal.getMob());
        assertSame(table, goal.getThreatTable());
    }

    @Test
    @DisplayName("ThreatTargetGoal shouldActivate is false when threat table is empty or mob is invalid")
    void testActivationPreconditions() {
        UUID mobId = UUID.randomUUID();
        Mob mockMob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (p, m, a) -> {
                    if (m.getName().equals("getUniqueId")) return mobId;
                    if (m.getName().equals("isValid")) return false;
                    return null;
                });

        ThreatTable table = new ThreatTable(mobId);
        ThreatTargetGoal goal = new ThreatTargetGoal(mockMob, table);

        assertFalse(goal.shouldActivate());
        assertFalse(goal.shouldStayActive());
    }
}
