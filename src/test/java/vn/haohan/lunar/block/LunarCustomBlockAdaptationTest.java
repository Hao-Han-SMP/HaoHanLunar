package vn.haohan.lunar.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.CustomBlockRegistry;
import vn.haohan.itemcore.api.block.CustomBlockService;
import vn.haohan.itemcore.api.item.ItemCategory;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemFactory;
import vn.haohan.itemcore.api.item.ItemRegistry;
import vn.haohan.itemcore.api.item.ItemService;
import vn.haohan.itemcore.api.recipe.RecipeRegistry;
import vn.haohan.itemcore.api.recipe.RecipeService;
import vn.haohan.itemcore.internal.item.DefaultItemRegistry;
import vn.haohan.lunar.features.MiningMechanic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LunarCustomBlockAdaptationTest {

    private ItemRegistry itemRegistry;
    private CustomBlockService customBlockService;
    private CustomBlockRegistry customBlockRegistry;

    @BeforeEach
    void setUp() {
        itemRegistry = new DefaultItemRegistry(Logger.getLogger("test"));
        customBlockService = mock(CustomBlockService.class);
        customBlockRegistry = mock(CustomBlockRegistry.class);
        when(customBlockService.getRegistry()).thenReturn(customBlockRegistry);

        ItemFactory itemFactory = mock(ItemFactory.class);
        ItemService itemService = mock(ItemService.class);
        RecipeRegistry recipeRegistry = mock(RecipeRegistry.class);
        RecipeService recipeService = mock(RecipeService.class);

        HaoHanItemCore core = new HaoHanItemCore(
                itemRegistry, itemFactory, itemService,
                recipeRegistry, recipeService, null,
                customBlockService
        );
        HaoHanItemCore.setInstance(core);
    }

    @AfterEach
    void tearDown() {
        HaoHanItemCore.setInstance(null);
    }

    @Test
    @DisplayName("Quặng Lunar phải có custom_block_id, custom_block_model và custom_block_drop theo chuẩn mới")
    void testLunarOresHaveCustomBlockProperties() throws Exception {
        Class<?> lunarItemsClass = Class.forName("vn.haohan.lunar.item.LunarItems");
        Field regField = lunarItemsClass.getDeclaredField("registered");
        regField.setAccessible(true);
        regField.set(null, false);

        Method regMethod = lunarItemsClass.getDeclaredMethod("register");
        regMethod.invoke(null);

        String[] lunarOres = {
                "haohan:anorthosite_ore",
                "haohan:ilmenite_ore",
                "haohan:pyroxene_ore",
                "haohan:kreep_basalt",
                "haohan:battery_charger"
        };

        for (String oreId : lunarOres) {
            ItemDefinition def = itemRegistry.get(oreId);
            assertNotNull(def, "Ore/Block item must be registered: " + oreId);

            Map<String, Object> props = def.getProperties();
            assertTrue(props.containsKey("custom_block_id"), oreId + " must contain custom_block_id");
            assertEquals(oreId, props.get("custom_block_id"), "custom_block_id must match oreId");

            assertTrue(props.containsKey("custom_block_model"), oreId + " must contain custom_block_model");
            String modelStr = (String) props.get("custom_block_model");
            assertNotNull(modelStr);
            assertEquals(oreId, modelStr, "custom_block_model must reference item ID directly: " + modelStr);
            assertFalse(modelStr.contains("note_block"), "custom_block_model must not reference note_block: " + modelStr);

            assertTrue(props.containsKey("custom_block_drop"), oreId + " must contain custom_block_drop");
            assertNotNull(props.get("custom_block_drop"));

            assertTrue(props.containsKey("custom_block_hardness"), oreId + " must contain custom_block_hardness");
            float hardness = ((Number) props.get("custom_block_hardness")).floatValue();
            assertTrue(hardness >= 4.0f, oreId + " hardness must be >= 4.0s");

            if ("haohan:anorthosite_ore".equals(oreId)) {
                assertEquals(90.0f, hardness, "anorthosite_ore hardness must be 90.0f");
                assertEquals("netherite", props.get("custom_block_required_tier"), "anorthosite_ore must require netherite tier");
            } else if ("haohan:kreep_basalt".equals(oreId)) {
                assertEquals(80.0f, hardness, "kreep_basalt hardness must be 80.0f");
                assertEquals("soulsteel", props.get("custom_block_required_tier"), "kreep_basalt must require soulsteel tier");
            } else if ("haohan:pyroxene_ore".equals(oreId)) {
                assertEquals(75.0f, hardness, "pyroxene_ore hardness must be 75.0f");
                assertEquals("mithril", props.get("custom_block_required_tier"), "pyroxene_ore must require mithril tier");
            } else if ("haohan:ilmenite_ore".equals(oreId)) {
                assertEquals(70.0f, hardness, "ilmenite_ore hardness must be 70.0f");
                assertEquals("diamond", props.get("custom_block_required_tier"), "ilmenite_ore must require diamond tier");
            }

            assertTrue(props.containsKey("custom_block_tool_type"), oreId + " must contain custom_block_tool_type");
            assertEquals("PICKAXE", props.get("custom_block_tool_type"), oreId + " must require PICKAXE");

            assertTrue(props.containsKey("custom_block_sounds"), oreId + " must contain custom_block_sounds");
            Object soundsObj = props.get("custom_block_sounds");
            assertTrue(soundsObj instanceof Map<?, ?>, oreId + " custom_block_sounds must be a Map");
            Map<?, ?> soundMap = (Map<?, ?>) soundsObj;
            assertTrue(soundMap.containsKey("place"), oreId + " sounds must have place");
            assertTrue(soundMap.containsKey("break"), oreId + " sounds must have break");
            assertTrue(soundMap.containsKey("hit"), oreId + " sounds must have hit");
            assertTrue(soundMap.containsKey("step"), oreId + " sounds must have step");

            boolean isBatteryCharger = "haohan:battery_charger".equals(oreId);
            assertEquals(Material.BARRIER, def.getMaterial(), oreId + " must use Material.BARRIER");
            if (!isBatteryCharger) {
                assertTrue(props.containsKey("custom_variation"), oreId + " must contain custom_variation");
                int expectedVariation = switch (oreId) {
                    case "haohan:kreep_basalt" -> 771;
                    case "haohan:pyroxene_ore" -> 772;
                    case "haohan:ilmenite_ore" -> 773;
                    case "haohan:anorthosite_ore" -> 774;
                    default -> -1;
                };
                assertEquals(expectedVariation, ((Number) props.get("custom_variation")).intValue(), oreId + " variation must match");
            }

            assertEquals(vn.haohan.itemcore.api.item.ItemType.BLOCK, def.getType(), oreId + " must use ItemType.BLOCK");
            assertTrue(ItemCategory.CUSTOM_BLOCKS.matches(def), oreId + " must match ItemCategory.CUSTOM_BLOCKS");
            assertFalse(ItemCategory.SPECIAL.matches(def), oreId + " must not match ItemCategory.SPECIAL");
        }

        // Verify that custom block definition was registered with a valid SoundGroup
        org.mockito.ArgumentCaptor<vn.haohan.itemcore.api.block.CustomBlockDefinition> captor =
                org.mockito.ArgumentCaptor.forClass(vn.haohan.itemcore.api.block.CustomBlockDefinition.class);
        verify(customBlockRegistry, atLeast(5)).register(captor.capture());
        for (vn.haohan.itemcore.api.block.CustomBlockDefinition blockDef : captor.getAllValues()) {
            assertNotNull(blockDef.getSoundGroup(), "Registered blockDef " + blockDef.getId() + " must have a SoundGroup");
            assertNotNull(blockDef.getSoundGroup().getPlaceSound(), "SoundGroup must have placeSound");
            assertNotNull(blockDef.getSoundGroup().getBreakSound(), "SoundGroup must have breakSound");
            assertNotNull(blockDef.getSoundGroup().getHitSound(), "SoundGroup must have hitSound");
            assertNotNull(blockDef.getSoundGroup().getStepSound(), "SoundGroup must have stepSound");

            if ("haohan:battery_charger".equals(blockDef.getId())) {
                assertTrue(blockDef.isAlternative(), "battery_charger must be AlternativeCustomBlock");
            } else if (blockDef.getId().startsWith("haohan:")) {
                assertTrue(blockDef.isNative(), blockDef.getId() + " must be NativeCustomBlock");
                vn.haohan.itemcore.api.block.NativeCustomBlock nativeBlock = (vn.haohan.itemcore.api.block.NativeCustomBlock) blockDef;
                int expectedVar = switch (blockDef.getId()) {
                    case "haohan:kreep_basalt" -> 771;
                    case "haohan:pyroxene_ore" -> 772;
                    case "haohan:ilmenite_ore" -> 773;
                    case "haohan:anorthosite_ore" -> 774;
                    default -> -1;
                };
                assertEquals(expectedVar, nativeBlock.getCustomVariation(), blockDef.getId() + " variation must match");
            }
        }
    }

    @Test
    @DisplayName("MiningMechanic phải nhận diện Barrier custom block quặng Lunar")
    void testMiningMechanicDetectsBarrier() throws Exception {
        MiningMechanic mechanic = new MiningMechanic(null);
        Method isLookingMethod = MiningMechanic.class.getDeclaredMethod("isLookingAtLunarOre", Player.class);
        isLookingMethod.setAccessible(true);

        // 1. Test với Barrier có custom block ID là anorthosite_ore
        Player playerBarrier = mock(Player.class);
        Block barrierBlock = mock(Block.class);
        when(barrierBlock.getType()).thenReturn(Material.BARRIER);
        when(playerBarrier.getTargetBlockExact(5)).thenReturn(barrierBlock);

        when(customBlockService.isCustomBlock(barrierBlock)).thenReturn(true);
        when(customBlockService.getCustomBlockId(barrierBlock)).thenReturn("haohan:anorthosite_ore");

        boolean resultBarrier = (boolean) isLookingMethod.invoke(mechanic, playerBarrier);
        assertTrue(resultBarrier, "MiningMechanic must recognize custom barrier ore");

        // 2. Test với Barrier nhưng không phải quặng Mặt Trăng (ví dụ khối khác)
        when(customBlockService.getCustomBlockId(barrierBlock)).thenReturn("haohan:iron_crate");
        boolean resultOtherBlock = (boolean) isLookingMethod.invoke(mechanic, playerBarrier);
        assertFalse(resultOtherBlock, "MiningMechanic must ignore non-lunar custom blocks");

        // 3. Test với khối thường (Stone)
        Player playerStone = mock(Player.class);
        Block stoneBlock = mock(Block.class);
        when(stoneBlock.getType()).thenReturn(Material.STONE);
        when(playerStone.getTargetBlockExact(5)).thenReturn(stoneBlock);

        boolean resultStone = (boolean) isLookingMethod.invoke(mechanic, playerStone);
        assertFalse(resultStone, "MiningMechanic must return false for standard stone");
    }

    @Test
    @DisplayName("Robot 4 Chân phải nhận diện chính xác quặng Barrier hiếm và phổ thông")
    void testRobotOreScanRecognition() {
        Block barrierOreRare = mock(Block.class);
        when(barrierOreRare.getType()).thenReturn(Material.BARRIER);
        when(customBlockService.isCustomBlock(barrierOreRare)).thenReturn(true);
        when(customBlockService.getCustomBlockId(barrierOreRare)).thenReturn("haohan:pyroxene_ore");

        Block barrierOreCommon = mock(Block.class);
        when(barrierOreCommon.getType()).thenReturn(Material.BARRIER);
        when(customBlockService.isCustomBlock(barrierOreCommon)).thenReturn(true);
        when(customBlockService.getCustomBlockId(barrierOreCommon)).thenReturn("haohan:anorthosite_ore");

        Block normalBlock = mock(Block.class);
        when(normalBlock.getType()).thenReturn(Material.STONE);
        when(customBlockService.isCustomBlock(normalBlock)).thenReturn(false);

        // 1. Test getCustomBlockIdSafe
        assertEquals("haohan:pyroxene_ore", vn.haohan.lunar.robot.LunarRobotEntity.getCustomBlockIdSafe(barrierOreRare));
        assertEquals("haohan:anorthosite_ore", vn.haohan.lunar.robot.LunarRobotEntity.getCustomBlockIdSafe(barrierOreCommon));
        assertNull(vn.haohan.lunar.robot.LunarRobotEntity.getCustomBlockIdSafe(normalBlock));
        assertNull(vn.haohan.lunar.robot.LunarRobotEntity.getCustomBlockIdSafe(null));

        // 2. Test ore classification
        assertTrue(vn.haohan.lunar.robot.LunarRobotEntity.isRareCustomOre("haohan:pyroxene_ore"));
        assertTrue(vn.haohan.lunar.robot.LunarRobotEntity.isRareCustomOre("haohan:kreep_basalt"));
        assertFalse(vn.haohan.lunar.robot.LunarRobotEntity.isRareCustomOre("haohan:anorthosite_ore"));

        assertTrue(vn.haohan.lunar.robot.LunarRobotEntity.isCommonCustomOre("haohan:anorthosite_ore"));
        assertTrue(vn.haohan.lunar.robot.LunarRobotEntity.isCommonCustomOre("haohan:ilmenite_ore"));
        assertFalse(vn.haohan.lunar.robot.LunarRobotEntity.isCommonCustomOre("haohan:pyroxene_ore"));
    }
}
