package vn.haohan.lunar.api.system.loot;

import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.loot.luck.LuckModifier;
import vn.haohan.lunar.api.system.loot.pity.PityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Immutable configuration for a loot drop table.
 * Supports independent roll mode (each entry rolls on its own chance)
 * and weighted roll mode (selects 1-of-N based on relative weight).
 * Integrated with DropOptions, LuckModifier, and PityManager.
 */
public record DropTableDefinition(String id,
                                  RollMode mode,
                                  List<DropEntry> entries,
                                  int rolls,
                                  DropOptions options) {

    public enum RollMode {
        INDEPENDENT,
        WEIGHTED
    }

    public DropTableDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Drop table ID must not be blank");
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        mode = mode != null ? mode : RollMode.INDEPENDENT;
        entries = entries != null ? List.copyOf(entries) : List.of();
        rolls = Math.max(1, rolls);
        options = options != null ? options : DropOptions.DEFAULT;
    }

    public DropTableDefinition(String id, RollMode mode, List<DropEntry> entries, int rolls) {
        this(id, mode, entries, rolls, DropOptions.DEFAULT);
    }

    public DropTableDefinition(String id, List<DropEntry> entries) {
        this(id, RollMode.INDEPENDENT, entries, 1, DropOptions.DEFAULT);
    }

    /**
     * Executes the loot roll and returns the generated ItemStacks.
     */
    public List<ItemStack> roll(DropMetadata metadata,
                                Random random,
                                HaoHanItemBridge itemBridge,
                                ConditionRegistry conditionRegistry,
                                PityManager pityManager) {
        Objects.requireNonNull(random, "Random generator must not be null");
        Objects.requireNonNull(itemBridge, "Item bridge must not be null");

        List<ItemStack> items = new ArrayList<>();
        UUID playerUuid = metadata != null && metadata.killer() != null ? metadata.killer().getUniqueId() : null;

        double luckBonus = metadata != null ? metadata.amountModifier() : 1.0;
        double luck = (metadata != null && metadata.killer() != null)
                ? LuckModifier.calculateLuck(metadata.killer())
                : 0.0;

        if (mode == RollMode.INDEPENDENT) {
            for (int r = 0; r < rolls; r++) {
                for (DropEntry entry : entries) {
                    boolean guaranteedByPity = false;
                    if (pityManager != null && playerUuid != null) {
                        // If item has a pity condition or check
                        if (pityManager.isPityTriggered(playerUuid, entry.itemId(), 50)) {
                            guaranteedByPity = true;
                        }
                    }

                    boolean should = guaranteedByPity || entry.shouldDrop(metadata, random, conditionRegistry);
                    if (should) {
                        int amount = (int) Math.round(entry.rollAmount(metadata, random) * luckBonus);
                        amount = Math.max(1, amount);
                        itemBridge.createItemStack(entry.itemId(), amount).ifPresent(items::add);
                        if (pityManager != null && playerUuid != null) {
                            pityManager.recordSuccess(playerUuid, entry.itemId());
                        }
                    } else if (pityManager != null && playerUuid != null) {
                        pityManager.recordMiss(playerUuid, entry.itemId());
                    }
                }
            }
        } else if (mode == RollMode.WEIGHTED) {
            for (int r = 0; r < rolls; r++) {
                List<DropEntry> eligible = new ArrayList<>();
                double totalWeight = 0.0;
                for (DropEntry entry : entries) {
                    if (meetsConditionsOnly(entry, metadata, conditionRegistry)) {
                        eligible.add(entry);
                        double w = (luck != 0.0) ? LuckModifier.scaleWeight(entry.chance(), luck) : entry.chance();
                        totalWeight += w;
                    }
                }

                if (eligible.isEmpty() || totalWeight <= 0.0) continue;

                double target = random.nextDouble() * totalWeight;
                double current = 0.0;
                DropEntry selected = eligible.getFirst();
                for (DropEntry entry : eligible) {
                    double w = (luck != 0.0) ? LuckModifier.scaleWeight(entry.chance(), luck) : entry.chance();
                    current += w;
                    if (target <= current) {
                        selected = entry;
                        break;
                    }
                }

                int amount = (int) Math.round(selected.rollAmount(metadata, random) * luckBonus);
                amount = Math.max(1, amount);
                itemBridge.createItemStack(selected.itemId(), amount).ifPresent(items::add);
            }
        }

        return List.copyOf(items);
    }

    public List<ItemStack> roll(DropMetadata metadata,
                                Random random,
                                HaoHanItemBridge itemBridge,
                                ConditionRegistry conditionRegistry) {
        return roll(metadata, random, itemBridge, conditionRegistry, null);
    }

    private static boolean meetsConditionsOnly(DropEntry entry,
                                               DropMetadata metadata,
                                               ConditionRegistry conditionRegistry) {
        if (entry.conditions().isEmpty() || conditionRegistry == null || metadata == null) {
            return true;
        }
        var caster = metadata.dropper() != null ? metadata.dropper().entity() : null;
        var killer = metadata.killer();
        if (caster != null) {
            var context = new ConditionContext(
                    caster,
                    killer,
                    null,
                    new CooldownRegistry(),
                    java.util.Map.of(),
                    metadata.tick()
            );
            return conditionRegistry.and(context, entry.conditions()).matched();
        }
        return true;
    }
}
