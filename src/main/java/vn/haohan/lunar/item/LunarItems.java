package vn.haohan.lunar.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemType;
import vn.haohan.itemcore.api.recipe.*;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.List;
import java.util.Map;

public class LunarItems {

        private static volatile boolean registered = false;

        public static synchronized void register() {
                if (registered) {
                        return;
                }
                registered = true;

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
                registry.register(ItemDefinition.builder("haohan:lunar_claymore")
                                .material(Material.NETHERITE_SWORD)
                                .displayName("§b§lThanh Kiếm Nguyệt Thạch")
                                .lore(List.of("§7Thanh đại kiếm mang sức mạnh bóng đêm của Mặt Trăng."))
                                .customModelData(6001)
                                .model("haohan:claymore")
                                .type(ItemType.WEAPON)
                                .maxStackSize(1)
                                .build());

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

                // 7. Lunar Quadruped Robot Items
                registry.register(ItemDefinition.builder("haohan:robot_tablet")
                                .material(Material.PAPER)
                                .displayName("§b§lBảng Điều Khiển Robot")
                                .lore(List.of(
                                                "§7Thiết bị kết nối và điều khiển từ xa cho Robot 4 Chân.",
                                                "§8▪ Chuột phải vào Robot hoang dã để giải mã kết nối.",
                                                "§8▪ Chuột phải khi đã liên kết để mở Dashboard quản lý."))
                                .customModelData(6001)
                                .type(ItemType.SPECIAL)
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:empty_module_slot")
                                .material(Material.PAPER)
                                .displayName("§7Ô Trống")
                                .lore(List.of("§8Khe cắm module trống trên Robot."))
                                .customModelData(6100)
                                .type(ItemType.SPECIAL)
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_module_ore_scan")
                                .material(Material.PAPER)
                                .displayName("§6§lModule Dò Quặng")
                                .lore(List.of(
                                                "§7Module cảm biến địa chất cho Robot 4 Chân.",
                                                "§e▪ Quét radar tìm quặng phổ thông xung quanh.",
                                                "§e▪ Tạo hiệu ứng Hologram xung quanh quặng hiếm.",
                                                "§7Độ hiệu quả: §a100.0%"))
                                .customModelData(6101)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_module", "ore_scan", "efficiency", 100.0))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_module_combat")
                                .material(Material.PAPER)
                                .displayName("§c§lModule Chiến Đấu")
                                .lore(List.of(
                                                "§7Module vũ trang cận chiến cho Robot 4 Chân.",
                                                "§c▪ Hỗ trợ người chơi tiêu diệt mục tiêu thù địch.",
                                                "§c▪ Tăng sát thương và tốc độ đánh theo độ hiệu quả.",
                                                "§7Độ hiệu quả: §a100.0%"))
                                .customModelData(6102)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_module", "combat", "efficiency", 100.0))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_module_speed")
                                .material(Material.PAPER)
                                .displayName("§b§lModule Tốc Hành")
                                .lore(List.of(
                                                "§7Module truyền động gia tốc cho Robot 4 Chân.",
                                                "§b▪ Cho phép người chơi ngồi lên và cưỡi di chuyển nhanh.",
                                                "§b▪ Giảm ảnh hưởng của trọng lực và triệt tiêu rơi.",
                                                "§7Độ hiệu quả: §a100.0%"))
                                .customModelData(6103)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_module", "speed", "efficiency", 100.0))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_module_thrust")
                                .material(Material.PAPER)
                                .displayName("§e§lModule Đẩy Phản Lực")
                                .lore(List.of(
                                                "§7Module phản lực tên lửa cho Robot 4 Chân.",
                                                "§6▪ Đẩy robot và người chơi phóng thẳng lên cao.",
                                                "§6▪ Tiêu hao năng lượng lớn khi kích hoạt.",
                                                "§7Độ hiệu quả: §a100.0%"))
                                .customModelData(6104)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_module", "thrust", "efficiency", 100.0))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_battery_small")
                                .material(Material.CARROT_ON_A_STICK)
                                .displayName("§a§lPin Robot Nhỏ")
                                .lore(List.of(
                                                "§7Nguồn năng lượng sơ cấp cho Robot 4 Chân.",
                                                "§a▪ Dung lượng: §f5,000 / 5,000 EU §a(100%)",
                                                "§8Đặt vào Trạm Sạc để nạp lại năng lượng."))
                                .customModelData(6201)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_battery", "small", "capacity", 5000, "max_damage", 5000))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_battery_medium")
                                .material(Material.CARROT_ON_A_STICK)
                                .displayName("§b§lPin Robot Vừa")
                                .lore(List.of(
                                                "§7Nguồn năng lượng tiêu chuẩn cho Robot 4 Chân.",
                                                "§b▪ Dung lượng: §f15,000 / 15,000 EU §a(100%)",
                                                "§8Đặt vào Trạm Sạc để nạp lại năng lượng."))
                                .customModelData(6202)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_battery", "medium", "capacity", 15000, "max_damage", 15000))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:robot_battery_large")
                                .material(Material.CARROT_ON_A_STICK)
                                .displayName("§d§lPin Robot Lớn")
                                .lore(List.of(
                                                "§7Lõi năng lượng lượng tử cho Robot 4 Chân.",
                                                "§d▪ Dung lượng: §f30,000 / 30,000 EU §a(100%)",
                                                "§8Đặt vào Trạm Sạc để nạp lại năng lượng."))
                                .customModelData(6203)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("robot_battery", "large", "capacity", 30000, "max_damage", 30000))
                                .maxStackSize(1)
                                .build());

                registry.register(ItemDefinition.builder("haohan:battery_charger")
                                .material(Material.CARROT_ON_A_STICK)
                                .displayName("§e§lTrạm Sạc Pin Robot")
                                .lore(List.of(
                                                "§7Trạm sạc năng lượng cao cấp cho Pin Robot 4 Chân.",
                                                "§e▪ Chế độ: §fNạp Thụ Động (Quang năng) & Nạp Nhanh",
                                                "§8Chuột phải lên mặt đất để đặt Trạm Sạc."))
                                .customModelData(6205)
                                .type(ItemType.SPECIAL)
                                .properties(Map.of("battery_charger", true))
                                .maxStackSize(16)
                                .build());

                // 8. Smithing Recipes for Spacesuit Armor Upgrade
                registerSpacesuitRecipes();

                // 9. Shapeless Recipes for Telescope Repair
                registerTelescopeRecipes();

                // 10. Robot Crafting & Maintenance Recipes
                registerRobotRecipes();
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

        private static void registerRobotRecipes() {
                var recipeRegistry = HaoHanItemCore.get().getRecipeRegistry();

                // 1. Tablet Recipe
                RecipeDefinition tabletRecipe = new ShapedRecipeDefinition(
                                "haohan:robot_tablet",
                                List.of(
                                                "SGS",
                                                "RAR",
                                                "SMS"),
                                Map.of(
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'G', new Ingredient.MaterialIngredient(Material.GLASS_PANE, 1),
                                                'R', new Ingredient.MaterialIngredient(Material.REDSTONE, 1),
                                                'A', new Ingredient.ItemIngredient("haohan:aero_compound", 1),
                                                'M', new Ingredient.MaterialIngredient(Material.AMETHYST_SHARD, 1)),
                                new ItemResult("haohan:robot_tablet", 1));
                if (!recipeRegistry.exists(tabletRecipe.getId())) {
                        recipeRegistry.register(tabletRecipe);
                }

                // 2. Battery Recipes
                RecipeDefinition batterySmall = new ShapedRecipeDefinition(
                                "haohan:robot_battery_small",
                                List.of(
                                                " C ",
                                                "SRS",
                                                " K "),
                                Map.of(
                                                'C', new Ingredient.MaterialIngredient(Material.COPPER_INGOT, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'R', new Ingredient.MaterialIngredient(Material.REDSTONE, 1),
                                                'K', new Ingredient.ItemIngredient("haohan:kreep_dust", 1)),
                                new ItemResult("haohan:robot_battery_small", 1));
                if (!recipeRegistry.exists(batterySmall.getId())) {
                        recipeRegistry.register(batterySmall);
                }

                RecipeDefinition batteryMed = new ShapedRecipeDefinition(
                                "haohan:robot_battery_medium",
                                List.of(
                                                " G ",
                                                "DBD",
                                                " K "),
                                Map.of(
                                                'G', new Ingredient.MaterialIngredient(Material.GOLD_INGOT, 1),
                                                'D', new Ingredient.MaterialIngredient(Material.DIAMOND, 1),
                                                'B', new Ingredient.ItemIngredient("haohan:robot_battery_small", 1),
                                                'K', new Ingredient.ItemIngredient("haohan:kreep_dust", 1)),
                                new ItemResult("haohan:robot_battery_medium", 1));
                if (!recipeRegistry.exists(batteryMed.getId())) {
                        recipeRegistry.register(batteryMed);
                }

                RecipeDefinition batteryLarge = new ShapedRecipeDefinition(
                                "haohan:robot_battery_large",
                                List.of(
                                                " N ",
                                                "ABA",
                                                " K "),
                                Map.of(
                                                'N', new Ingredient.MaterialIngredient(Material.NETHERITE_INGOT, 1),
                                                'A', new Ingredient.ItemIngredient("haohan:aero_compound", 1),
                                                'B', new Ingredient.ItemIngredient("haohan:robot_battery_medium", 1),
                                                'K', new Ingredient.ItemIngredient("haohan:kreep_dust", 1)),
                                new ItemResult("haohan:robot_battery_large", 1));
                if (!recipeRegistry.exists(batteryLarge.getId())) {
                        recipeRegistry.register(batteryLarge);
                }

                RecipeDefinition chargerRecipe = new ShapedRecipeDefinition(
                                "haohan:battery_charger",
                                List.of(
                                                "SCS",
                                                "RBR",
                                                "SKS"),
                                Map.of(
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'C', new Ingredient.MaterialIngredient(Material.COPPER_INGOT, 1),
                                                'R', new Ingredient.MaterialIngredient(Material.REDSTONE, 1),
                                                'B', new Ingredient.ItemIngredient("haohan:robot_battery_small", 1),
                                                'K', new Ingredient.ItemIngredient("haohan:kreep_dust", 1)),
                                new ItemResult("haohan:battery_charger", 1));
                if (!recipeRegistry.exists(chargerRecipe.getId())) {
                        recipeRegistry.register(chargerRecipe);
                }

                // 3. Module Recipes
                RecipeDefinition modOre = new ShapedRecipeDefinition(
                                "haohan:robot_module_ore_scan",
                                List.of(
                                                " C ",
                                                "SAS",
                                                " R "),
                                Map.of(
                                                'C', new Ingredient.MaterialIngredient(Material.COMPASS, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'A', new Ingredient.ItemIngredient("haohan:raw_anorthosite", 1),
                                                'R', new Ingredient.MaterialIngredient(Material.REDSTONE, 1)),
                                new ItemResult("haohan:robot_module_ore_scan", 1));
                if (!recipeRegistry.exists(modOre.getId())) {
                        recipeRegistry.register(modOre);
                }

                RecipeDefinition modCombat = new ShapedRecipeDefinition(
                                "haohan:robot_module_combat",
                                List.of(
                                                " D ",
                                                "SPS",
                                                " R "),
                                Map.of(
                                                'D', new Ingredient.MaterialIngredient(Material.DIAMOND_SWORD, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'P', new Ingredient.ItemIngredient("haohan:pyroxene_debris", 1),
                                                'R', new Ingredient.MaterialIngredient(Material.REDSTONE, 1)),
                                new ItemResult("haohan:robot_module_combat", 1));
                if (!recipeRegistry.exists(modCombat.getId())) {
                        recipeRegistry.register(modCombat);
                }

                RecipeDefinition modSpeed = new ShapedRecipeDefinition(
                                "haohan:robot_module_speed",
                                List.of(
                                                " F ",
                                                "SIS",
                                                " U "),
                                Map.of(
                                                'F', new Ingredient.MaterialIngredient(Material.FEATHER, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'I', new Ingredient.ItemIngredient("haohan:raw_ilmenite", 1),
                                                'U', new Ingredient.MaterialIngredient(Material.SUGAR, 1)),
                                new ItemResult("haohan:robot_module_speed", 1));
                if (!recipeRegistry.exists(modSpeed.getId())) {
                        recipeRegistry.register(modSpeed);
                }

                RecipeDefinition modThrust = new ShapedRecipeDefinition(
                                "haohan:robot_module_thrust",
                                List.of(
                                                " F ",
                                                "SAS",
                                                " B "),
                                Map.of(
                                                'F', new Ingredient.MaterialIngredient(Material.FIRE_CHARGE, 1),
                                                'S', new Ingredient.ItemIngredient("haohan:steel_ingot", 1),
                                                'A', new Ingredient.ItemIngredient("haohan:aero_compound", 1),
                                                'B', new Ingredient.MaterialIngredient(Material.BLAZE_POWDER, 1)),
                                new ItemResult("haohan:robot_module_thrust", 1));
                if (!recipeRegistry.exists(modThrust.getId())) {
                        recipeRegistry.register(modThrust);
                }
        }
}
