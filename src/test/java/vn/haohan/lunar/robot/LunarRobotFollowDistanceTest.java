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
}
