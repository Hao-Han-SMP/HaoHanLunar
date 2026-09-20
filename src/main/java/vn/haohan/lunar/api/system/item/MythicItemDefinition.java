package vn.haohan.lunar.core.system.item;

import org.bukkit.Material;

import java.util.*;

/**
 * Definition of a custom MythicItem with Paper 1.21 Data Components and active skill triggers.
 */
public record MythicItemDefinition(
        String id,
        Material material,
        String displayName,
        List<String> lore,
        Integer customModelData,
        String rarity,
        boolean unbreakable,
        Map<String, Integer> enchantments,
        Map<String, Map<String, Double>> attributes,
        Set<String> itemFlags,
        List<ItemSkillBinding> skills,
        Map<String, Object> customData
) {
    public MythicItemDefinition {
        Objects.requireNonNull(id, "Item ID must not be null");
        material = material != null ? material : Material.STONE;
        lore = lore != null ? List.copyOf(lore) : List.of();
        enchantments = enchantments != null ? Map.copyOf(enchantments) : Map.of();
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        itemFlags = itemFlags != null ? Set.copyOf(itemFlags) : Set.of();
        skills = skills != null ? List.copyOf(skills) : List.of();
        customData = customData != null ? Map.copyOf(customData) : Map.of();
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private Material material = Material.STONE;
        private String displayName;
        private final List<String> lore = new ArrayList<>();
        private Integer customModelData;
        private String rarity = "COMMON";
        private boolean unbreakable = false;
        private final Map<String, Integer> enchantments = new LinkedHashMap<>();
        private final Map<String, Map<String, Double>> attributes = new LinkedHashMap<>();
        private final Set<String> itemFlags = new LinkedHashSet<>();
        private final List<ItemSkillBinding> skills = new ArrayList<>();
        private final Map<String, Object> customData = new LinkedHashMap<>();

        public Builder(String id) {
            this.id = Objects.requireNonNull(id, "Item ID must not be null");
        }

        public Builder material(Material material) {
            this.material = material != null ? material : Material.STONE;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder lore(List<String> lore) {
            if (lore != null) this.lore.addAll(lore);
            return this;
        }

        public Builder addLore(String line) {
            if (line != null) this.lore.add(line);
            return this;
        }

        public Builder customModelData(Integer cmd) {
            this.customModelData = cmd;
            return this;
        }

        public Builder rarity(String rarity) {
            this.rarity = rarity != null ? rarity : "COMMON";
            return this;
        }

        public Builder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public Builder addEnchantment(String name, int level) {
            if (name != null) this.enchantments.put(name.toUpperCase(Locale.ROOT), level);
            return this;
        }

        public Builder addAttribute(String slot, String attribute, double amount) {
            this.attributes.computeIfAbsent(slot.toUpperCase(Locale.ROOT), s -> new LinkedHashMap<>())
                    .put(attribute.toUpperCase(Locale.ROOT), amount);
            return this;
        }

        public Builder addItemFlag(String flag) {
            if (flag != null) this.itemFlags.add(flag.toUpperCase(Locale.ROOT));
            return this;
        }

        public Builder addSkill(ItemSkillBinding skill) {
            if (skill != null) this.skills.add(skill);
            return this;
        }

        public Builder customData(String key, Object val) {
            if (key != null && val != null) this.customData.put(key, val);
            return this;
        }

        public MythicItemDefinition build() {
            return new MythicItemDefinition(
                    id, material, displayName, lore, customModelData, rarity,
                    unbreakable, enchantments, attributes, itemFlags, skills, customData
            );
        }
    }
}
