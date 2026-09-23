package vn.haohan.lunar.api.system.loot;

import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.mob.scaling.MobLevelApplier;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.loot.luck.LuckModifier;
import vn.haohan.lunar.api.system.loot.pity.PityManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Immutable configuration for a loot drop table.
 * Supports independent roll mode (each entry rolls on its own chance)
 * and weighted roll mode (selects 1-of-N based on relative weight).
 * Integrated with DropOptions, LuckModifier, and PityManager.
 * Supports ITEM drops, EXP drops, and nested DROP_TABLE delegation.
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
            List<ConditionRegistry.ConditionCall> conditions = new ArrayList<>();

            if (tokens.length > 1) {
                String amountStr = tokens[1];
                if (amountStr.contains("-")) {
                    String[] parts = amountStr.split("-", 2);
                    try {
                        min = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException ignored) {}
                    try {
                        max = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {}
                } else {
                    try {
                        min = Integer.parseInt(amountStr);
                        max = min;
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (tokens.length > 2) {
                try {
                    chance = Double.parseDouble(tokens[2]);
                } catch (NumberFormatException ignored) {}
            }

            for (int i = 3; i < tokens.length; i++) {
                String tok = tokens[i].trim();
                if (tok.startsWith("?")) {
                    tok = tok.substring(1);
                }
                if (!tok.isBlank()) {
                    int bStart = tok.indexOf('{');
                    int bEnd = tok.lastIndexOf('}');
                    if (bStart > 0 && bEnd > bStart) {
                        String cId = tok.substring(0, bStart).trim();
                        String cContent = tok.substring(bStart + 1, bEnd).trim();
                        conditions.add(new ConditionRegistry.ConditionCall(cId, parseInlineParams(cContent)));
                    } else {
                        conditions.add(new ConditionRegistry.ConditionCall(tok.trim(), Map.of()));
                    }
                }
            }

            return new DropEntry(itemId, chance, min, max, conditions);
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
                } catch (NumberFormatException ignored) {}
                try {
                    max = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            }

            List<ConditionRegistry.ConditionCall> conditions = new ArrayList<>();
            Object condObj = find(map, "conditions", "condition");
            if (condObj instanceof Collection<?> col) {
                for (Object item : col) {
                    if (item instanceof String cStr) {
                        String tok = cStr.trim();
                        if (tok.startsWith("?")) tok = tok.substring(1);
                        int bStart = tok.indexOf('{');
                        int bEnd = tok.lastIndexOf('}');
                        if (bStart > 0 && bEnd > bStart) {
                            String cId = tok.substring(0, bStart).trim();
                            conditions.add(new ConditionRegistry.ConditionCall(cId, parseInlineParams(tok.substring(bStart + 1, bEnd).trim())));
                        } else {
                            conditions.add(new ConditionRegistry.ConditionCall(tok.trim(), Map.of()));
                        }
                    } else if (item instanceof Map<?, ?> cMap) {
                        String cId = String.valueOf(find(cMap, "id", "type"));
                        Map<String, Object> params = new LinkedHashMap<>();
                        cMap.forEach((k, v) -> {
                            if (!"id".equals(k) && !"type".equals(k)) {
                                params.put(String.valueOf(k), v);
                            }
                        });
                        conditions.add(new ConditionRegistry.ConditionCall(cId, params));
                    }
                }
            }

            return new DropEntry(itemId, chance, min, max, conditions);
        }
        return null;
    }

    private static Map<String, Object> parseInlineParams(String content) {
        if (content == null || content.isBlank()) return Map.of();
        Map<String, Object> map = new LinkedHashMap<>();
        for (String pair : content.split("[;,]")) {
            String p = pair.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq > 0) {
                String k = p.substring(0, eq).trim();
                String v = p.substring(eq + 1).trim();
                try {
                    if (v.contains(".")) {
                        map.put(k, Double.parseDouble(v));
                    } else {
                        map.put(k, Integer.parseInt(v));
                    }
                } catch (NumberFormatException e) {
                    map.put(k, v);
                }
            } else {
                map.put(p, true);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Executes the loot roll and returns the generated ItemStacks.
     */
    public List<ItemStack> roll(DropMetadata metadata,
                                Random random,
                                HaoHanItemBridge itemBridge,
                                ConditionRegistry conditionRegistry,
                                PityManager pityManager) {
        return rollComposite(metadata, random, itemBridge, conditionRegistry, pityManager, null, 0).items();
    }

    /**
     * Executes the full composite roll returning item drops, experience orbs, and recursively rolled sub-tables.
     */
    public DropRollResult rollComposite(DropMetadata metadata,
                                       Random random,
                                       HaoHanItemBridge itemBridge,
                                       ConditionRegistry conditionRegistry,
                                       PityManager pityManager,
                                       Function<String, Optional<DropTableDefinition>> tableResolver,
                                       int currentDepth) {
        Objects.requireNonNull(random, "Random generator must not be null");
        Objects.requireNonNull(itemBridge, "Item bridge must not be null");

        if (currentDepth > 5) {
            return DropRollResult.EMPTY;
        }

        List<ItemStack> items = new ArrayList<>();
        AtomicInteger totalExp = new AtomicInteger(0);
        List<String> subTables = new ArrayList<>();

        UUID playerUuid = metadata != null && metadata.killer() != null ? metadata.killer().getUniqueId() : null;

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
                        if (pityManager.isPityTriggered(playerUuid, entry.itemId(), 50)) {
                            guaranteedByPity = true;
                        }
                    }

                    boolean should = guaranteedByPity || entry.shouldDrop(metadata, random, conditionRegistry);
                    if (should) {
                        int amount = (int) Math.round(entry.rollAmount(metadata, random) * luckBonus);
                        amount = Math.max(1, amount);
                        applyEntryOutcome(entry.itemId(), amount, items, totalExp, subTables, metadata, random, itemBridge, conditionRegistry, pityManager, tableResolver, currentDepth);
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
                applyEntryOutcome(selected.itemId(), amount, items, totalExp, subTables, metadata, random, itemBridge, conditionRegistry, pityManager, tableResolver, currentDepth);
            }
        }

        return new DropRollResult(items, totalExp.get(), subTables);
    }

    private void applyEntryOutcome(String itemId,
                                   int amount,
                                   List<ItemStack> items,
                                   AtomicInteger totalExp,
                                   List<String> subTables,
                                   DropMetadata metadata,
                                   Random random,
                                   HaoHanItemBridge itemBridge,
                                   ConditionRegistry conditionRegistry,
                                   PityManager pityManager,
                                   Function<String, Optional<DropTableDefinition>> tableResolver,
                                   int currentDepth) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (lower.equals("exp") || lower.equals("experience") || lower.startsWith("exp:") || lower.startsWith("experience:")) {
            totalExp.addAndGet(amount);
            return;
        }

        if (lower.startsWith("table:") || lower.startsWith("droptable:")) {
            String targetTableId = lower.contains(":") ? lower.substring(lower.indexOf(':') + 1).trim() : lower;
            subTables.add(targetTableId);
            if (tableResolver != null) {
                for (int i = 0; i < amount; i++) {
                    tableResolver.apply(targetTableId).ifPresent(subTable -> {
                        DropRollResult subResult = subTable.rollComposite(metadata, random, itemBridge, conditionRegistry, pityManager, tableResolver, currentDepth + 1);
                        items.addAll(subResult.items());
                        totalExp.addAndGet(subResult.experience());
                    });
                }
            }
            return;
        }

        itemBridge.createItemStack(itemId, amount).ifPresent(items::add);
    }
}
