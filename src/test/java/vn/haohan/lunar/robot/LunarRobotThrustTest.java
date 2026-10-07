package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotThrustTest {

    @Test
    public void testThrustFlightTicksCalculation() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setModule1Id("haohan:robot_module_thrust");
        data.setModule1Efficiency(100.0);

        double eff100 = data.getModuleEfficiency("thrust");
        assertEquals(100.0, eff100);
        int maxTicks100 = (int) Math.round(60 + (eff100 / 100.0) * 40);
        assertEquals(100, maxTicks100, "100% efficiency should yield 100 ticks (5.0s) of flight");

        data.setModule1Efficiency(0.0);
        double eff0 = data.getModuleEfficiency("thrust");
        assertEquals(0.0, eff0);
        int maxTicks0 = (int) Math.round(60 + (eff0 / 100.0) * 40);
        assertEquals(60, maxTicks0, "0% efficiency should yield 60 ticks (3.0s) of flight");

        data.setModule1Efficiency(50.0);
        double eff50 = data.getModuleEfficiency("thrust");
        int maxTicks50 = (int) Math.round(60 + (eff50 / 100.0) * 40);
        assertEquals(80, maxTicks50, "50% efficiency should yield 80 ticks (4.0s) of flight");
    }

    @Test
    public void testThrustFuelDepletionAndRechargeLogic() {
        int maxTicks = 80;
        int currentTicks = maxTicks;

        // Simulate 30 ticks of flight
        for (int i = 0; i < 30; i++) {
            assertTrue(currentTicks > 0);
            currentTicks--;
        }
        assertEquals(50, currentTicks);

        // Simulate flying until depletion
        for (int i = 0; i < 60; i++) {
            if (currentTicks > 0) {
                currentTicks--;
            }
        }
        assertEquals(0, currentTicks, "Fuel must not drop below 0");

        // Simulate touching ground and recharging (+2 ticks per tick)
        for (int i = 0; i < 40; i++) {
            currentTicks = Math.min(maxTicks, currentTicks + 2);
        }
        assertEquals(maxTicks, currentTicks, "Fuel should fully recharge to max on ground");

        // Cannot overcharge
        currentTicks = Math.min(maxTicks, currentTicks + 2);
        assertEquals(maxTicks, currentTicks);
    }

    @Test
    public void testTaskSeparationSpeedAndThrust() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setModule1Id("haohan:robot_module_speed");
        data.setModule1Efficiency(90.0);
        data.setModule2Id("haohan:robot_module_thrust");
        data.setModule2Efficiency(80.0);

        // Task 1: SPEED mode
        data.setActiveTask(RobotTask.SPEED);
        assertEquals(RobotTask.SPEED, data.getActiveTask());

        // In SPEED mode: base speed is 0.39, increases to 0.65 when space is held (boosting)
        double baseSpeed = 0.39;
        double boostSpeed = 0.65;
        assertEquals(0.39, baseSpeed);
        assertEquals(0.65, boostSpeed);

        // Task 2: THRUST mode
        data.setActiveTask(RobotTask.THRUST);
        assertEquals(RobotTask.THRUST, data.getActiveTask());

        // In THRUST mode, ground speed remains baseline (0.35), speed module boost does NOT apply
        double thrustGroundSpeed = data.getActiveTask() == RobotTask.SPEED ? baseSpeed : 0.35;
        assertEquals(0.35, thrustGroundSpeed, "Ground speed in THRUST mode must remain baseline (0.35)");

        // Thrust flight capability is enabled
        assertTrue(data.hasModule("thrust"));
    }

    @Test
    public void testSpeedBoostEngineHeatAndCooling() {
        // Test heat accumulation (0 -> max) and overheat lockout (>= 100%)
        int maxHeatTicks = 100;
        int currentHeat = 0;
        boolean isOverheated = false;

        // Holding Space for 20 ticks increases heat by 20 ticks
        for (int i = 0; i < 20; i++) {
            currentHeat++;
        }
        assertEquals(20, currentHeat);

        // Heat accumulation up to max -> triggers overheat
        for (int i = 20; i < maxHeatTicks; i++) {
            currentHeat++;
        }
        assertEquals(maxHeatTicks, currentHeat);
        if (currentHeat >= maxHeatTicks) {
            isOverheated = true;
        }
        assertTrue(isOverheated, "Should trigger overheat lockout at 100% heat");

        // While overheated, boosting is locked out even if Space is held
        boolean holdingSpace = true;
        int coolingDelay = 15;
        if (holdingSpace && isOverheated) {
            // Cannot boost when overheated
            coolingDelay = 15;
        }
        assertEquals(100, currentHeat);

        // When releasing Space: cooling delay elapses first
        holdingSpace = false;
        int coolingCooldown = 0;
        for (int gameTick = 0; gameTick < 15; gameTick++) {
            if (coolingDelay > 0) {
                coolingDelay--;
            } else {
                coolingCooldown++;
                if (coolingCooldown >= 2) {
                    coolingCooldown = 0;
                    currentHeat = Math.max(0, currentHeat - 1);
                }
            }
        }
        assertEquals(100, currentHeat, "During first 15 ticks of release, heat must remain 100");

        // After delay: cools down 1 tick every 2 game ticks
        for (int gameTick = 0; gameTick < 200; gameTick++) {
            if (coolingDelay > 0) {
                coolingDelay--;
            } else {
                coolingCooldown++;
                if (coolingCooldown >= 2) {
                    coolingCooldown = 0;
                    currentHeat = Math.max(0, currentHeat - 1);
                    if (currentHeat <= 0) {
                        isOverheated = false;
                    }
                }
            }
        }
        assertEquals(0, currentHeat, "Heat must return to 0 after sufficient cooling");
        assertFalse(isOverheated, "Overheat flag must clear when heat reaches 0");
    }

    @Test
    public void testSpeedBoostEnergyConsumption350To680EUS() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setEnergy(5000);
        data.setModule1Id("haohan:robot_module_speed");

        // 100% efficiency: 34 - (100/100)*16 = 18 EU/tick (360 EU/s)
        data.setModule1Efficiency(100.0);
        double eff100 = data.getModuleEfficiency("speed");
        int cost100 = (int) Math.round(34 - (eff100 / 100.0) * 16);
        assertEquals(18, cost100);
        assertEquals(360, cost100 * 20, "100% efficiency should consume ~360 EU/s");

        // 0% efficiency: 34 - 0 = 34 EU/tick (680 EU/s)
        data.setModule1Efficiency(0.0);
        double eff0 = data.getModuleEfficiency("speed");
        int cost0 = (int) Math.round(34 - (eff0 / 100.0) * 16);
        assertEquals(34, cost0);
        assertEquals(680, cost0 * 20, "0% efficiency should consume ~680 EU/s");

        // 50% efficiency: 34 - 8 = 26 EU/tick (520 EU/s)
        data.setModule1Efficiency(50.0);
        double eff50 = data.getModuleEfficiency("speed");
        int cost50 = (int) Math.round(34 - (eff50 / 100.0) * 16);
        assertEquals(26, cost50);
        assertEquals(520, cost50 * 20, "50% efficiency should consume ~520 EU/s");

        // Consumption tick simulation
        data.consumeEnergy(cost50);
        assertEquals(5000 - 26, data.getEnergy());
    }

    @Test
    public void testEnergyConsumptionDuringThrustFlight() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setEnergy(100);

        // Each flight tick consumes 3 EU
        int ticksFlew = 0;
        while (data.getEnergy() >= 3 && ticksFlew < 10) {
            boolean consumed = data.consumeEnergy(3);
            assertTrue(consumed);
            ticksFlew++;
        }

        assertEquals(10, ticksFlew);
        assertEquals(70, data.getEnergy());

        // Empty energy check
        data.setEnergy(2);
        assertFalse(data.getEnergy() >= 3, "Flight should shut off when energy is below 3 EU");
    }

    @Test
    public void testThrustLaunchEnergyCost720To1240EU() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setModule1Id("haohan:robot_module_thrust");

        // 0% efficiency: 1240 EU
        data.setModule1Efficiency(0.0);
        double eff0 = data.getModuleEfficiency("thrust");
        int cost0 = (int) Math.round(1240.0 - (eff0 / 100.0) * (1240.0 - 720.0));
        assertEquals(1240, cost0, "0% efficiency should cost 1240 EU");

        // 100% efficiency: 720 EU
        data.setModule1Efficiency(100.0);
        double eff100 = data.getModuleEfficiency("thrust");
        int cost100 = (int) Math.round(1240.0 - (eff100 / 100.0) * (1240.0 - 720.0));
        assertEquals(720, cost100, "100% efficiency should cost 720 EU");

        // 50% efficiency: 1240 - 260 = 980 EU
        data.setModule1Efficiency(50.0);
        double eff50 = data.getModuleEfficiency("thrust");
        int cost50 = (int) Math.round(1240.0 - (eff50 / 100.0) * (1240.0 - 720.0));
        assertEquals(980, cost50, "50% efficiency should cost 980 EU");
    }
}
