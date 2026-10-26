package vn.haohan.lunar.robot;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.charger.BatteryChargerMechanic;
import vn.haohan.lunar.charger.BatteryChargerStation;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class RobotBatteryAndChargerTest {

    @Test
    public void testBatteryNominalAndExponentialCapacity() {
        assertEquals(35000, RobotBatteryUtil.NOMINAL_CAPACITY);
        assertEquals(35000, RobotBatteryUtil.getCapacity(RobotBatteryUtil.BATTERY_STANDARD));

        // Effective capacity formula: round(35000 * (health/100)^2)
        assertEquals(35000, RobotBatteryUtil.calculateEffectiveCapacity(100.0));
        assertEquals(28350, RobotBatteryUtil.calculateEffectiveCapacity(90.0)); // 35000 * 0.81
        assertEquals(17150, RobotBatteryUtil.calculateEffectiveCapacity(70.0)); // 35000 * 0.49
        assertEquals(8750, RobotBatteryUtil.calculateEffectiveCapacity(50.0));  // 35000 * 0.25
        assertEquals(1400, RobotBatteryUtil.calculateEffectiveCapacity(20.0));  // 35000 * 0.04
        assertEquals(1000, RobotBatteryUtil.calculateEffectiveCapacity(0.0), "Floor capacity must be at least 1000 EU");
    }

    @Test
    public void testRobotEnergySyncAndCycles() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());

        // Initially equipped with standard battery
        assertEquals("small", data.getBatteryType());
        assertEquals(35000, data.getMaxEnergy());
        assertEquals(35000, data.getEnergy());
        assertEquals(100.0, data.getBatteryHealth());
        assertEquals(0, data.getChargeCycles());

        // Robot consumes 5,000 EU
        assertTrue(data.consumeEnergy(5000));
        assertEquals(30000, data.getEnergy());

        // Increment charge cycle -> cycle = 1, health = 99.5%
        data.incrementChargeCycles();
        assertEquals(1, data.getChargeCycles());
        assertEquals(99.5, data.getBatteryHealth(), 0.001);

        // Effective max energy updates with health
        int expectedMaxE = RobotBatteryUtil.calculateEffectiveCapacity(99.5);
        assertEquals(expectedMaxE, data.getMaxEnergy());

        // Remove battery (Slot empty)
        data.setBatteryType("none");
        assertEquals("none", data.getBatteryType());
        assertEquals(0, data.getMaxEnergy());
        assertEquals(0, data.getEnergy());

        // Reinstall battery
        data.setBatteryType("small");
        assertEquals(expectedMaxE, data.getMaxEnergy());
    }

    @Test
    public void testChargerFuelOnlyRawAnorthosite() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 180.0f);

        assertEquals(0, station.getFuelBuffer());

        // Add 1 Raw Anorthosite (+3500 EU)
        station.addFuelBuffer(3500);
        assertEquals(3500, station.getFuelBuffer());

        // Add 2 more Raw Anorthosite (+7000 EU)
        station.addFuelBuffer(7000);
        assertEquals(10500, station.getFuelBuffer());

        // Consume 500 EU for MagSafe charging
        station.setFuelBuffer(station.getFuelBuffer() - 500);
        assertEquals(10000, station.getFuelBuffer());

        // Negative check clamping
        station.setFuelBuffer(-100);
        assertEquals(0, station.getFuelBuffer(), "Fuel buffer must not be negative");
    }

    @Test
    public void testChargerFourStateTransitions() {
        UUID owner = UUID.randomUUID();
        UUID display = UUID.randomUUID();
        BatteryChargerStation station = new BatteryChargerStation(null, display, owner, 0.0f);

        // 1. Initial State: Fuel == 0 -> RED
        assertEquals(0, station.getFuelBuffer());
        assertNull(station.getDockedRobotUuid());
        station.setCurrentState(BatteryChargerStation.ChargerState.RED);
        assertEquals(BatteryChargerStation.ChargerState.RED, station.getCurrentState());
        assertEquals("haohan:robot_charger_red", station.getCurrentState().getCustomItemId());

        // 2. Add Fuel -> Turns BLUE (Available)
        station.addFuelBuffer(3500);
        station.setCurrentState(BatteryChargerStation.ChargerState.BLUE);
        assertEquals(BatteryChargerStation.ChargerState.BLUE, station.getCurrentState());
        assertEquals("haohan:robot_charger", station.getCurrentState().getCustomItemId());

        // 3. Robot Docks -> Turns YELLOW (Charging)
        UUID robotId = UUID.randomUUID();
        station.setDockedRobotUuid(robotId);
        station.setCurrentState(BatteryChargerStation.ChargerState.YELLOW);
        assertEquals(BatteryChargerStation.ChargerState.YELLOW, station.getCurrentState());
        assertEquals("haohan:robot_charger_yellow", station.getCurrentState().getCustomItemId());

        // 4. Robot Done -> Turns CYAN (Completed)
        station.setDockedRobotUuid(null);
        station.setCyanDisplayTicks(100);
        station.setCurrentState(BatteryChargerStation.ChargerState.CYAN);
        assertEquals(BatteryChargerStation.ChargerState.CYAN, station.getCurrentState());
        assertEquals("haohan:robot_charger_cyan", station.getCurrentState().getCustomItemId());
    }

    @Test
    public void testChargerYawSnapCalculation() {
        float[] playerYaws = {0.0f, 85.0f, 95.0f, 175.0f, 260.0f, 355.0f};
        for (float pYaw : playerYaws) {
            float snap = Math.round((pYaw + 180.0f) / 90.0f) * 90.0f;
            assertEquals(0.0f, snap % 90.0f, "Snapped yaw should be a multiple of 90 degrees");
        }
    }

    @Test
    public void testChargerPickaxeTierRequirement() {
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(null));

        org.bukkit.inventory.ItemStack air = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(air.getType()).thenReturn(org.bukkit.Material.AIR);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(air));

        org.bukkit.inventory.ItemStack woodPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(woodPick.getType()).thenReturn(org.bukkit.Material.WOODEN_PICKAXE);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(woodPick));

        org.bukkit.inventory.ItemStack stonePick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(stonePick.getType()).thenReturn(org.bukkit.Material.STONE_PICKAXE);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(stonePick));

        org.bukkit.inventory.ItemStack ironPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(ironPick.getType()).thenReturn(org.bukkit.Material.IRON_PICKAXE);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(ironPick));

        org.bukkit.inventory.ItemStack goldPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(goldPick.getType()).thenReturn(org.bukkit.Material.GOLDEN_PICKAXE);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(goldPick));

        org.bukkit.inventory.ItemStack diamondSword = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(diamondSword.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(diamondSword));

        org.bukkit.inventory.ItemStack netheriteAxe = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(netheriteAxe.getType()).thenReturn(org.bukkit.Material.NETHERITE_AXE);
        assertFalse(BatteryChargerMechanic.isAllowedChargerPickaxe(netheriteAxe));

        org.bukkit.inventory.ItemStack diamondPick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(diamondPick.getType()).thenReturn(org.bukkit.Material.DIAMOND_PICKAXE);
        assertTrue(BatteryChargerMechanic.isAllowedChargerPickaxe(diamondPick));

        org.bukkit.inventory.ItemStack netheritePick = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        org.mockito.Mockito.when(netheritePick.getType()).thenReturn(org.bukkit.Material.NETHERITE_PICKAXE);
        assertTrue(BatteryChargerMechanic.isAllowedChargerPickaxe(netheritePick));
    }

    @Test
    public void testBreakStationDefenseInDepthCustomBlockPurge() throws Exception {
        vn.haohan.itemcore.api.block.CustomBlockService cbService = org.mockito.Mockito.mock(vn.haohan.itemcore.api.block.CustomBlockService.class);
        vn.haohan.itemcore.api.item.ItemFactory itemFactory = org.mockito.Mockito.mock(vn.haohan.itemcore.api.item.ItemFactory.class);
        vn.haohan.itemcore.api.HaoHanItemCore core = new vn.haohan.itemcore.api.HaoHanItemCore(
                null, itemFactory, null, null, null, null, cbService
        );
        vn.haohan.itemcore.api.HaoHanItemCore.setInstance(core);

        org.bukkit.Server mockServer = org.mockito.Mockito.mock(org.bukkit.Server.class);
        org.bukkit.block.data.BlockData mockBlockData = org.mockito.Mockito.mock(org.bukkit.block.data.BlockData.class);
        org.mockito.Mockito.when(mockServer.createBlockData(org.bukkit.Material.IRON_BLOCK)).thenReturn(mockBlockData);
        org.bukkit.inventory.ItemFactory bukkitItemFactory = org.mockito.Mockito.mock(org.bukkit.inventory.ItemFactory.class);
        org.mockito.Mockito.when(mockServer.getItemFactory()).thenReturn(bukkitItemFactory);

        java.lang.reflect.Field serverField = org.bukkit.Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, mockServer);

        try {
            org.bukkit.Location loc = org.mockito.Mockito.mock(org.bukkit.Location.class);
            org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
            org.mockito.Mockito.when(loc.getWorld()).thenReturn(world);
            org.mockito.Mockito.when(loc.clone()).thenReturn(loc);
            org.mockito.Mockito.when(loc.add(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(loc);
            org.mockito.Mockito.when(world.getName()).thenReturn("world");
            org.mockito.Mockito.when(world.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class)).thenReturn(java.util.Collections.emptyList());
            org.mockito.Mockito.when(loc.getBlockX()).thenReturn(10);
            org.mockito.Mockito.when(loc.getBlockY()).thenReturn(64);
            org.mockito.Mockito.when(loc.getBlockZ()).thenReturn(20);

            org.bukkit.block.Block block = org.mockito.Mockito.mock(org.bukkit.block.Block.class);
            org.mockito.Mockito.when(block.getLocation()).thenReturn(loc);

            org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
            org.mockito.Mockito.when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);

            BatteryChargerStation station = new BatteryChargerStation(loc, null, UUID.randomUUID(), 0.0f);
            station.setFuelBuffer(0);

            vn.haohan.lunar.HaoHanLunarPlugin plugin = org.mockito.Mockito.mock(vn.haohan.lunar.HaoHanLunarPlugin.class);
            org.mockito.Mockito.when(plugin.getName()).thenReturn("HaoHanLunar");
            java.io.File tempFolder = new java.io.File(System.getProperty("java.io.tmpdir"), "lunar_test_" + UUID.randomUUID());
            tempFolder.mkdirs();
            tempFolder.deleteOnExit();
            org.mockito.Mockito.when(plugin.getDataFolder()).thenReturn(tempFolder);

            BatteryChargerMechanic mechanic = new BatteryChargerMechanic(plugin);
            mechanic.getStations().put(mechanic.formatKey(loc), station);

            mechanic.breakStation(player, block, station);

            // Verify block set to AIR
            org.mockito.Mockito.verify(block).setType(org.bukkit.Material.AIR);
            // Verify defense-in-depth purged customBlockService
            org.mockito.Mockito.verify(cbService).removeCustomBlock(loc);
        } finally {
            serverField.set(null, null);
            vn.haohan.itemcore.api.HaoHanItemCore.setInstance(null);
        }
    }
}
