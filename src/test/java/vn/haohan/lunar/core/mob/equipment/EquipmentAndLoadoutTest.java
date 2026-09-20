package vn.haohan.lunar.core.mob.equipment;

import vn.haohan.lunar.api.mob.equipment.*;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAndLoadoutTest {

    private static class DummyItemStack extends ItemStack {
        private final Material material;
        private final int amount;

        public DummyItemStack(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return material;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public ItemStack clone() {
            return new DummyItemStack(material, amount);
        }
    }

    @BeforeEach
    void setUp() {
        HaoHanItemBridge.get().setItemStackFactory(DummyItemStack::new);
    }

    @Test
    void equipmentDefinitionSlots() {
        MobEquipmentDefinition def = MobEquipmentDefinition.builder()
                .mainHand("DIAMOND_SWORD", 0.5)
                .offHand("SHIELD", 0.1)
                .helmet("NETHERITE_HELMET", 0.05)
                .chestplate("NETHERITE_CHESTPLATE", 0.0)
                .leggings("NETHERITE_LEGGINGS", 0.0)
                .boots("NETHERITE_BOOTS", 0.2)
                .build();

        assertEquals(6, def.allSlots().size());
        assertTrue(def.get(EquipmentSlot.MAIN_HAND).isPresent());
        assertEquals("DIAMOND_SWORD", def.get(EquipmentSlot.MAIN_HAND).get().itemId());
        assertEquals(0.5, def.get(EquipmentSlot.MAIN_HAND).get().dropChance());
        assertEquals(0.2, def.get(EquipmentSlot.FEET).get().dropChance());
    }

    @Test
    void itemProviderRegistryVanillaAndHookResolvers() {
        ItemProviderRegistry registry = new ItemProviderRegistry();

        // Vanilla material
        Optional<ItemStack> diamond = registry.resolveItem("DIAMOND_SWORD");
        assertTrue(diamond.isPresent());
        assertEquals(Material.DIAMOND_SWORD, diamond.get().getType());

        // Custom hook
        registry.registerHook("custom", id -> {
            if ("god_blade".equalsIgnoreCase(id)) {
                return new DummyItemStack(Material.NETHERITE_SWORD, 1);
            }
            return null;
        });

        Optional<ItemStack> custom = registry.resolveItem("custom:god_blade");
        assertTrue(custom.isPresent());
        assertEquals(Material.NETHERITE_SWORD, custom.get().getType());

        // Missing item fallback
        Optional<ItemStack> missing = registry.resolveItem("custom:unknown_item");
        assertTrue(missing.isEmpty());
    }

    @Test
    void equipmentApplierAppliesToEntityEquipment() {
        ItemProviderRegistry itemRegistry = new ItemProviderRegistry();
        MobEquipmentDefinition def = MobEquipmentDefinition.builder()
                .mainHand("DIAMOND_SWORD", 0.4)
                .helmet("IRON_HELMET", 0.1)
                .build();

        Map<String, Object> state = new HashMap<>();
        EntityEquipment mockEquipment = (EntityEquipment) Proxy.newProxyInstance(
                EntityEquipment.class.getClassLoader(),
                new Class<?>[]{EntityEquipment.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setItemInMainHand")) {
                        state.put("mainHand", args[0]);
                        return null;
                    }
                    if (method.getName().equals("setItemInMainHandDropChance")) {
                        state.put("mainHandDropChance", args[0]);
                        return null;
                    }
                    if (method.getName().equals("setHelmet")) {
                        state.put("helmet", args[0]);
                        return null;
                    }
                    if (method.getName().equals("setHelmetDropChance")) {
                        state.put("helmetDropChance", args[0]);
                        return null;
                    }
                    return null;
                }
        );

        LivingEntity mockEntity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getEquipment")) return mockEquipment;
                    return null;
                }
        );

        EquipmentApplier.applyEquipment(mockEntity, def, itemRegistry);

        assertNotNull(state.get("mainHand"));
        assertEquals(0.4f, (float) state.get("mainHandDropChance"), 0.001f);
        assertNotNull(state.get("helmet"));
        assertEquals(0.1f, (float) state.get("helmetDropChance"), 0.001f);
    }

    @Test
    void dropChanceRollBoundaryChecks() {
        assertFalse(EquipmentApplier.rollDrop(0.0));
        assertTrue(EquipmentApplier.rollDrop(1.0));
    }
}
