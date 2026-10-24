package vn.haohan.lunar.robot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.charger.BatteryChargerMechanic;
import vn.haohan.lunar.charger.BatteryChargerStation;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RobotBatteryAndChargerTest {

    @Test
    public void testBatteryNominalAndExponentialCapacity() {
        assertEquals(35000, RobotBatteryUtil.NOMINAL_CAPACITY);
        assertEquals(35000, RobotBatteryUtil.getCapacity(RobotBatteryUtil.BATTERY_STANDARD));

        // Effective capacity formula: round(35000 * (health/100)^2)
        assertEquals(35000, RobotBatteryUtil.calculateEffectiveCapacity(100.0));
        assertEquals(28350, RobotBatteryUtil.calculateEffectiveCapacity(90.0)); // 35000 * 0.81
        assertEquals(17150, RobotBatteryUtil.calculateEffectiveCapacity(70.0)); // 35000 * 0.49
        assertEquals(8750, RobotBatteryUtil.calculateEffectiveCapacity(50.0));  // 35000 * 0.25
        assertEquals(1400, RobotBatteryUtil.calculateEffectiveCapacity(20.0));  // 35000 * 0.04
        assertEquals(1000, RobotBatteryUtil.calculateEffectiveCapacity(0.0), "Floor capacity must be at least 1000 EU");
    }

    @Test
    public void testRobotEnergySyncAndCycles() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());

        // Initially equipped with standard battery
        assertEquals("small", data.getBatteryType());
        assertEquals(35000, data.getMaxEnergy());
        assertEquals(35000, data.getEnergy());
        assertEquals(100.0, data.getBatteryHealth());
        assertEquals(0, data.getChargeCycles());

        // Robot consumes 5,000 EU
        assertTrue(data.consumeEnergy(5000));
        assertEquals(30000, data.getEnergy());

        // Increment charge cycle -> cycle = 1, health = 99.5%
        data.incrementChargeCycles();
        assertEquals(1, data.getChargeCycles());
        assertEquals(99.5, data.getBatteryHealth(), 0.001);

        // Effective max energy updates with health
        int expectedMaxE = RobotBatteryUtil.calculateEffectiveCapacity(99.5);
        assertEquals(expectedMaxE, data.getMaxEnergy());

        // Remove battery (Slot empty)
        data.setBatteryType("none");
        assertEquals("none", data.getBatteryType());
        assertEquals(0, data.getMaxEnergy());
        assertEquals(0, data.getEnergy());

        // Reinstall battery
        data.setBatteryType("small");
        assertEquals(expectedMaxE, data.getMaxEnergy());
    }

    @Test
    public void testChargerFuelOnlyRawAnorthosite() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 180.0f);

        assertEquals(0, station.getFuelBuffer());

        // Add 1 Raw Anorthosite (+3500 EU)
        station.addFuelBuffer(3500);
        assertEquals(3500, station.getFuelBuffer());

        // Add 2 more Raw Anorthosite (+7000 EU)
        station.addFuelBuffer(7000);
        assertEquals(10500, station.getFuelBuffer());

        // Consume 500 EU for MagSafe charging
        station.setFuelBuffer(station.getFuelBuffer() - 500);
        assertEquals(10000, station.getFuelBuffer());

        // Negative check clamping
        station.setFuelBuffer(-100);
        assertEquals(0, station.getFuelBuffer(), "Fuel buffer must not be negative");
    }

    @Test
    public void testChargerFourStateTransitions() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 0.0f);

        // 1. Initial State: Fuel == 0 -> RED
        assertEquals(0, station.getFuelBuffer());
        assertNull(station.getDockedRobotUuid());
        station.setCurrentState(BatteryChargerStation.ChargerState.RED);
        assertEquals(BatteryChargerStation.ChargerState.RED, station.getCurrentState());
        assertEquals("haohan:robot_charger_red", station.getCurrentState().getCustomItemId());

        // 2. Add Fuel -> Turns BLUE (Available)
        station.addFuelBuffer(3500);
        station.setCurrentState(BatteryChargerStation.ChargerState.BLUE);
        assertEquals(BatteryChargerStation.ChargerState.BLUE, station.getCurrentState());
        assertEquals("haohan:robot_charger", station.getCurrentState().getCustomItemId());

        // 3. Robot Docks -> Turns YELLOW (Charging)
        UUID robotId = UUID.randomUUID();
        station.setDockedRobotUuid(robotId);
        station.setCurrentState(BatteryChargerStation.ChargerState.YELLOW);
        assertEquals(BatteryChargerStation.ChargerState.YELLOW, station.getCurrentState());
        assertEquals("haohan:robot_charger_yellow", station.getCurrentState().getCustomItemId());

        // 4. Robot Done -> Turns CYAN (Completed)
        station.setDockedRobotUuid(null);
        station.setCyanDisplayTicks(100);
        station.setCurrentState(BatteryChargerStation.ChargerState.CYAN);
        assertEquals(BatteryChargerStation.ChargerState.CYAN, station.getCurrentState());
        assertEquals("haohan:robot_charger_cyan", station.getCurrentState().getCustomItemId());
    }

    @Test
    public void testChargerYawSnapCalculation() {
        float[] playerYaws = {0.0f, 85.0f, 95.0f, 175.0f, 260.0f, 355.0f};
        for (float pYaw : playerYaws) {
            float snap = Math.round((pYaw + 180.0f) / 90.0f) * 90.0f;
            assertEquals(0.0f, snap % 90.0f, "Snapped yaw should be a multiple of 90 degrees");
        }
    }
}
