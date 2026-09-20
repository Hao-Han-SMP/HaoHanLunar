package vn.haohan.lunar.api.loot;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.combat.skill.condition.ConditionResult;
import vn.haohan.lunar.api.loot.luck.LuckModifier;
import vn.haohan.lunar.api.combat.skill.CooldownRegistry;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Single item entry within a drop table, including drop rate, amount range and conditions.
 */
public record DropEntry(String itemId,
                        double chance,
                        int minAmount,
                        int maxAmount,
                        List<ConditionRegistry.ConditionCall> conditions) {

    public DropEntry {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Drop item ID must not be blank");
        }
        itemId = itemId.trim();
        chance = Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 0.0;
        if (minAmount < 1) minAmount = 1;
        if (maxAmount < minAmount) maxAmount = minAmount;
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
    }

    public DropEntry(String itemId, double chance, int minAmount, int maxAmount) {
        this(itemId, chance, minAmount, maxAmount, List.of());
    }

    public DropEntry(String itemId, double chance, int amount) {
        this(itemId, chance, amount, amount, List.of());
    }

    /**
     * Determines whether this entry successfully drops based on RNG, luck modifier, and conditions.
     */
    public boolean shouldDrop(DropMetadata metadata, Random random, ConditionRegistry conditionRegistry) {
        if (chance <= 0.0) return false;
        double effectiveChance = chance;
        if (metadata != null && metadata.killer() != null) {
            double luck = LuckModifier.calculateLuck(metadata.killer());
            if (luck != 0.0) {
                effectiveChance = LuckModifier.scaleChance(chance, luck);
            }
        }

        if (effectiveChance < 1.0) {
            double roll = random.nextDouble();
            if (roll > effectiveChance) return false;
        }

        if (!conditions.isEmpty() && conditionRegistry != null && metadata != null) {
            LivingEntity caster = metadata.dropper() != null ? metadata.dropper().entity() : null;
            LivingEntity killer = metadata.killer();
            if (caster != null) {
                ConditionContext context = new ConditionContext(
                        caster,
                        killer,
                        null,
                        new CooldownRegistry(),
                        Map.of(),
                        metadata.tick()
                );
                ConditionResult result = conditionRegistry.and(context, conditions);
                if (!result.valid() || !result.matched()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Rolls the quantity of items to drop.
     */
    public int rollAmount(DropMetadata metadata, Random random) {
        int base;
        if (minAmount == maxAmount) {
            base = minAmount;
        } else {
            base = minAmount + random.nextInt(maxAmount - minAmount + 1);
        }

        double mod = metadata != null ? metadata.amountModifier() : 1.0;
        int result = (int) Math.round(base * mod);
        return Math.max(1, result);
    }
}
