package vn.haohan.lunar.item;

import vn.haohan.itemmanager.api.HaoHanItemManager;
import vn.haohan.itemmanager.api.item.ItemDefinition;
import vn.haohan.itemmanager.api.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

public class LunarItems {

    public static void register() {
        var registry = HaoHanItemManager.get().getItemRegistry();
        var behavior = new OxygenTankBehavior();

        // 1. Spacesuit Parts
        registry.register(ItemDefinition.builder("haohan:spacesuit_helmet")
                .material(Material.NETHERITE_HELMET)
                .displayName("Spacesuit Helmet")
                .customModelData(1001)
                .type(ItemType.ARMOR)
                .property("equippable_asset_id", "haohan:spacesuit")
                .build());

        registry.register(ItemDefinition.builder("haohan:spacesuit_chestplate")
                .material(Material.NETHERITE_CHESTPLATE)
                .displayName("Spacesuit Chestplate")
                .customModelData(1002)
                .type(ItemType.ARMOR)
                .property("equippable_asset_id", "haohan:spacesuit")
                .build());

        registry.register(ItemDefinition.builder("haohan:spacesuit_leggings")
                .material(Material.NETHERITE_LEGGINGS)
                .displayName("Spacesuit Leggings")
                .customModelData(1003)
                .type(ItemType.ARMOR)
                .property("equippable_asset_id", "haohan:spacesuit")
                .build());

        registry.register(ItemDefinition.builder("haohan:spacesuit_boots")
                .material(Material.NETHERITE_BOOTS)
                .displayName("Spacesuit Boots")
                .customModelData(1004)
                .type(ItemType.ARMOR)
                .property("equippable_asset_id", "haohan:spacesuit")
                .build());

        // 2. Oxygen Tanks
        registry.register(ItemDefinition.builder("haohan:oxygen_tank_small")
                .material(Material.CARROT_ON_A_STICK)
                .displayName("§bBình Oxy Nhỏ")
                .lore(List.of("§7Dung tích: 1500", "§8Chuột phải để kích hoạt"))
                .customModelData(2001)
                .type(ItemType.SPECIAL)
                .maxStackSize(1)
                .behavior(behavior)
                .properties(Map.of("oxygen_tank", true, "oxygen_tank_tier", 1, "oxygen_tank_capacity", 1500, "max_damage", 1500))
                .build());

        registry.register(ItemDefinition.builder("haohan:oxygen_tank_medium")
                .material(Material.CARROT_ON_A_STICK)
                .displayName("§bBình Oxy Vừa")
                .lore(List.of("§7Dung tích: 3000", "§8Chuột phải để kích hoạt"))
                .customModelData(2002)
                .type(ItemType.SPECIAL)
                .maxStackSize(1)
                .behavior(behavior)
                .properties(Map.of("oxygen_tank", true, "oxygen_tank_tier", 2, "oxygen_tank_capacity", 3000, "max_damage", 3000))
                .build());

        registry.register(ItemDefinition.builder("haohan:oxygen_tank_large")
                .material(Material.CARROT_ON_A_STICK)
                .displayName("§bBình Oxy Lớn")
                .lore(List.of("§7Dung tích: 6800", "§8Chuột phải để kích hoạt"))
                .customModelData(2003)
                .type(ItemType.SPECIAL)
                .maxStackSize(1)
                .behavior(behavior)
                .properties(Map.of("oxygen_tank", true, "oxygen_tank_tier", 3, "oxygen_tank_capacity", 6800, "max_damage", 6800))
                .build());

        // 3. Materials
        registry.register(ItemDefinition.builder("haohan:aero_compound")
                .material(Material.KNOWLEDGE_BOOK)
                .displayName("Aero Compound")
                .customModelData(3001)
                .type(ItemType.MATERIAL)
                .build());

        registry.register(ItemDefinition.builder("haohan:steel_ingot")
                .material(Material.KNOWLEDGE_BOOK)
                .displayName("Steel Ingot")
                .customModelData(3002)
                .type(ItemType.MATERIAL)
                .build());

        // 4. Music Disc
        registry.register(ItemDefinition.builder("haohan:i_really_want_to_stay_at_your_house")
                .material(Material.MUSIC_DISC_13)
                .displayName("§6Đĩa nhạc HaoHanSMP")
                .lore(List.of("§7Lunity - I Really Want to Stay at Your House (Acoustic Cover)"))
                .customModelData(4001)
                .type(ItemType.SPECIAL)
                .properties(Map.of("jukebox_playable", "haohan:i_really_want_to_stay_at_your_house"))
                .build());

        // 5. Block
        registry.register(ItemDefinition.builder("haohan:anorthosite_ore")
                .material(Material.NOTE_BLOCK)
                .displayName("Anorthosite Ore")
                .customModelData(5001)
                .type(ItemType.SPECIAL)
                .property("custom_block_data", "minecraft:note_block[note=24,instrument=pling,powered=true]")
                .build());
    }
}
