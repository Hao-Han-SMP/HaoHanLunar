package vn.haohan.lunar.api.system.loot;

import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.mob.scaling.MobLevelApplier;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.loot.luck.LuckModifier;
import vn.haohan.lunar.api.system.loot.pity.PityManager;

import java.util.*;

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

    private static Object find(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v;
        }
        return null;
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

    @SuppressWarnings("unchecked")
    public static DropTableDefinition fromMap(String id, Map<String, Object> map, ConditionRegistry conditionRegistry) {
        if (map == null) return new DropTableDefinition(id, List.of());

        RollMode mode = RollMode.INDEPENDENT;
        Object rawMode = map.getOrDefault("RollMode", map.get("mode"));
        if (rawMode != null) {
            try {
                mode = RollMode.valueOf(rawMode.toString().trim().toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored) {
            }
        }

        int rolls = 1;
        Object rawRolls = map.getOrDefault("Rolls", map.get("rolls"));
        if (rawRolls instanceof Number n) rolls = Math.max(1, n.intValue());

        DropOptions.Builder optsBuilder = DropOptions.builder();
        Object rawOptions = find(map, "Options", "options");
        if (rawOptions instanceof Map<?, ?> optMap) {
            boolean dpp = Boolean.parseBoolean(String.valueOf(find(optMap, "DropsPerPlayer", "drops_per_player")));
            double reqDmg = 0.0;
            Object rd = find(optMap, "DropsPerPlayerRequiredDamagePercent", "required_damage_percent");
            if (rd instanceof Number n) reqDmg = n.doubleValue();
            optsBuilder.dropsPerPlayer(dpp, reqDmg);

            optsBuilder.lootsplosion(Boolean.parseBoolean(String.valueOf(find(optMap, "DropsDoLootsplosion", "lootsplosion"))));
            optsBuilder.glowByDefault(Boolean.parseBoolean(String.valueOf(find(optMap, "DropsGlowByDefault", "glow"))));
            optsBuilder.beamByDefault(Boolean.parseBoolean(String.valueOf(find(optMap, "DropsHaveBeamByDefault", "beam"))));

            Object bl = find(optMap, "BonusLuck", "bonus_luck");
            if (bl instanceof Number n) optsBuilder.bonusLuck(n.doubleValue());

            Object blvl = find(optMap, "BonusLevel", "bonus_level");
            if (blvl instanceof Number n) optsBuilder.bonusLevel(n.doubleValue());
        }

        List<DropEntry> entries = new ArrayList<>();
        Object rawDrops = map.get("Drops");
        if (rawDrops == null) rawDrops = map.get("drops");
        if (rawDrops == null) rawDrops = map.get("Items");
        if (rawDrops == null) rawDrops = map.get("items");

        if (rawDrops instanceof Collection<?> col) {
            for (Object item : col) {
                DropEntry entry = parseEntry(item, conditionRegistry);
                if (entry != null) entries.add(entry);
            }
        }

        return new DropTableDefinition(id, mode, entries, rolls, optsBuilder.build());
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

    @SuppressWarnings("unchecked")
    public static DropEntry parseEntry(Object raw, ConditionRegistry conditionRegistry) {
        if (raw == null) return null;
        if (raw instanceof DropEntry de) return de;

        if (raw instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) return null;
            String[] tokens = trimmed.split("\\s+");
            String itemId = tokens[0];
            int min = 1;
            int max = 1;
            double chance = 1.0;

            if (tokens.length > 1) {
                String amountStr = tokens[1];
                if (amountStr.contains("-")) {
                    String[] parts = amountStr.split("-", 2);
                    try {
                        min = Integer.parseInt(parts[0]);
                    }
                    catch (NumberFormatException ignored) {
                    }
                    try {
                        max = Integer.parseInt(parts[1]);
                    }
                    catch (NumberFormatException ignored) {
                    }
                } else {
                    try {
                        min = Integer.parseInt(amountStr);
                        max = min;
                    }
                    catch (NumberFormatException ignored) {
                    }
                }
            }
            if (tokens.length > 2) {
                try {
                    chance = Double.parseDouble(tokens[2]);
                }
                catch (NumberFormatException ignored) {
                }
            }
            return new DropEntry(itemId, chance, min, max);
        } else if (raw instanceof Map<?, ?> map) {
            String itemId = String.valueOf(find(map, "item", "id"));
            double chance = 1.0;
            Object c = find(map, "chance", "weight");
            if (c instanceof Number n) chance = n.doubleValue();

            int min = 1;
            int max = 1;
            Object amt = find(map, "amount", "count");
            if (amt instanceof Number n) {
                min = n.intValue();
                max = min;
            } else if (amt instanceof String str && str.contains("-")) {
                String[] parts = str.split("-", 2);
                try {
                    min = Integer.parseInt(parts[0]);
                }
                catch (NumberFormatException ignored) {
                }
                try {
                    max = Integer.parseInt(parts[1]);
                }
                catch (NumberFormatException ignored) {
                }
            }
            return new DropEntry(itemId, chance, min, max);
        }
        return null;
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

        // Base amountModifier is already applied inside DropEntry.rollAmount().
        // luckBonus only scales extra bonus luck and bonus mob levels.
        double luckBonus = 1.0;
        double luck = (metadata != null && metadata.killer() != null)
                ? LuckModifier.calculateLuck(metadata.killer())
                : 0.0;

        if (options.bonusLuckMultiplier() > 0.0 && luck > 0.0) {
            luckBonus *= (1.0 + (luck * options.bonusLuckMultiplier()));
        }

        if (options.bonusLevelMultiplier() > 0.0 && metadata != null && metadata.dropper() != null && metadata.dropper().entity() != null) {
            try {
                int mobLevel = MobLevelApplier.getLevel(metadata.dropper().entity());
                if (mobLevel > 1) {
                    luckBonus *= (1.0 + ((mobLevel - 1) * options.bonusLevelMultiplier()));
                }
            }
            catch (Throwable ignored) {
            }
        }

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
}
