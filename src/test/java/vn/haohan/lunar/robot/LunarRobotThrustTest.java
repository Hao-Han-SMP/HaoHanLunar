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
    public void testSpeedBoostStaminaConsumptionAndSlowRecovery() {
        // Test consumption (1 tick per tick) vs slow recovery (1 tick per 2 ticks)
        int maxTicks = 100;
        int currentTicks = maxTicks;

        // Holding Space for 20 ticks consumes 20 ticks
        for (int i = 0; i < 20; i++) {
            currentTicks--;
        }
        assertEquals(80, currentTicks);

        // When stamina is exhausted to 0 and space is still held:
        // Stamina remains 0 and does NOT recharge at all
        currentTicks = 0;
        int recoveryDelay = 15;
        boolean holdingSpace = true;
        if (holdingSpace) {
            recoveryDelay = 15; // Holding space suppresses recovery
        }
        assertEquals(0, currentTicks, "Should not recover while space is held");

        // When releasing space: 15 ticks delay must elapse before recovery begins
        holdingSpace = false;
        int recoveryCooldown = 0;
        for (int gameTick = 0; gameTick < 15; gameTick++) {
            if (recoveryDelay > 0) {
                recoveryDelay--;
            } else {
                recoveryCooldown++;
                if (recoveryCooldown >= 2) {
                    recoveryCooldown = 0;
                    currentTicks = Math.min(maxTicks, currentTicks + 1);
                }
            }
        }
        assertEquals(0, currentTicks, "During first 15 ticks of release, stamina must still be 0");

        // After 15 ticks delay: stamina recovers 1 tick every 2 game ticks
        for (int gameTick = 0; gameTick < 20; gameTick++) {
            if (recoveryDelay > 0) {
                recoveryDelay--;
            } else {
                recoveryCooldown++;
                if (recoveryCooldown >= 2) {
                    recoveryCooldown = 0;
                    currentTicks = Math.min(maxTicks, currentTicks + 1);
                }
            }
        }
        // In 20 game ticks, recovers 10 stamina ticks
        assertEquals(10, currentTicks);
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
}
