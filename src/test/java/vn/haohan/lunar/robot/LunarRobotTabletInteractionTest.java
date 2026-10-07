package vn.haohan.lunar.robot;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.robot.ui.LunarRobotDashboardUi;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LunarRobotTabletInteractionTest {

    @Test
    @DisplayName("1. Verify onPlayerInteract cancels event and ignores reopening when Dashboard is already open")
    public void testTabletClickIgnoredWhenDashboardActive() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        LunarRobotMechanic mechanic = new LunarRobotMechanic(plugin);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        PlayerInventory inv = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inv);

        // Tablet item setup
        ItemStack tablet = mock(ItemStack.class);
        when(tablet.getType()).thenReturn(Material.PAPER);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(tablet.getItemMeta()).thenReturn(meta);
        when(pdc.get(any(), eq(PersistentDataType.STRING))).thenReturn("haohan:robot_tablet");
        when(inv.getItemInMainHand()).thenReturn(tablet);
        when(inv.getItemInOffHand()).thenReturn(null);

        // Active Dashboard mock
        LunarRobotDashboardUi activeDashboard = mock(LunarRobotDashboardUi.class);
        when(activeDashboard.getState()).thenReturn(LunarRobotDashboardUi.UiState.DASHBOARD);

        var mapField = getActiveDashboardsMap(mechanic);
        mapField.put(playerId, activeDashboard);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, tablet, null, null, EquipmentSlot.HAND);
        mechanic.onPlayerInteract(event);

        // Event MUST be cancelled and no new dashboard opened
        assertTrue(event.isCancelled(), "PlayerInteractEvent must be cancelled when dashboard is active");
        assertSame(activeDashboard, mechanic.getActiveDashboard(playerId), "Active dashboard must not be replaced");
    }

    @Test
    @DisplayName("2. Verify onPlayerArmSwing delegates to dashboard handleClickAtCursor when Dashboard is active")
    public void testArmSwingTriggersDashboardClick() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        LunarRobotMechanic mechanic = new LunarRobotMechanic(plugin);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        LunarRobotDashboardUi activeDashboard = mock(LunarRobotDashboardUi.class);
        when(activeDashboard.getState()).thenReturn(LunarRobotDashboardUi.UiState.DASHBOARD);

        var mapField = getActiveDashboardsMap(mechanic);
        mapField.put(playerId, activeDashboard);

        PlayerAnimationEvent swingEvent = new PlayerAnimationEvent(player, PlayerAnimationType.ARM_SWING);
        mechanic.onPlayerArmSwing(swingEvent);

        verify(activeDashboard, times(1)).handleClickAtCursor(player);
    }

    @Test
    @DisplayName("3. Verify onPlayerInteractEntity delegates to dashboard handleClickAtCursor when Dashboard is active")
    public void testRightClickInteractEntityTriggersDashboardClick() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        LunarRobotMechanic mechanic = new LunarRobotMechanic(plugin);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        org.bukkit.entity.Entity targetEntity = mock(org.bukkit.entity.Entity.class);

        LunarRobotDashboardUi activeDashboard = mock(LunarRobotDashboardUi.class);
        when(activeDashboard.getState()).thenReturn(LunarRobotDashboardUi.UiState.DASHBOARD);

        var mapField = getActiveDashboardsMap(mechanic);
        mapField.put(playerId, activeDashboard);

        org.bukkit.event.player.PlayerInteractEntityEvent entityEvent =
                new org.bukkit.event.player.PlayerInteractEntityEvent(player, targetEntity, EquipmentSlot.HAND);
        mechanic.onPlayerInteractEntity(entityEvent);

        assertTrue(entityEvent.isCancelled(), "PlayerInteractEntityEvent must be cancelled");
        verify(activeDashboard, times(1)).handleClickAtCursor(player);
    }

    @Test
    @DisplayName("4. Verify onPlayerInteract right click delegates to dashboard handleClickAtCursor when Dashboard is active")
    public void testRightClickPlayerInteractEventTriggersDashboardClick() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        LunarRobotMechanic mechanic = new LunarRobotMechanic(plugin);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        LunarRobotDashboardUi activeDashboard = mock(LunarRobotDashboardUi.class);
        when(activeDashboard.getState()).thenReturn(LunarRobotDashboardUi.UiState.DASHBOARD);

        var mapField = getActiveDashboardsMap(mechanic);
        mapField.put(playerId, activeDashboard);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, null, null, null, EquipmentSlot.HAND);
        mechanic.onPlayerInteract(event);

        assertTrue(event.isCancelled(), "PlayerInteractEvent must be cancelled");
        verify(activeDashboard, times(1)).handleClickAtCursor(player);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<UUID, LunarRobotDashboardUi> getActiveDashboardsMap(LunarRobotMechanic mechanic) {
        try {
            var field = LunarRobotMechanic.class.getDeclaredField("activeDashboards");
            field.setAccessible(true);
            return (java.util.Map<UUID, LunarRobotDashboardUi>) field.get(mechanic);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
