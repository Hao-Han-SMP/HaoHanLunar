package vn.haohan.lunar.lunar;

import vn.haohan.lunar.features.MiningMechanic;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningMechanicTest {
    @Test
    void onlyNetheritePickaxeIsAllowedForLunarOre() {
        assertTrue(MiningMechanic.isAllowedLunarPickaxe(Material.NETHERITE_PICKAXE));
        for (Material material : new Material[]{
                Material.AIR, Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
                Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE,
                Material.IRON_AXE, Material.IRON_SHOVEL, Material.IRON_SWORD}) {
            assertFalse(MiningMechanic.isAllowedLunarPickaxe(material), material.name());
        }
    }

    @Test
    void testIsAllowedLunarPickaxeWithToolTiers() {
        // Air / Null
        assertFalse(MiningMechanic.isAllowedLunarPickaxe((org.bukkit.inventory.ItemStack) null));

        // Vanilla pickaxes without ItemCore registry mock
        org.bukkit.inventory.ItemStack netheritePick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(netheritePick.getType()).thenReturn(Material.NETHERITE_PICKAXE);
        assertTrue(MiningMechanic.isAllowedLunarPickaxe(netheritePick));

        org.bukkit.inventory.ItemStack diamondPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(diamondPick.getType()).thenReturn(Material.DIAMOND_PICKAXE);
        assertFalse(MiningMechanic.isAllowedLunarPickaxe(diamondPick));

        org.bukkit.inventory.ItemStack ironPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(ironPick.getType()).thenReturn(Material.IRON_PICKAXE);
        assertFalse(MiningMechanic.isAllowedLunarPickaxe(ironPick));
    }
}
