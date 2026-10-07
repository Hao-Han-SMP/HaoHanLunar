package vn.haohan.lunar.robot;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.features.GravityMechanic;

import static org.mockito.Mockito.*;

public class LunarRobotDimensionAttributeTest {

    @Test
    @DisplayName("Verify syncGravityAttributes applies attributes in Lunar world")
    public void testSyncGravityAttributesInLunar() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        GravityMechanic gravity = mock(GravityMechanic.class);
        when(plugin.getGravityMechanic()).thenReturn(gravity);

        World lunarWorld = mock(World.class);
        when(lunarWorld.getKey()).thenReturn(HaoHanLunarPlugin.LUNAR_WORLD_KEY);

        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getWorld()).thenReturn(lunarWorld);

        LunarRobotEntity.syncGravityAttributes(plugin, entity, lunarWorld);

        verify(gravity, times(1)).applyLunarAttributes(entity);
        verify(gravity, never()).removeLunarAttributes(entity);
    }

    @Test
    @DisplayName("Verify syncGravityAttributes removes attributes in Overworld")
    public void testSyncGravityAttributesInOverworld() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        GravityMechanic gravity = mock(GravityMechanic.class);
        when(plugin.getGravityMechanic()).thenReturn(gravity);

        World overworld = mock(World.class);
        when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));

        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getWorld()).thenReturn(overworld);

        LunarRobotEntity.syncGravityAttributes(plugin, entity, overworld);

        verify(gravity, times(1)).removeLunarAttributes(entity);
        verify(gravity, never()).applyLunarAttributes(entity);
    }
}
