package vn.haohan.lunar.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoonUtilTest {

    @Test
    void testIsNightTime() {
        // Day ticks
        assertFalse(MoonUtil.isNightTime(0));
        assertFalse(MoonUtil.isNightTime(6000));
        assertFalse(MoonUtil.isNightTime(12000));

        // Night ticks
        assertTrue(MoonUtil.isNightTime(13000));
        assertTrue(MoonUtil.isNightTime(18000)); // Midnight
        assertTrue(MoonUtil.isNightTime(23000));

        // Edge of sunrise
        assertFalse(MoonUtil.isNightTime(24000));
        assertFalse(MoonUtil.isNightTime(24100)); // Wrapped modulo 24000 -> 100 (day)
    }

    @Test
    void testGetMoonDirectionMidnight() {
        // At midnight (18000), moon is at zenith (straight up: y ~ 1.0)
        Vector dir = MoonUtil.getMoonDirection(18000);
        assertNotNull(dir);
        assertTrue(dir.getY() > 0.95, "At midnight, moon should be straight overhead with Y close to 1.0, was: " + dir.getY());
        assertEquals(1.0, dir.length(), 0.001, "Direction vector must be normalized");
    }

    @Test
    void testIsLookingAtMoonAngleCalculation() {
        // At midnight, moon is (0, 1, 0)
        Vector moonDir = MoonUtil.getMoonDirection(18000);

        // Player looking straight up (0, 1, 0) -> angle = 0 deg <= 14 deg -> true
        Vector playerLookingUp = new Vector(0, 1, 0).normalize();
        assertTrue(MoonUtil.isAngleClose(playerLookingUp, moonDir, 14.0));

        // Player looking slightly tilted (pitch ~ 80 deg instead of 90 deg) -> angle ~ 10 deg <= 14 deg -> true
        Vector playerLookingSlightTilt = new Vector(0.15, 0.98, 0).normalize();
        assertTrue(MoonUtil.isAngleClose(playerLookingSlightTilt, moonDir, 14.0));

        // Player looking horizontally (0, 0, 1) -> angle = 90 deg > 14 deg -> false
        Vector playerLookingHorizon = new Vector(0, 0, 1).normalize();
        assertFalse(MoonUtil.isAngleClose(playerLookingHorizon, moonDir, 14.0));

        // Player looking down (0, -1, 0) -> false
        Vector playerLookingDown = new Vector(0, -1, 0).normalize();
        assertFalse(MoonUtil.isAngleClose(playerLookingDown, moonDir, 14.0));
    }
}
