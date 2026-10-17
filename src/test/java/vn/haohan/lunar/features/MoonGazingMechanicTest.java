package vn.haohan.lunar.features;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MoonGazingMechanicTest {

    @Test
    @DisplayName("Test standing still looking at moon accumulates cuoiTicks and awards advancement")
    public void testMoonCuoiAdvancementAwarded() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        List<NamespacedKey> granted = new ArrayList<>();
        MoonGazingMechanic mechanic = new MoonGazingMechanic(plugin, false) {
            @Override
            protected void grantAdvancement(Player player, NamespacedKey key) {
                granted.add(key);
            }
        };

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getTime()).thenReturn(18000L); // Midnight
        when(world.hasStorm()).thenReturn(false);

        Location loc = new Location(world, 100, 64, 100);
        loc.setDirection(new org.bukkit.util.Vector(0, 1, 0)); // Looking straight up at moon
        when(player.getLocation()).thenReturn(loc);
        when(player.getEyeLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        when(world.getHighestBlockYAt(any(Location.class))).thenReturn(64);

        // Simulate 40 ticks check (with 5 ticks interval per tick check, 40 * 5 = 200 ticks = 10s)
        for (int i = 0; i < 40; i++) {
            mechanic.checkPlayer(player, 5);
        }

        assertTrue(granted.contains(MoonGazingMechanic.ADV_CUOI), "Should have granted ADV_CUOI advancement");
    }

    @Test
    @DisplayName("Test spyglass looking at moon for 0.75s (15 ticks) awards spyglass advancement")
    public void testMoonSpyglassAdvancementAwarded() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        List<NamespacedKey> granted = new ArrayList<>();
        MoonGazingMechanic mechanic = new MoonGazingMechanic(plugin, false) {
            @Override
            protected void grantAdvancement(Player player, NamespacedKey key) {
                granted.add(key);
            }
        };

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getTime()).thenReturn(18000L); // Midnight
        when(world.hasStorm()).thenReturn(false);

        Location loc = new Location(world, 100, 64, 100);
        loc.setDirection(new org.bukkit.util.Vector(0, 1, 0)); // Looking straight up at moon
        when(player.getLocation()).thenReturn(loc);
        when(player.getEyeLocation()).thenReturn(loc);
        when(player.getWorld()).thenReturn(world);
        when(world.getHighestBlockYAt(any(Location.class))).thenReturn(64);

        // Player is using spyglass (mock ItemStack to avoid Paper RegistryAccess uninitialized error in tests)
        when(player.isHandRaised()).thenReturn(true);
        when(player.getHandRaised()).thenReturn(EquipmentSlot.HAND);
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack spyglassItem = mock(ItemStack.class);
        when(spyglassItem.getType()).thenReturn(Material.SPYGLASS);
        when(inv.getItem(EquipmentSlot.HAND)).thenReturn(spyglassItem);
        when(player.getInventory()).thenReturn(inv);

        // Simulate 3 ticks check (3 * 5 = 15 ticks = 0.75s)
        for (int i = 0; i < 3; i++) {
            mechanic.checkPlayer(player, 5);
        }

        assertTrue(granted.contains(MoonGazingMechanic.ADV_SPYGLASS), "Should have granted ADV_SPYGLASS advancement");
    }

    @Test
    @DisplayName("Test movement resets cuoiTicks counter")
    public void testMovementResetsCuoiCounter() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        MoonGazingMechanic mechanic = new MoonGazingMechanic(plugin, false);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getTime()).thenReturn(18000L);
        when(world.hasStorm()).thenReturn(false);
        when(player.getWorld()).thenReturn(world);

        Location loc1 = new Location(world, 100, 64, 100);
        loc1.setDirection(new org.bukkit.util.Vector(0, 1, 0));
        when(player.getLocation()).thenReturn(loc1);
        when(player.getEyeLocation()).thenReturn(loc1);
        when(world.getHighestBlockYAt(any(Location.class))).thenReturn(64);

        // Tick once
        mechanic.checkPlayer(player, 5);
        assertEquals(5, mechanic.getCuoiTicks(uuid));

        // Player moves significantly
        Location loc2 = new Location(world, 105, 64, 100);
        loc2.setDirection(new org.bukkit.util.Vector(0, 1, 0));
        when(player.getLocation()).thenReturn(loc2);
        when(player.getEyeLocation()).thenReturn(loc2);

        mechanic.checkPlayer(player, 5);
        // After move, it should reset back to 5 (not 10)
        assertEquals(5, mechanic.getCuoiTicks(uuid));
    }
}
