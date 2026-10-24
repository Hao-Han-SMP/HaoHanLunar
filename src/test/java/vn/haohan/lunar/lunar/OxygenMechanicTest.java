package vn.haohan.lunar.lunar;

import vn.haohan.lunar.features.OxygenMechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Regression coverage for fail-closed safe-zone queries and oxygen constants. */
class OxygenMechanicTest {
    @Test
    void nullLocationsNeverReachBukkitStructureLookup() {
        OxygenMechanic oxygen = new OxygenMechanic(null);

        assertFalse(oxygen.isInSafeZone(null));
        assertFalse(oxygen.isInRestBase(null));
        assertFalse(oxygen.isInSpaceStation(null));
    }

    @Test
    void maxAirConstantsMatchSpecifications() {
        assertEquals(600, OxygenMechanic.LUNAR_MAX_AIR);
        assertEquals(300, OxygenMechanic.VANILLA_DEFAULT_AIR);
        assertEquals(300, OxygenMechanic.VANILLA_HUD_MAX_AIR);
    }

    @Test
    void calculateHudAirAlwaysKeepsBubblesVisibleOnLand() {
        // Full oxygen (600) maps to 299 (< 300) so client renders all 10 bubbles
        assertEquals(299, OxygenMechanic.calculateHudAir(600));

        // Half oxygen (300) maps proportionally
        assertEquals(150, OxygenMechanic.calculateHudAir(300));

        // Empty oxygen (0) maps to 0
        assertEquals(0, OxygenMechanic.calculateHudAir(0));
        assertEquals(0, OxygenMechanic.calculateHudAir(-5));
    }
}
