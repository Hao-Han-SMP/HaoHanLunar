package vn.haohan.lunar.api.system.loot.luck;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Calculates per-player luck modifier and scales drop chances/weights.
 */
public final class LuckModifier {

    private static final double LUCK_WEIGHT_FACTOR = 0.05; // 5% increase per luck point

    private LuckModifier() {}

    /**
     * Calculates total luck for an entity based on base attribute and active potion effects.
     */
    public static double calculateLuck(LivingEntity entity) {
        if (entity == null) return 0.0;

        double luck = 0.0;
        try {
            Attribute luckAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("luck"));
            if (luckAttr == null) {
                luckAttr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.luck"));
            }
            if (luckAttr != null) {
                AttributeInstance attr = entity.getAttribute(luckAttr);
                if (attr != null) {
                    luck += attr.getValue();
                }
            }
        } catch (Throwable ignored) {}

        try {
            PotionEffect luckPot = entity.getPotionEffect(PotionEffectType.LUCK);
            if (luckPot != null) {
                luck += (luckPot.getAmplifier() + 1);
            }
            PotionEffect badLuck = entity.getPotionEffect(PotionEffectType.UNLUCK);
            if (badLuck != null) {
                luck -= (badLuck.getAmplifier() + 1);
            }
        } catch (Throwable ignored) {}

        return luck;
    }

    /**
     * Scales an entry's drop chance using luck modifier.
     */
    public static double scaleChance(double baseChance, double luck) {
        if (baseChance <= 0.0) return 0.0;
        if (baseChance >= 1.0) return 1.0;
        double multiplier = Math.max(0.1, 1.0 + (luck * LUCK_WEIGHT_FACTOR));
        return Math.min(1.0, baseChance * multiplier);
    }

    /**
     * Scales an entry's weighted roll weight using luck modifier.
     */
    public static double scaleWeight(double baseWeight, double luck) {
        if (baseWeight <= 0.0) return 0.0;
        double multiplier = Math.max(0.1, 1.0 + (luck * LUCK_WEIGHT_FACTOR));
        return baseWeight * multiplier;
    }
}
