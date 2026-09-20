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
