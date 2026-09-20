package vn.haohan.lunar.core.system.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLunarDataTest {
    @Test
    void oxygenIsBoundedToTheCoreReserveRange() {
        PlayerLunarData data = new PlayerLunarData(null);

        data.setOxygen(-20);
        assertEquals(0, data.getOxygen());

        data.setOxygen(999);
        assertEquals(600, data.getOxygen());
    }

    @Test
    void tankStateRetainsItsConfiguredValues() {
        PlayerLunarData data = new PlayerLunarData(null);

        assertFalse(data.isTankActive());
        data.setTankTier(3);
        data.setTankO2(6800);
        data.setTankActive(true);

        assertEquals(3, data.getTankTier());
        assertEquals(6800, data.getTankO2());
        assertTrue(data.isTankActive());
    }
}
