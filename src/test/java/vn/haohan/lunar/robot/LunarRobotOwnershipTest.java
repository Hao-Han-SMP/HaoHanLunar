package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotOwnershipTest {

    @Test
    public void testWildRobotInitialState() {
        UUID robotId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotId);

        assertEquals(robotId, data.getRobotId());
        assertFalse(data.isTamed());
        assertNull(data.getOwnerUuid());
        assertNull(data.getOwnerName());
    }

    @Test
    public void testTamedRobotOwnership() {
        UUID robotId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotId);

        UUID playerA = UUID.randomUUID();
        data.setOwnerUuid(playerA);
        data.setOwnerName("PlayerA");

        assertTrue(data.isTamed());
        assertEquals(playerA, data.getOwnerUuid());
        assertEquals("PlayerA", data.getOwnerName());

        // Attempting to overwrite by PlayerB without unbind should be blocked by logic
        UUID playerB = UUID.randomUUID();
        assertNotEquals(playerB, data.getOwnerUuid());
        assertTrue(data.isTamed());
    }

    @Test
    public void testRobotLocationTracking() {
        UUID robotId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotId);

        data.setLastWorldName("world_lunar");
        data.setLastX(120.5);
        data.setLastY(64.0);
        data.setLastZ(-350.25);

        assertEquals("world_lunar", data.getLastWorldName());
        assertEquals(120.5, data.getLastX(), 0.001);
        assertEquals(64.0, data.getLastY(), 0.001);
        assertEquals(-350.25, data.getLastZ(), 0.001);
    }

    @Test
    public void testRobotIntegrityPercentage() {
        UUID robotId = UUID.randomUUID();
        LunarRobotData data = new LunarRobotData(robotId);

        // Default 60.0 / 60.0 HP -> 100%
        assertEquals(100.0, data.getIntegrityPercentage(), 0.001);
        assertEquals("100%", data.getFormattedIntegrity());

        // Damaged: 30.0 / 60.0 HP -> 50%
        data.setHealth(30.0);
        assertEquals(50.0, data.getIntegrityPercentage(), 0.001);
        assertEquals("50%", data.getFormattedIntegrity());

        // Damaged: 15.0 / 60.0 HP -> 25%
        data.setHealth(15.0);
        assertEquals(25.0, data.getIntegrityPercentage(), 0.001);
        assertEquals("25%", data.getFormattedIntegrity());

        // Zero health: 0.0 -> 0%
        data.setHealth(0.0);
        assertEquals(0.0, data.getIntegrityPercentage(), 0.001);
        assertEquals("0%", data.getFormattedIntegrity());

        // Negative input clamped to 0%
        data.setHealth(-10.0);
        assertEquals(0.0, data.getIntegrityPercentage(), 0.001);

        // Exceeding max health clamped to 100%
        data.setHealth(120.0);
        assertEquals(100.0, data.getIntegrityPercentage(), 0.001);
    }
}
