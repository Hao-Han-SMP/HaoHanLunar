package vn.haohan.lunar.api.mob.equipment;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles applying equipment to LivingEntities and calculating item drops on death.
 */
public final class EquipmentApplier {

    private EquipmentApplier() {
    }

    /**
     * Applies a mob's equipment definition to an active LivingEntity.
     */
    public static void applyEquipment(
            LivingEntity entity,
            MobEquipmentDefinition equipmentDef,
            ItemProviderRegistry itemRegistry
    ) {
        if (entity == null || equipmentDef == null || equipmentDef.isEmpty()) {
            return;
        }

        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }

        for (SlotEquipmentDefinition slotDef : equipmentDef.allSlots().values()) {
            ItemStack stack = itemRegistry != null
                    ? itemRegistry.resolveItem(slotDef.itemId()).orElse(null)
                    : null;

            if (stack == null) {
                continue;
            }

            float dropChance = (float) slotDef.dropChance();

            switch (slotDef.slot()) {
                case MAIN_HAND -> {
                    equipment.setItemInMainHand(stack);
                    equipment.setItemInMainHandDropChance(dropChance);
                }
                case OFF_HAND -> {
                    equipment.setItemInOffHand(stack);
                    equipment.setItemInOffHandDropChance(dropChance);
                }
                case HEAD -> {
                    equipment.setHelmet(stack);
                    equipment.setHelmetDropChance(dropChance);
                }
                case CHEST -> {
                    equipment.setChestplate(stack);
                    equipment.setChestplateDropChance(dropChance);
                }
                case LEGS -> {
                    equipment.setLeggings(stack);
                    equipment.setLeggingsDropChance(dropChance);
                }
                case FEET -> {
                    equipment.setBoots(stack);
                    equipment.setBootsDropChance(dropChance);
                }
            }
        }
    }

    /**
     * Determines whether an equipment slot item should drop according to its drop chance.
     */
    public static boolean rollDrop(double dropChance) {
        if (dropChance <= 0.0) return false;
        if (dropChance >= 1.0) return true;
        return ThreadLocalRandom.current().nextDouble() < dropChance;
    }

    /**
     * Resolves equipment drops on mob death according to individual slot drop chances.
     */
    public static List<ItemStack> resolveEquipmentDrops(MobEquipmentDefinition equipmentDef, ItemProviderRegistry itemRegistry) {
        if (equipmentDef == null || equipmentDef.isEmpty()) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        for (SlotEquipmentDefinition slotDef : equipmentDef.allSlots().values()) {
            if (rollDrop(slotDef.dropChance())) {
                var opt = itemRegistry != null ? itemRegistry.resolveItem(slotDef.itemId()) : HaoHanItemBridge.get().createItemStack(slotDef.itemId(), 1);
                opt.ifPresent(drops::add);
            }
        }
        return List.copyOf(drops);
    }
}
