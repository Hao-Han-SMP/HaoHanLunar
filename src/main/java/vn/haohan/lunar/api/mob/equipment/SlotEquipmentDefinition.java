package vn.haohan.lunar.api.mob.equipment;

import java.util.Objects;

/**
 * Immutable definition for one equipment slot.
 */
public record SlotEquipmentDefinition(
        EquipmentSlot slot,
        String itemId,
        double dropChance,
        boolean unbreakable
) {
    public SlotEquipmentDefinition {
        Objects.requireNonNull(slot, "EquipmentSlot must not be null");
        Objects.requireNonNull(itemId, "ItemId must not be null");
        itemId = itemId.trim();
        if (itemId.isEmpty()) {
            throw new IllegalArgumentException("ItemId must not be blank");
        }
        dropChance = Math.max(0.0, Math.min(1.0, dropChance));
    }

    public SlotEquipmentDefinition(EquipmentSlot slot, String itemId, double dropChance) {
        this(slot, itemId, dropChance, false);
    }

    public SlotEquipmentDefinition(EquipmentSlot slot, String itemId) {
        this(slot, itemId, 0.0, false);
    }
}
