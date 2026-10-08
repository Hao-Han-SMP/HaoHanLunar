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
                "haohan:kreep_basalt"
        };

        for (String oreId : lunarOres) {
            ItemDefinition def = itemRegistry.get(oreId);
            assertNotNull(def, "Ore item must be registered: " + oreId);

            Map<String, Object> props = def.getProperties();
            assertTrue(props.containsKey("custom_block_id"), oreId + " must contain custom_block_id");
            assertEquals(oreId, props.get("custom_block_id"), "custom_block_id must match oreId");

            assertTrue(props.containsKey("custom_block_model"), oreId + " must contain custom_block_model");
            String modelStr = (String) props.get("custom_block_model");
            assertNotNull(modelStr);
            assertTrue(modelStr.startsWith("minecraft:note_block["), "custom_block_model must reference blockstate: " + modelStr);

            assertTrue(props.containsKey("custom_block_drop"), oreId + " must contain custom_block_drop");
            assertNotNull(props.get("custom_block_drop"));

            assertTrue(ItemCategory.CUSTOM_BLOCKS.matches(def), oreId + " must match ItemCategory.CUSTOM_BLOCKS");
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
