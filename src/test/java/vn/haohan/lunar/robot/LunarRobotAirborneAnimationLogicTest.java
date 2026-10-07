package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotAirborneAnimationLogicTest {

    static class AirborneStateMachine {
        int airborneTicks = 0;
        boolean isLandingPrepared = false;
        String currentAnimation = "idle";

        public String update(boolean onGround, double distToGround, double vy) {
            if (onGround) {
                airborneTicks = 0;
                isLandingPrepared = false;
                currentAnimation = "idle";
                return currentAnimation;
            }

            airborneTicks++;

            // Nearing ground while descending (distance 1.5 - 2.5 blocks)
            if (airborneTicks >= 3 && vy <= 0.20 && distToGround <= 2.5) {
                if (!isLandingPrepared) {
                    isLandingPrepared = true;
                    currentAnimation = "air_leap_land";
                }
                return currentAnimation;
            }

            // Launching into the air (first 10 ticks = 0.5s)
            if (airborneTicks < 10) {
                if (!"air_leap_start".equals(currentAnimation) && !isLandingPrepared) {
                    currentAnimation = "air_leap_start";
                }
                return currentAnimation;
            }

            // Full mid-air glide loop
            if (!isLandingPrepared) {
                currentAnimation = "air_leap";
            }
            return currentAnimation;
        }
    }

    @Test
    public void testAirborneCycleTransitions() {
        AirborneStateMachine sm = new AirborneStateMachine();

        // 1. Standing on ground
        assertEquals("idle", sm.update(true, 0.0, 0.0));
        assertEquals(0, sm.airborneTicks);
        assertFalse(sm.isLandingPrepared);

        // 2. Leaves ground: Takeoff (ticks 1 to 9 -> air_leap_start)
        for (int t = 1; t < 10; t++) {
            assertEquals("air_leap_start", sm.update(false, 8.0, 0.4), "Tick " + t + " should play air_leap_start");
            assertEquals(t, sm.airborneTicks);
            assertFalse(sm.isLandingPrepared);
        }

        // 3. Full airborne glide (tick 10+ -> air_leap)
        for (int t = 10; t <= 20; t++) {
            assertEquals("air_leap", sm.update(false, 10.0, 0.0), "Tick " + t + " should play air_leap");
            assertEquals(t, sm.airborneTicks);
            assertFalse(sm.isLandingPrepared);
        }

        // 4. Descending towards ground: reaches 2.2 blocks altitude (within 1.5 - 2.5 blocks)
        String landingAnim = sm.update(false, 2.2, -0.4);
        assertEquals("air_leap_land", landingAnim, "Should transition to air_leap_land when near ground");
        assertTrue(sm.isLandingPrepared);

        // 5. Continues landing preparation while still in the air
        assertEquals("air_leap_land", sm.update(false, 1.2, -0.5));
        assertTrue(sm.isLandingPrepared);

        // 6. Touchdown: on ground!
        assertEquals("idle", sm.update(true, 0.0, 0.0), "Should reset to ground idle when on ground");
        assertEquals(0, sm.airborneTicks);
        assertFalse(sm.isLandingPrepared);
    }
}
