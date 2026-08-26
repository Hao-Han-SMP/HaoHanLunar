package vn.haohan.lunar.item;

import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemType;
import vn.haohan.itemcore.api.recipe.Ingredient;
import vn.haohan.itemcore.api.recipe.ItemResult;
import vn.haohan.itemcore.api.recipe.RecipeDefinition;
import vn.haohan.itemcore.api.recipe.RecipeType;
import vn.haohan.itemcore.api.recipe.ShapedRecipeDefinition;
import vn.haohan.lunar.HaoHanLunarPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

public class LunarItems {

        public static void register() {
                var registry = HaoHanItemCore.get().getItemRegistry();
                var behavior = new OxygenTankBehavior();

                // 1. Spacesuit Parts
                registerSpacesuitPart(registry, "helmet", Material.NETHERITE_HELMET, "Spacesuit Helmet", 1001);
                registerSpacesuitPart(registry, "chestplate", Material.NETHERITE_CHESTPLATE, "Spacesuit Chestplate",
                                1002);
                registerSpacesuitPart(registry, "leggings", Material.NETHERITE_LEGGINGS, "Spacesuit Leggings", 1003);
                registerSpacesuitPart(registry, "boots", Material.NETHERITE_BOOTS, "Spacesuit Boots", 1004);

                // 2. Oxygen Tanks
                registerOxygenTank(registry, behavior, "small", "§bBình Oxy Nhỏ", 1, 1500, 100, 2001);
                registerOxygenTank(registry, behavior, "medium", "§bBình Oxy Vừa", 2, 3000, 200, 2002);
                registerOxygenTank(registry, behavior, "large", "§bBình Oxy Lớn", 3, 6800, 320, 2003);

                // 3. Materials
                registry.register(ItemDefinition.builder("haohan:aero_compound")
                                .material(Material.PAPER)
                                .displayName("Aero Compound")
                                .customModelData(3001)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:steel_ingot")
                                .material(Material.PAPER)
                                .displayName("Steel Ingot")
                                .customModelData(3002)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:raw_anorthosite")
                                .material(Material.PAPER)
                                .displayName("Raw Anorthosite")
                                .customModelData(3003)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:raw_ilmenite")
                                .material(Material.PAPER)
                                .displayName("Raw Ilmenite")
                                .customModelData(3004)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:raw_pyroxene")
                                .material(Material.PAPER)
                                .displayName("Raw Pyroxene")
                                .customModelData(3005)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:pyroxene_debris")
                                .material(Material.PAPER)
                                .displayName("Pyroxene Debris")
                                .customModelData(3006)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:kreep_dust")
                                .material(Material.PAPER)
                                .displayName("KREEP Dust")
                                .customModelData(3007)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(64)
                                .build());

                // 4. Music Disc
                registry.register(ItemDefinition.builder("haohan:i_really_want_to_stay_at_your_house")
                                .material(Material.MUSIC_DISC_13)
                                .displayName("§6Đĩa nhạc HaoHanSMP")
                                .customModelData(4001)
                                .type(ItemType.SPECIAL)
                                .maxStackSize(1)
                                .properties(Map.of("jukebox_playable", "haohan:i_really_want_to_stay_at_your_house"))
                                .build());

                // 5. Tools & Utility
                registry.register(ItemDefinition.builder("haohan:telescope")
                                .material(Material.SPYGLASS)
                                .displayName("Kính viễn vọng")
                                .lore(List.of("§7Công cụ hỗ trợ tìm kiếm và phân tích các vật thể"))
                                .type(ItemType.TOOL)
                                .maxStackSize(1)
                                .properties(Map.of("max_damage", 60))
                                .build());

                registry.register(ItemDefinition.builder("haohan:telescope_broken")
                                .material(Material.PAPER)
                                .displayName("Kính viễn vọng")
                                .lore(List.of(
                                                "§7Công cụ hỗ trợ tìm kiếm và phân tích các vật thể",
                                                "§cỐng kính đã không còn tác dụng, vui lòng thay ống mới."))
                                .model("haohan:telescope")
                                .type(ItemType.TOOL)
                                .maxStackSize(1)
                                .properties(Map.of("max_damage", 60))
                                .build());

                registry.register(ItemDefinition.builder("haohan:telescope_lens")
                                .material(Material.PAPER)
                                .displayName("Ống kính")
                                .lore(List.of("§7Cụm ống kính quang học tinh vi dùng cho kính viễn vọng."))
                                .model("haohan:telescope_lens")
                                .customModelData(3008)
                                .type(ItemType.MATERIAL)
                                .maxStackSize(8)
                                .build());

                // 6. Block
                registry.register(ItemDefinition.builder("haohan:anorthosite_ore")
                                .material(Material.NOTE_BLOCK)
                                .displayName("Anorthosite Ore")
                                .customModelData(5001)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of(
                                                "custom_block_data",
                                                "minecraft:note_block[note=24,instrument=pling,powered=true]",
                                                "custom_block_drop", "haohan:raw_anorthosite",
                                                "hide_additional_tooltip", true))
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:ilmenite_ore")
                                .material(Material.NOTE_BLOCK)
                                .displayName("Ilmenite Ore")
                                .customModelData(5002)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of(
                                                "custom_block_data",
                                                "minecraft:note_block[note=23,instrument=pling,powered=true]",
                                                "custom_block_drop", "haohan:raw_ilmenite",
                                                "hide_additional_tooltip", true))
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:pyroxene_ore")
                                .material(Material.NOTE_BLOCK)
                                .displayName("Pyroxene Ore")
                                .customModelData(5003)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of(
                                                "custom_block_data",
                                                "minecraft:note_block[note=22,instrument=pling,powered=true]",
                                                "custom_block_drop", "haohan:raw_pyroxene",
                                                "hide_additional_tooltip", true))
                                .maxStackSize(64)
                                .build());

                registry.register(ItemDefinition.builder("haohan:kreep_basalt")
                                .material(Material.NOTE_BLOCK)
                                .displayName("KREEP Basalt")
                                .customModelData(5004)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of(
                                                "custom_block_data",
                                                "minecraft:note_block[note=21,instrument=pling,powered=true]",
                                                "custom_block_drop", "haohan:kreep_dust",
                                                "hide_additional_tooltip", true))
                                .maxStackSize(64)
                                .build());

                // 6. Smithing Recipes for Spacesuit Armor Upgrade
                registerSpacesuitRecipes();

                // 7. Shapeless Recipes for Telescope Repair
                registerTelescopeRecipes();
        }

        private static void registerSpacesuitPart(vn.haohan.itemcore.api.item.ItemRegistry registry,
                        String part, Material material, String displayName, int customModelData) {
                registry.register(ItemDefinition.builder("haohan:spacesuit_" + part)
                                .material(material)
                                .displayName(displayName)
                                .customModelData(customModelData)
                                .type(ItemType.ARMOR)
                                .maxStackSize(1)
                                .property("equippable_asset_id", "haohan:spacesuit")
                                .build());
        }

        private static void registerOxygenTank(vn.haohan.itemcore.api.item.ItemRegistry registry,
                        OxygenTankBehavior behavior, String size, String displayName,
                        int tier, int capacity, int chargeTicks, int customModelData) {
                registry.register(ItemDefinition.builder("haohan:oxygen_tank_" + size)
                                .material(Material.CARROT_ON_A_STICK)
                                .displayName(displayName)
                                .lore(List.of("§7Dung tích: " + capacity, "§8Chuột phải để kích hoạt"))
                                .customModelData(customModelData)
                                .type(ItemType.SPECIAL)
                                .maxStackSize(1)
                                .behavior(behavior)
                                .properties(Map.of(
                                                "oxygen_tank", true,
                                                "oxygen_tank_tier", tier,
                                                "oxygen_tank_capacity", capacity,
                                                "oxygen_tank_charge_ticks", chargeTicks,
                                                "max_damage", capacity))
                                .build());
        }

        private static void registerSpacesuitRecipes() {
                var recipeRegistry = HaoHanItemCore.get().getRecipeRegistry();
                Plugin plugin = HaoHanLunarPlugin.getInstance();
                var itemFactory = HaoHanItemCore.get().getItemFactory();
                var itemRegistry = HaoHanItemCore.get().getItemRegistry();

                Map<String, Material> armorUpgrades = Map.of(
                                "helmet", Material.NETHERITE_HELMET,
                                "chestplate", Material.NETHERITE_CHESTPLATE,
                                "leggings", Material.NETHERITE_LEGGINGS,
                                "boots", Material.NETHERITE_BOOTS);

                for (Map.Entry<String, Material> entry : armorUpgrades.entrySet()) {
                        String part = entry.getKey();
                        Material netheriteMaterial = entry.getValue();

                        RecipeDefinition upgradeRecipe = new RecipeDefinition(
                                        "haohan:spacesuit_" + part + "_smithing",
                                        RecipeType.SMITHING,
                                        List.of(
                                                        new Ingredient.MaterialIngredient(
                                                                        Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                                                                        1),
                                                        new Ingredient.MaterialIngredient(netheriteMaterial, 1),
                                                        new Ingredient.ItemIngredient("haohan:aero_compound", 1)),
                                        new ItemResult("haohan:spacesuit_" + part, 1));

                        if (!recipeRegistry.exists(upgradeRecipe.getId())) {
                                recipeRegistry.register(upgradeRecipe);
                        }
                        registerBukkitSmithingRecipe(plugin, upgradeRecipe, itemFactory, itemRegistry);
                }
        }

        private static void registerBukkitSmithingRecipe(Plugin plugin, RecipeDefinition recipe,
                        vn.haohan.itemcore.api.item.ItemFactory itemFactory,
                        vn.haohan.itemcore.api.item.ItemRegistry itemRegistry) {
                NamespacedKey key = new NamespacedKey(plugin, recipe.getKey());
                Bukkit.removeRecipe(key);

                ItemStack resultStack = itemFactory.create(recipe.getResult().item(), recipe.getResult().amount());

                List<Ingredient> ingredients = recipe.getIngredients();
                RecipeChoice template = toRecipeChoice(ingredients.get(0), itemFactory, itemRegistry);
                RecipeChoice base = toRecipeChoice(ingredients.get(1), itemFactory, itemRegistry);
                RecipeChoice addition = toRecipeChoice(ingredients.get(2), itemFactory, itemRegistry);

                SmithingTransformRecipe bukkitRecipe = new SmithingTransformRecipe(key, resultStack, template, base,
                                addition);
                Bukkit.addRecipe(bukkitRecipe);
        }

        private static RecipeChoice toRecipeChoice(Ingredient ingredient,
                        vn.haohan.itemcore.api.item.ItemFactory itemFactory,
                        vn.haohan.itemcore.api.item.ItemRegistry itemRegistry) {
                if (ingredient instanceof Ingredient.ItemIngredient item) {
                        if (itemRegistry.exists(item.id())) {
                                var def = itemRegistry.get(item.id());
                                if (def != null) {
                                        return new RecipeChoice.MaterialChoice(def.getMaterial());
                                }
                        }
                        if (item.id().startsWith("minecraft:")) {
                                String matName = item.id().substring("minecraft:".length()).toUpperCase();
                                Material mat = Material.matchMaterial(matName);
                                if (mat != null)
                                        return new RecipeChoice.MaterialChoice(mat);
                        }
                } else if (ingredient instanceof Ingredient.MaterialIngredient mat) {
                        return new RecipeChoice.MaterialChoice(mat.material());
                }
                throw new IllegalArgumentException("Unsupported ingredient: " + ingredient);
        }

        private static void registerTelescopeRecipes() {
                var recipeRegistry = HaoHanItemCore.get().getRecipeRegistry();

                // 1. Công thức chế tạo Ống kính kính viễn vọng (Shaped Recipe)
                RecipeDefinition lensRecipe = new ShapedRecipeDefinition(
                                "haohan:telescope_lens",
                                List.of(
                                                "QAG",
                                                " SA",
                                                "S Q"),
                                Map.of(
                                                'Q', new Ingredient.MaterialIngredient(Material.QUARTZ, 1),
                                                'A', new Ingredient.MaterialIngredient(Material.AMETHYST_SHARD, 1),
                                                'G', new Ingredient.MaterialIngredient(Material.TINTED_GLASS, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1)),
                                new ItemResult("haohan:telescope_lens", 1));

                if (!recipeRegistry.exists(lensRecipe.getId())) {
                        recipeRegistry.register(lensRecipe);
                }

                // 2. Công thức chế tạo Kính viễn vọng (Shaped Recipe)
                RecipeDefinition telescopeRecipe = new ShapedRecipeDefinition(
                                "haohan:telescope",
                                List.of(
                                                "QAL",
                                                "ATA",
                                                "NAQ"),
                                Map.of(
                                                'Q', new Ingredient.MaterialIngredient(Material.QUARTZ, 1),
                                                'A', new Ingredient.MaterialIngredient(Material.AMETHYST_SHARD, 1),
                                                'L', new Ingredient.ItemIngredient("haohan:telescope_lens", 1),
                                                'T', new Ingredient.MaterialIngredient(Material.SPYGLASS, 1),
                                                'N', new Ingredient.MaterialIngredient(Material.NETHERITE_INGOT, 1)),
                                new ItemResult("haohan:telescope", 1));

                if (!recipeRegistry.exists(telescopeRecipe.getId())) {
                        recipeRegistry.register(telescopeRecipe);
                }

                // 3. Sửa kính viễn vọng đã hỏng hoàn toàn (haohan:telescope_broken + haohan:telescope_lens -> haohan:telescope)
                RecipeDefinition repairBrokenRecipe = new RecipeDefinition(
                                "haohan:telescope_repair_broken",
                                RecipeType.SHAPELESS,
                                List.of(
                                                new Ingredient.ItemIngredient("haohan:telescope_broken", 1),
                                                new Ingredient.ItemIngredient("haohan:telescope_lens", 1)),
                                new ItemResult("haohan:telescope", 1));

                if (!recipeRegistry.exists(repairBrokenRecipe.getId())) {
                        recipeRegistry.register(repairBrokenRecipe);
                }
        }
}

