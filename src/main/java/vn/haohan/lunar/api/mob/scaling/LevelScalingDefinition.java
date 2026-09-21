package vn.haohan.lunar.api.mob.scaling;

import java.util.Map;
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

    public static LevelScalingDefinition fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return DEFAULT;
        int min = 1;
        int max = 1;
        Object lvlObj = map.get("Level");
        if (lvlObj == null) lvlObj = map.get("level");

        if (lvlObj instanceof Number n) {
            min = n.intValue();
            max = min;
        } else if (lvlObj instanceof String s) {
            if (s.contains("-")) {
                String[] parts = s.split("-", 2);
                try {
                    min = Integer.parseInt(parts[0].trim());
                }
                catch (Exception ignored) {
                }
                try {
                    max = Integer.parseInt(parts[1].trim());
                }
                catch (Exception ignored) {
                }
            } else {
                try {
                    min = Integer.parseInt(s.trim());
                    max = min;
                }
                catch (Exception ignored) {
                }
            }
        }

        double perHp = 0.0;
        double perDmg = 0.0;
        double perArmor = 0.0;
        double perPower = 0.0;

        Object mods = map.get("LevelModifiers");
        if (mods == null) mods = map.get("level_modifiers");
        if (mods == null) mods = map.get("LevelScaling");
        if (mods == null) mods = map.get("level_scaling");

        if (mods instanceof Map<?, ?> modMap) {
            perHp = parseDouble(modMap, "Health", "health", "hp");
            perDmg = parseDouble(modMap, "Damage", "damage", "dmg");
            perArmor = parseDouble(modMap, "Armor", "armor");
            perPower = parseDouble(modMap, "Power", "power");
        }

        return new LevelScalingDefinition(min, max, perHp, perDmg, perArmor, perPower);
    }

    private static double parseDouble(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof String s) {
                try {
                    return Double.parseDouble(s.trim());
                }
                catch (Exception ignored) {
                }
            }
        }
        return 0.0;
    }

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
