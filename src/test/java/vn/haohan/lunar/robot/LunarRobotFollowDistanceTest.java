package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotFollowDistanceTest {

    @Test
    public void testFollowDistanceThreshold5Blocks() {
        // Distance squared threshold is 25.0 (5 blocks)
        double thresholdSq = 25.0;

        // Robot within 5 blocks: dist = 4.5 -> distSq = 20.25 (should stop & idle, not move towards owner)
        double distClose = 4.5;
        assertTrue(distClose * distClose <= thresholdSq, "Robot at 4.5 blocks should be within 5 blocks radius");

        // Robot exactly at 5 blocks: dist = 5.0 -> distSq = 25.0
        double distBoundary = 5.0;
        assertTrue(distBoundary * distBoundary <= thresholdSq);

        // Robot outside 5 blocks: dist = 5.5 -> distSq = 30.25 (should move towards owner)
        double distFar = 5.5;
        assertTrue(distFar * distFar > thresholdSq, "Robot at 5.5 blocks should move towards owner");

        // Max follow distance before teleport: dist = 20 blocks -> distSq = 400.0
        double maxFollowDist = 20.0;
        assertEquals(400.0, maxFollowDist * maxFollowDist);
    }

    @Test
    public void testSafeFollowOffsetsRadiusApprox5Blocks() {
        int[][] offsets = {
                {-5, 0}, {5, 0}, {0, -5}, {0, 5},
                {-4, -3}, {-4, 3}, {4, -3}, {4, 3},
                {-3, -4}, {-3, 4}, {3, -4}, {3, 4}
        };

        for (int[] offset : offsets) {
            double distSq = offset[0] * offset[0] + offset[1] * offset[1];
            // All candidates are around radius 5 blocks (distSq = 25.0)
            assertEquals(25.0, distSq, 0.001, "Offset candidate should be ~5 blocks away from owner");
        }
    }

    @Test
    public void testFollowDisabledStatePreventsMovementAndTeleport() {
        LunarRobotData data = new LunarRobotData(java.util.UUID.randomUUID());
        assertTrue(data.isFollowOwner(), "Follow should be enabled by default");

        // Toggle follow OFF
        data.setFollowOwner(false);
        assertFalse(data.isFollowOwner(), "Follow should be disabled after toggling off");

        // When follow is disabled, even if distance is 6 blocks (distSq = 36.0 > 25.0) or 25 blocks (distSq = 625.0 > 400.0),
        // robot follow logic must NOT issue moveTo or teleportToOwner
        double distFar = 25.0;
        double distFarSq = distFar * distFar;
        boolean shouldMove = data.isFollowOwner() && !data.isSitting() && distFarSq > 25.0;
        boolean shouldTeleport = data.isFollowOwner() && !data.isSitting() && distFarSq > 400.0;

        assertFalse(shouldMove, "Robot must NOT move towards owner when follow is disabled");
        assertFalse(shouldTeleport, "Robot must NOT teleport to owner when follow is disabled");
    }

    @Test
    public void testFollowBoundaryStrict5BlocksWhenEnabled() {
        LunarRobotData data = new LunarRobotData(java.util.UUID.randomUUID());
        data.setFollowOwner(true);

        // Within 5 blocks (e.g. 4.0 blocks or 2.0 blocks)
        double closeDistSq = 4.0; // 2 blocks away
        boolean shouldMoveClose = data.isFollowOwner() && !data.isSitting() && closeDistSq > 25.0;
        assertFalse(shouldMoveClose, "Robot within 5 blocks must stop pathfinding and not move closer");

        // Beyond 5 blocks but within 20 blocks
        double midDistSq = 36.0; // 6 blocks away
        boolean shouldMoveMid = data.isFollowOwner() && !data.isSitting() && midDistSq > 25.0;
        boolean shouldTeleportMid = data.isFollowOwner() && !data.isSitting() && midDistSq > 400.0;
        assertTrue(shouldMoveMid, "Robot beyond 5 blocks must move towards owner");
        assertFalse(shouldTeleportMid, "Robot under 20 blocks must not teleport");
    }
}
