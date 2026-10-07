package vn.haohan.lunar.robot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotCombatHiddenStatTest {

    @Test
    public void testEmergencyMultiplierInactiveWhenOwnerHealthAboveOrEqual6Point5Hearts() {
        // 6.5 hearts = 13.0 HP. At or above this threshold, multiplier must strictly be 1.0
        assertEquals(1.0, LunarRobotEntity.calculateEmergencyCombatMultiplier(20.0), 0.001, "Full health owner (20 HP) must have 1.0x multiplier");
        assertEquals(1.0, LunarRobotEntity.calculateEmergencyCombatMultiplier(15.0), 0.001, "15 HP (> 6.5 hearts) must have 1.0x multiplier");
        assertEquals(1.0, LunarRobotEntity.calculateEmergencyCombatMultiplier(13.0), 0.001, "Exact 13.0 HP (6.5 hearts) must have 1.0x multiplier");
    }

    @Test
    public void testEmergencyMultiplierScalesBelow6Point5Hearts() {
        // Below 13.0 HP, emergency ratio = (13.0 - hp) / 13.0
        // Multiplier = 1.0 + ratio * 1.5

        // Mid emergency: 6.5 HP (3.25 hearts) -> ratio = 0.5 -> multiplier = 1.0 + 0.5 * 1.5 = 1.75
        double multMid = LunarRobotEntity.calculateEmergencyCombatMultiplier(6.5);
        assertEquals(1.75, multMid, 0.001, "At 6.5 HP, multiplier should be 1.75x");

        // High emergency: 1.0 HP -> ratio = 12 / 13 = 0.92307 -> multiplier = 1.0 + 0.92307 * 1.5 = 2.3846
        double multLow = LunarRobotEntity.calculateEmergencyCombatMultiplier(1.0);
        assertEquals(1.0 + (12.0 / 13.0) * 1.5, multLow, 0.001);

        // Extreme emergency: 0.0 HP -> ratio = 1.0 -> multiplier = 2.5
        double multZero = LunarRobotEntity.calculateEmergencyCombatMultiplier(0.0);
        assertEquals(2.5, multZero, 0.001, "At 0 HP, multiplier reaches max 2.5x");

        // Negative HP protection clamped to max
        double multNegative = LunarRobotEntity.calculateEmergencyCombatMultiplier(-5.0);
        assertEquals(2.5, multNegative, 0.001, "Negative health must clamp ratio to 1.0 (2.5x)");
    }

    @Test
    public void testDamageAndEnergyConsumptionScaling() {
        double baseDamage = 14.0;
        int baseEnergy = 25;

        // 1. Normal state (Owner at 20 HP)
        double multNormal = LunarRobotEntity.calculateEmergencyCombatMultiplier(20.0);
        double finalDamageNormal = baseDamage * multNormal;
        int energyNormal = Math.max(1, (int) Math.round(baseEnergy / multNormal));
        assertEquals(14.0, finalDamageNormal, 0.001);
        assertEquals(25, energyNormal);

        // 2. Critical state (Owner at 6.5 HP -> 1.75x)
        double multCrit = LunarRobotEntity.calculateEmergencyCombatMultiplier(6.5);
        double finalDamageCrit = baseDamage * multCrit;
        int energyCrit = Math.max(1, (int) Math.round(baseEnergy / multCrit));
        assertEquals(24.5, finalDamageCrit, 0.001, "Damage increases proportionally (14.0 * 1.75 = 24.5)");
        assertEquals(14, energyCrit, "Energy consumption decreases inversely (round(25 / 1.75) = 14)");

        // 3. Extreme state (Owner at 0 HP -> 2.5x)
        double multMax = LunarRobotEntity.calculateEmergencyCombatMultiplier(0.0);
        double finalDamageMax = baseDamage * multMax;
        int energyMax = Math.max(1, (int) Math.round(baseEnergy / multMax));
        assertEquals(35.0, finalDamageMax, 0.001, "Max damage: 14.0 * 2.5 = 35.0");
        assertEquals(10, energyMax, "Min energy consumption: round(25 / 2.5) = 10");

        // Verify: As hidden stat increases, damage increases and energy decreases
        assertTrue(finalDamageMax > finalDamageCrit && finalDamageCrit > finalDamageNormal);
        assertTrue(energyMax < energyCrit && energyCrit < energyNormal);
    }
}
