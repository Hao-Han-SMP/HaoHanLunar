package vn.haohan.lunar.core.lunar;

import vn.haohan.lunar.core.features.MiningMechanic;

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
}
