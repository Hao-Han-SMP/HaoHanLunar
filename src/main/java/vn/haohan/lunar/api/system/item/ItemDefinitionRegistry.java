package vn.haohan.lunar.core.system.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.lunar.core.integration.bridge.item.HaoHanItemBridge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry and builder for custom MythicItems with Paper 1.21 Data Components and PDC identification.
 */
public final class ItemDefinitionRegistry {

    public static final NamespacedKey KEY_ITEM_ID = new NamespacedKey("haohan", "item_id");
    public static final NamespacedKey KEY_ITEM_RARITY = new NamespacedKey("haohan", "item_rarity");

    private static final Pattern SKILL_ID_PATTERN = Pattern.compile("s=([a-zA-Z0-9_-]+)");
    private static final Pattern SKILL_CD_PATTERN = Pattern.compile("cd=([0-9]+)");
    private static final Pattern TRIGGER_PATTERN = Pattern.compile("~([a-zA-Z0-9]+)");

    private final Map<String, MythicItemDefinition> registry = new ConcurrentHashMap<>();
    private BiFunction<MythicItemDefinition, Integer, ItemStack> testItemBuilder;

    public void register(MythicItemDefinition definition) {
        Objects.requireNonNull(definition, "Definition must not be null");
        registry.put(definition.id().toUpperCase(Locale.ROOT), definition);
    }

    public Optional<MythicItemDefinition> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id.toUpperCase(Locale.ROOT)));
    }

    public boolean contains(String id) {
        if (id == null) return false;
        return registry.containsKey(id.toUpperCase(Locale.ROOT));
    }

    public Collection<MythicItemDefinition> allDefinitions() {
        return Collections.unmodifiableCollection(registry.values());
    }

    public int size() {
        return registry.size();
    }

    public void clear() {
        registry.clear();
    }

    public void setTestItemBuilder(BiFunction<MythicItemDefinition, Integer, ItemStack> builder) {
        this.testItemBuilder = builder;
    }

    /**
     * Loads a MythicItemDefinition from a parsed YAML map.
     */
    public MythicItemDefinition loadFromMap(String id, Map<String, Object> map) {
        Objects.requireNonNull(id, "Item ID must not be null");
        Objects.requireNonNull(map, "YAML map must not be null");

        MythicItemDefinition.Builder builder = MythicItemDefinition.builder(id);

        if (map.get("Material") != null) {
            String matStr = map.get("Material").toString().toUpperCase(Locale.ROOT);
            try {
                builder.material(Material.valueOf(matStr));
            } catch (IllegalArgumentException ignored) {
                builder.material(Material.STONE);
            }
        }

        if (map.get("Display") != null) {
            builder.displayName(map.get("Display").toString());
        }

        if (map.get("Lore") instanceof List<?> loreList) {
            for (Object line : loreList) {
                if (line != null) builder.addLore(line.toString());
            }
        }

        if (map.get("CustomModelData") instanceof Number cmd) {
            builder.customModelData(cmd.intValue());
        }

        if (map.get("Rarity") != null) {
            builder.rarity(map.get("Rarity").toString());
        }

        if (map.get("Unbreakable") != null) {
            builder.unbreakable(Boolean.parseBoolean(map.get("Unbreakable").toString()));
        }

        if (map.get("Enchantments") instanceof List<?> enchList) {
            for (Object enchObj : enchList) {
                if (enchObj == null) continue;
                String[] parts = enchObj.toString().split(" ");
                if (parts.length >= 1) {
                    int lvl = parts.length > 1 ? parseSafeInt(parts[1], 1) : 1;
                    builder.addEnchantment(parts[0], lvl);
                }
            }
        } else if (map.get("Enchantments") instanceof Map<?, ?> enchMap) {
            for (Map.Entry<?, ?> entry : enchMap.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    builder.addEnchantment(entry.getKey().toString(), parseSafeInt(entry.getValue().toString(), 1));
                }
            }
        }

        if (map.get("Attributes") instanceof Map<?, ?> attrSlots) {
            for (Map.Entry<?, ?> slotEntry : attrSlots.entrySet()) {
                if (slotEntry.getValue() instanceof Map<?, ?> attrs) {
                    String slot = slotEntry.getKey().toString();
                    for (Map.Entry<?, ?> attrEntry : attrs.entrySet()) {
                        double amt = parseSafeDouble(attrEntry.getValue().toString(), 0.0);
                        builder.addAttribute(slot, attrEntry.getKey().toString(), amt);
                    }
                }
            }
        }

        if (map.get("ItemFlags") instanceof List<?> flagList) {
            for (Object flag : flagList) {
                if (flag != null) builder.addItemFlag(flag.toString());
            }
        }

        if (map.get("Skills") instanceof List<?> skillList) {
            for (Object skillLine : skillList) {
                if (skillLine != null) {
                    ItemSkillBinding binding = parseSkillLine(skillLine.toString());
                    if (binding != null) {
                        builder.addSkill(binding);
                    }
                }
            }
        }

        MythicItemDefinition def = builder.build();
        register(def);
        return def;
    }

    /**
     * Builds an ItemStack from the given MythicItem definition ID.
     */
    public Optional<ItemStack> buildItemStack(String id, int amount) {
        Optional<MythicItemDefinition> defOpt = get(id);
        if (defOpt.isEmpty()) return Optional.empty();
        MythicItemDefinition def = defOpt.get();

        if (testItemBuilder != null) {
            try {
                ItemStack item = testItemBuilder.apply(def, amount);
                if (item != null) return Optional.of(item);
            } catch (Throwable ignored) {}
        }

        ItemStack item;
        try {
            item = new ItemStack(def.material(), Math.max(1, amount));
        } catch (Throwable e) {
            return Optional.empty();
        }

        applyComponentsToMeta(item, def);
        return Optional.of(item);
    }

    /**
     * Applies metadata, components, and PDC keys onto the item.
     */
    public static void applyComponentsToMeta(ItemStack item, MythicItemDefinition def) {
        if (item == null || def == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Display Name & Lore via MiniMessage
        if (def.displayName() != null && !def.displayName().isBlank()) {
            meta.displayName(MiniMessage.miniMessage().deserialize(def.displayName()));
        }
        if (!def.lore().isEmpty()) {
            List<Component> loreComps = new ArrayList<>();
            for (String line : def.lore()) {
                loreComps.add(MiniMessage.miniMessage().deserialize(line));
            }
            meta.lore(loreComps);
        }

        // CustomModelData
        if (def.customModelData() != null) {
            meta.setCustomModelData(def.customModelData());
        }

        // Unbreakable
        meta.setUnbreakable(def.unbreakable());

        // ItemFlags
        for (String flagStr : def.itemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagStr));
            } catch (IllegalArgumentException ignored) {}
        }

        // PDC Identifiers
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_ITEM_ID, PersistentDataType.STRING, def.id());
        if (def.rarity() != null) {
            pdc.set(KEY_ITEM_RARITY, PersistentDataType.STRING, def.rarity());
        }

        item.setItemMeta(meta);

        // Enchantments
        for (Map.Entry<String, Integer> ench : def.enchantments().entrySet()) {
            try {
                Enchantment e = Enchantment.getByName(ench.getKey());
                if (e != null) {
                    item.addUnsafeEnchantment(e, ench.getValue());
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Extracts the custom item ID from an ItemStack PDC if present.
     */
    public static Optional<String> getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return Optional.empty();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String id = pdc.get(KEY_ITEM_ID, PersistentDataType.STRING);
            return Optional.ofNullable(id);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    /**
     * Checks whether an item has a custom MythicItem identity.
     */
    public static boolean isMythicItem(ItemStack item) {
        return getItemId(item).isPresent();
    }

    public static ItemSkillBinding parseSkillLine(String line) {
        if (line == null || line.isBlank()) return null;

        String skillId = null;
        Matcher mSkill = SKILL_ID_PATTERN.matcher(line);
        if (mSkill.find()) {
            skillId = mSkill.group(1);
        } else {
            int braceStart = line.indexOf('{');
            if (braceStart > 0) {
                skillId = line.substring(0, braceStart).trim();
            } else {
                String[] tokens = line.trim().split("\\s+");
                skillId = tokens[0];
            }
        }

        long cooldownTicks = 0L;
        Matcher mCd = SKILL_CD_PATTERN.matcher(line);
        if (mCd.find()) {
            cooldownTicks = Long.parseLong(mCd.group(1));
        }

        ItemSkillTrigger trigger = ItemSkillTrigger.ON_USE;
        Matcher mTrigger = TRIGGER_PATTERN.matcher(line);
        if (mTrigger.find()) {
            trigger = ItemSkillTrigger.fromString(mTrigger.group(1));
        }

        if (skillId == null || skillId.isBlank()) return null;
        return new ItemSkillBinding(skillId, trigger, cooldownTicks);
    }

    private static int parseSafeInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static double parseSafeDouble(String s, double fallback) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
    }
}
