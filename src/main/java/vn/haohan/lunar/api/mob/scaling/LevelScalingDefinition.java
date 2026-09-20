package vn.haohan.lunar.api.mob.scaling;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Configuration definition for mob level range and per-level stat growth.
 */
public record LevelScalingDefinition(
        int minLevel,
        int maxLevel,
        double perLevelHealth,
        double perLevelDamage,
        double perLevelArmor,
        double perLevelPower
) {
    public LevelScalingDefinition {
        minLevel = Math.max(1, minLevel);
        maxLevel = Math.max(minLevel, maxLevel);
        perLevelHealth = Math.max(0.0, perLevelHealth);
        perLevelDamage = Math.max(0.0, perLevelDamage);
        perLevelArmor = Math.max(0.0, perLevelArmor);
        perLevelPower = Math.max(0.0, perLevelPower);
    }

    public static final LevelScalingDefinition DEFAULT = new LevelScalingDefinition(1, 1, 0, 0, 0, 0);

    /**
     * Rolls a random level within [minLevel, maxLevel].
     */
    public int rollLevel() {
        if (minLevel >= maxLevel) return minLevel;
        return ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
    }

    /**
     * Calculates scaled health: BaseHealth + (level - 1) * perLevelHealth.
     * Prevents overflow and clamps to positive finite numbers.
     */
    public double calculateHealth(double baseHealth, int level) {
        if (level <= 1 || perLevelHealth <= 0.0) return baseHealth;
        double extra = (long) (level - 1) * perLevelHealth;
        return Double.isFinite(extra) ? baseHealth + extra : Double.MAX_VALUE / 2;
    }

    /**
     * Calculates scaled damage: BaseDamage + (level - 1) * perLevelDamage.
     */
    public double calculateDamage(double baseDamage, int level) {
        if (level <= 1 || perLevelDamage <= 0.0) return baseDamage;
        double extra = (long) (level - 1) * perLevelDamage;
        return Double.isFinite(extra) ? baseDamage + extra : Double.MAX_VALUE / 2;
    }

    /**
     * Calculates scaled armor: BaseArmor + (level - 1) * perLevelArmor.
     */
    public double calculateArmor(double baseArmor, int level) {
        if (level <= 1 || perLevelArmor <= 0.0) return baseArmor;
        double extra = (long) (level - 1) * perLevelArmor;
        return Double.isFinite(extra) ? baseArmor + extra : Double.MAX_VALUE / 2;
    }

    /**
     * Calculates scaled power: BasePower + (level - 1) * perLevelPower.
     */
    public double calculatePower(double basePower, int level) {
        if (level <= 1 || perLevelPower <= 0.0) return basePower;
        double extra = (long) (level - 1) * perLevelPower;
        return Double.isFinite(extra) ? basePower + extra : Double.MAX_VALUE / 2;
    }
}
