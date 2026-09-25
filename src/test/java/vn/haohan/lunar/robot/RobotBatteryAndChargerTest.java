package vn.haohan.lunar.robot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.charger.BatteryChargerStation;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RobotBatteryAndChargerTest {

    @Test
    public void testBatteryCapacityMapping() {
        assertEquals(5000, RobotBatteryUtil.getCapacity(RobotBatteryUtil.BATTERY_SMALL));
        assertEquals(15000, RobotBatteryUtil.getCapacity(RobotBatteryUtil.BATTERY_MEDIUM));
        assertEquals(30000, RobotBatteryUtil.getCapacity(RobotBatteryUtil.BATTERY_LARGE));
        assertEquals(0, RobotBatteryUtil.getCapacity("unknown"));
        assertEquals(0, RobotBatteryUtil.getCapacity(null));
    }

    @Test
    public void testRobotEnergySyncWithBatteryInput() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());

        // Initially no battery
        data.setBatteryType("none");
        assertEquals("none", data.getBatteryType());
        assertEquals(0, data.getMaxEnergy());
        assertEquals(0, data.getEnergy());

        // Install small battery with 3,500 EU remaining
        data.setBatteryType(RobotBatteryUtil.BATTERY_SMALL);
        data.setEnergy(3500);
        assertEquals(5000, data.getMaxEnergy());
        assertEquals(3500, data.getEnergy());

        // Robot consumes 500 EU
        assertTrue(data.consumeEnergy(500));
        assertEquals(3000, data.getEnergy());

        // Try consuming more than available
        assertFalse(data.consumeEnergy(5000));
        assertEquals(3000, data.getEnergy(), "Energy should not change if cost exceeds available EU");

        // Swap to Large Battery fully charged (30,000 EU)
        data.setBatteryType(RobotBatteryUtil.BATTERY_LARGE);
        data.setEnergy(30000);
        assertEquals(30000, data.getMaxEnergy());
        assertEquals(30000, data.getEnergy());

        // Remove battery (Slot empty)
        data.setBatteryType("none");
        data.setEnergy(0);
        assertEquals(0, data.getMaxEnergy());
        assertEquals(0, data.getEnergy());
    }

    @Test
    public void testChargerStationDataAndFuelBuffer() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 180.0f);

        assertEquals(0, station.getFuelBuffer());
        assertEquals(180.0f, station.getYaw());
        assertEquals(owner, station.getOwnerUuid());
        assertEquals(display, station.getDisplayEntityUuid());

        // Add Redstone (+500 EU)
        station.addFuelBuffer(500);
        assertEquals(500, station.getFuelBuffer());

        // Add Kreep Dust (+2000 EU)
        station.addFuelBuffer(2000);
        assertEquals(2500, station.getFuelBuffer());

        // Consume 100 EU for charging
        station.setFuelBuffer(station.getFuelBuffer() - 100);
        assertEquals(2400, station.getFuelBuffer());

        // Negative check clamping
        station.setFuelBuffer(-50);
        assertEquals(0, station.getFuelBuffer(), "Fuel buffer must not be negative");
    }

    @Test
    public void testChargerYawSnapCalculation() {
        // Test player yaw snapping to 90 degree increments facing player (yaw + 180 snap)
        float[] playerYaws = {0.0f, 85.0f, 95.0f, 175.0f, 260.0f, 355.0f};

        for (float pYaw : playerYaws) {
            float snap = Math.round((pYaw + 180.0f) / 90.0f) * 90.0f;
            assertEquals(0.0f, snap % 90.0f, "Snapped yaw should be a multiple of 90 degrees");
        }
    }

    @Test
    public void testSimulatedBatteryChargingCycle() {
        int capacity = 5000;
        int currentEnergy = 4700;
        int fuelBuffer = 500;

        // Tick 1: Needs 300 EU to full. Base 25 + bonus 50 = 75 EU
        int needed = capacity - currentEnergy;
        int chargeAmount = 25;
        if (fuelBuffer > 0) {
            int bonus = Math.min(50, fuelBuffer);
            chargeAmount += bonus;
            fuelBuffer -= bonus;
        }
        currentEnergy = Math.min(capacity, currentEnergy + chargeAmount);
        assertEquals(4775, currentEnergy);
        assertEquals(450, fuelBuffer);

        // Run until full
        while (currentEnergy < capacity) {
            int tickCharge = 25;
            if (fuelBuffer > 0) {
                int b = Math.min(50, fuelBuffer);
                tickCharge += b;
                fuelBuffer -= b;
            }
            currentEnergy = Math.min(capacity, currentEnergy + tickCharge);
        }

        assertEquals(capacity, currentEnergy, "Energy must cap exactly at battery capacity");
        assertTrue(fuelBuffer > 0, "Leftover fuel should remain in buffer");
    }

    @Test
    public void testRealTimeUiChargingRatesAndProgress() {
        int capacity = 5000;
        int currentEnergy = 0;
        int fuelBuffer = 5000;

        // At 5 ticks (0.25s per cycle):
        // Base rate: 15 EU (60 EU/s)
        // Fuel bonus: 35 EU (140 EU/s)
        // Total: 50 EU per 0.25s = 200 EU/s
        int ticks = 0;
        while (currentEnergy < capacity && ticks < 200) {
            ticks++;
            int charge = 15;
            if (fuelBuffer > 0) {
                int bonus = Math.min(35, fuelBuffer);
                charge += bonus;
                fuelBuffer -= bonus;
            }
            currentEnergy = Math.min(capacity, currentEnergy + charge);

            int pct = (int) Math.round(((double) currentEnergy / capacity) * 100.0);
            assertTrue(pct >= 0 && pct <= 100);
        }

        assertEquals(5000, currentEnergy);
        assertEquals(100, (int) Math.round(((double) currentEnergy / capacity) * 100.0));
        assertEquals(100, ticks, "At 50 EU/tick, 5000 EU should take exactly 100 cycles");
        assertEquals(5000 - (100 * 35), fuelBuffer, "Buffer should have consumed exactly 100 * 35 = 3500 EU bonus");
    }
}

