package vn.haohan.lunar.core.mob.scaling;

import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.scaling.LevelScalingDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobLevelScalingTest {

    @Test
    void linearScalingCalculations() {
        LevelScalingDefinition scaling = new LevelScalingDefinition(
                1, 20,
                10.0, // perLevelHealth
                2.5,  // perLevelDamage
                1.0,  // perLevelArmor
                0.5   // perLevelPower
        );

        // Level 1: base stats unchanged
        assertEquals(100.0, scaling.calculateHealth(100.0, 1));
        assertEquals(10.0, scaling.calculateDamage(10.0, 1));
        assertEquals(5.0, scaling.calculateArmor(5.0, 1));
        assertEquals(1.0, scaling.calculatePower(1.0, 1));

        // Level 5: base + 4 * perLevel
        // Health: 100 + 4 * 10 = 140
        assertEquals(140.0, scaling.calculateHealth(100.0, 5));
        // Damage: 10 + 4 * 2.5 = 20
        assertEquals(20.0, scaling.calculateDamage(10.0, 5));
        // Armor: 5 + 4 * 1.0 = 9
        assertEquals(9.0, scaling.calculateArmor(5.0, 5));
        // Power: 1.0 + 4 * 0.5 = 3.0
        assertEquals(3.0, scaling.calculatePower(1.0, 5));
    }

    @Test
    void rollLevelWithinConfiguredBounds() {
        LevelScalingDefinition scaling = new LevelScalingDefinition(5, 10, 1, 1, 1, 1);
        for (int i = 0; i < 50; i++) {
            int lvl = scaling.rollLevel();
            assertTrue(lvl >= 5 && lvl <= 10, "Rolled level " + lvl + " should be in [5, 10]");
        }
    }

    @Test
    void largeLevelOverflowProtection() {
        LevelScalingDefinition scaling = new LevelScalingDefinition(1, 1000000, 50.0, 10.0, 5.0, 1.0);
        double health = scaling.calculateHealth(100.0, 1_000_000);
        assertTrue(Double.isFinite(health));
        assertTrue(health > 100.0);
    }
}
