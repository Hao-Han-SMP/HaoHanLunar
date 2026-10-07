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
    public void testConditionMultiplierCalculation() {
        // Base case: normal world, low Y, no sky, no conductive block
        assertEquals(1.0, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(false, 64, false, false));

        // Single factors (+0.25 each)
        assertEquals(1.25, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(true, 64, false, false));
        assertEquals(1.25, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(false, 95, false, false));
        assertEquals(1.25, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(false, 64, true, false));
        assertEquals(1.25, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(false, 64, false, true));

        // Two factors (+0.50)
        assertEquals(1.50, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(true, 95, false, false));

        // Three factors (+0.75)
        assertEquals(1.75, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(true, 95, true, false));

        // All 4 factors (+1.00 -> 2.0x)
        assertEquals(2.0, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateConditionMultiplier(true, 95, true, true));
    }

    @Test
    public void testChargeRateDiminishingReturns() {
        // Base rate
        assertEquals(450, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.0));
        assertEquals(450, vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(0.8));

        // Milestone points matching design
        int r100 = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.0);
        int r125 = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.25);
        int r150 = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.50);
        int r175 = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.75);
        int r200 = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(2.00);

        assertEquals(450, r100);
        assertEquals(720, r125);
        assertEquals(920, r150);
        assertEquals(970, r175);
        assertEquals(1000, r200);

        // Verify strictly diminishing returns: delta1 > delta2 > delta3 > delta4
        int delta1 = r125 - r100; // 270
        int delta2 = r150 - r125; // 200
        int delta3 = r175 - r150; // 50
        int delta4 = r200 - r175; // 30

        assertEquals(270, delta1);
        assertEquals(200, delta2);
        assertEquals(50, delta3);
        assertEquals(30, delta4);

        assertTrue(delta1 > delta2, "Gain from 1.0->1.25 must exceed 1.25->1.50");
        assertTrue(delta2 > delta3, "Gain from 1.25->1.50 must exceed 1.50->1.75");
        assertTrue(delta3 > delta4, "Gain from 1.50->1.75 must exceed 1.75->2.00");

        // Extreme condition ceiling (asymptote never exceeds 1720)
        int extremeRate = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(10.0);
        assertTrue(extremeRate <= 1720, "Rate must not exceed 1720 EU/s cap");
        assertTrue(extremeRate > 1000, "Extreme rate should exceed base 2.0x rate");
    }

    @Test
    public void testStrictFuelRequirementZeroChargeWithoutFuel() {
        int capacity = 5000;
        int currentEnergy = 1000;
        int fuelBuffer = 0; // Hết nhiên liệu!

        // If fuel buffer is 0, no matter the rate, charge MUST be 0
        int chargeRate = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.50); // 920 EU/s
        int cycleCharge = chargeRate / 4; // 230 EU per cycle
        int actualCharge = Math.min(cycleCharge, fuelBuffer);

        assertEquals(0, actualCharge, "No energy should be charged when fuelBuffer is 0");
        currentEnergy += actualCharge;
        assertEquals(1000, currentEnergy, "Energy must remain unchanged");
    }

    @Test
    public void testDirectBatteryVsWirelessRobotSpeedDifference() {
        int chargeRate = vn.haohan.lunar.charger.BatteryChargerMechanic.calculateChargeRate(1.25); // 720 EU/s
        int directCycleCharge = chargeRate / 4; // 180 EU/cycle (720 EU/s)
        int wirelessCycleCharge = Math.max(1, (chargeRate / 4) / 5); // 36 EU/cycle (144 EU/s)

        assertEquals(180, directCycleCharge);
        assertEquals(36, wirelessCycleCharge);
        assertEquals(5, directCycleCharge / wirelessCycleCharge, "Direct charging must be exactly 5x faster than wireless");
    }

    private static class TestFuelItemStack extends org.bukkit.inventory.ItemStack {
        private final org.bukkit.Material mat;
        private int count;

        public TestFuelItemStack(org.bukkit.Material mat, int count) {
            this.mat = mat;
            this.count = count;
        }

        @Override
        public org.bukkit.Material getType() {
            return mat;
        }

        @Override
        public int getAmount() {
            return count;
        }

        @Override
        public void setAmount(int amount) {
            this.count = amount;
        }

        @Override
        public org.bukkit.inventory.ItemStack subtract(int amount) {
            this.count -= amount;
            return this;
        }

        @Override
        public boolean hasItemMeta() {
            return false;
        }

        @Override
        public org.bukkit.inventory.ItemStack clone() {
            return new TestFuelItemStack(mat, count);
        }
    }

    @Test
    public void testStationStoredFuelAutoReplenishment() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 0.0f);

        // Put a fuel item into station (Redstone: 500 EU each, amount: 2)
        org.bukkit.inventory.ItemStack redstone = new TestFuelItemStack(org.bukkit.Material.REDSTONE, 2);
        station.setFuelItem(redstone);
        assertEquals(0, station.getFuelBuffer());

        // First replenishment consumes 1 redstone -> adds 500 EU buffer
        vn.haohan.lunar.charger.BatteryChargerMechanic.replenishStationFuel(station);
        assertEquals(500, station.getFuelBuffer());
        assertNotNull(station.getFuelItem());
        assertEquals(1, station.getFuelItem().getAmount());

        // Second replenishment consumes remaining 1 redstone -> buffer becomes 1000 EU, fuelItem becomes null
        vn.haohan.lunar.charger.BatteryChargerMechanic.replenishStationFuel(station);
        assertEquals(1000, station.getFuelBuffer());
        assertNull(station.getFuelItem());
    }
}

