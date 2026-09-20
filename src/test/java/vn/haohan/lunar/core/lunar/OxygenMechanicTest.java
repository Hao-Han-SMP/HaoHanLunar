package vn.haohan.lunar.core.lunar;

import vn.haohan.lunar.core.features.OxygenMechanic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Regression coverage for fail-closed safe-zone queries. */
class OxygenMechanicTest {
    @Test
    void nullLocationsNeverReachBukkitStructureLookup() {
        OxygenMechanic oxygen = new OxygenMechanic(null);

        assertFalse(oxygen.isInSafeZone(null));
        assertFalse(oxygen.isInRestBase(null));
        assertFalse(oxygen.isInSpaceStation(null));
    }
}
