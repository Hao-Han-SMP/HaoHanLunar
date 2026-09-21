package vn.haohan.lunar.api.mob.equipment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Complete 6-slot loadout definition for a custom mob.
 */
public final class MobEquipmentDefinition {

    private final Map<EquipmentSlot, SlotEquipmentDefinition> slots;

    public MobEquipmentDefinition(Map<EquipmentSlot, SlotEquipmentDefinition> slots) {
        if (slots == null || slots.isEmpty()) {
            this.slots = Collections.emptyMap();
        } else {
            this.slots = Collections.unmodifiableMap(new EnumMap<>(slots));
        }
    }

    public static MobEquipmentDefinition empty() {
        return new MobEquipmentDefinition(Collections.emptyMap());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<SlotEquipmentDefinition> get(EquipmentSlot slot) {
        return Optional.ofNullable(slots.get(slot));
    }

    public Map<EquipmentSlot, SlotEquipmentDefinition> allSlots() {
        return slots;
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public static MobEquipmentDefinition fromMap(Object raw) {
        if (raw == null) return empty();
        if (raw instanceof MobEquipmentDefinition med) return med;
        if (!(raw instanceof Map<?, ?> map)) return empty();

        Builder builder = builder();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            EquipmentSlot slot;
            try {
                slot = EquipmentSlot.fromString(entry.getKey().toString());
            }
            catch (IllegalArgumentException ignored) {
                continue;
            }

            Object val = entry.getValue();
            if (val instanceof String str) {
                int lastColon = str.lastIndexOf(':');
                String itemId = str.trim();
                double dropChance = 0.0;
                if (lastColon > 0) {
                    String possibleChance = str.substring(lastColon + 1).trim();
                    try {
                        dropChance = Double.parseDouble(possibleChance);
                        itemId = str.substring(0, lastColon).trim();
                    } catch (NumberFormatException ignored) {
                        // Not a trailing drop chance; entire string is the itemId (e.g. "minecraft:iron_sword")
                    }
                }
                builder.set(slot, itemId, dropChance);
            } else if (val instanceof Map<?, ?> slotMap) {
                String itemId = String.valueOf(find(slotMap, "item", "id"));
                double dropChance = 0.0;
                Object dc = find(slotMap, "dropChance", "drop-chance");
                if (dc instanceof Number n) dropChance = n.doubleValue();
                boolean unbreakable = Boolean.parseBoolean(String.valueOf(find(slotMap, "unbreakable")));
                builder.set(slot, itemId, dropChance, unbreakable);
            }
        }
        return builder.build();
    }

    private static Object find(Map<?, ?> map, String... keys) {
        if (map == null) return null;
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }

    public static final class Builder {
        private final Map<EquipmentSlot, SlotEquipmentDefinition> map = new EnumMap<>(EquipmentSlot.class);

        public Builder set(EquipmentSlot slot, String itemId, double dropChance, boolean unbreakable) {
            map.put(slot, new SlotEquipmentDefinition(slot, itemId, dropChance, unbreakable));
            return this;
        }

        public Builder set(EquipmentSlot slot, String itemId, double dropChance) {
            return set(slot, itemId, dropChance, false);
        }

        public Builder set(EquipmentSlot slot, String itemId) {
            return set(slot, itemId, 0.0, false);
        }

        public Builder mainHand(String itemId, double dropChance) {
            return set(EquipmentSlot.MAIN_HAND, itemId, dropChance);
        }

        public Builder offHand(String itemId, double dropChance) {
            return set(EquipmentSlot.OFF_HAND, itemId, dropChance);
        }

        public Builder helmet(String itemId, double dropChance) {
            return set(EquipmentSlot.HEAD, itemId, dropChance);
        }

        public Builder chestplate(String itemId, double dropChance) {
            return set(EquipmentSlot.CHEST, itemId, dropChance);
        }

        public Builder leggings(String itemId, double dropChance) {
            return set(EquipmentSlot.LEGS, itemId, dropChance);
        }

        public Builder boots(String itemId, double dropChance) {
            return set(EquipmentSlot.FEET, itemId, dropChance);
        }

        public MobEquipmentDefinition build() {
            return new MobEquipmentDefinition(map);
        }
    }
}
