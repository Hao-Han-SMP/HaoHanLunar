package vn.haohan.lunar.features;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GravityMechanicTeleportTest {

    private HaoHanLunarPlugin createMockPlugin() {
        HaoHanLunarPlugin plugin = mock(HaoHanLunarPlugin.class);
        PluginDescriptionFile pdf = mock(PluginDescriptionFile.class);
        when(pdf.getName()).thenReturn("haohanlunar");
        when(plugin.getDescription()).thenReturn(pdf);
        when(plugin.getName()).thenReturn("haohanlunar");
        when(plugin.namespace()).thenReturn("haohanlunar");
        return plugin;
    }

    @Test
    @DisplayName("Test onEntityTeleport calls applyLunarAttributes when moving to Lunar")
    public void testEntityTeleportToLunarAppliesAttributes() {
        HaoHanLunarPlugin plugin = createMockPlugin();
        AtomicBoolean applied = new AtomicBoolean(false);
        AtomicBoolean removed = new AtomicBoolean(false);

        GravityMechanic mechanic = new GravityMechanic(plugin) {
            @Override
            public void applyLunarAttributes(LivingEntity entity) {
                applied.set(true);
            }

            @Override
            public void removeLunarAttributes(LivingEntity entity) {
                removed.set(true);
            }
        };

        LivingEntity entity = mock(LivingEntity.class);

        World overworld = mock(World.class);
        when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));

        World lunarWorld = mock(World.class);
        when(lunarWorld.getKey()).thenReturn(HaoHanLunarPlugin.LUNAR_WORLD_KEY);

        Location from = new Location(overworld, 100, 64, 100);
        Location to = new Location(lunarWorld, 50, 70, 50);

        EntityTeleportEvent event = new EntityTeleportEvent(entity, from, to);
        mechanic.onEntityTeleport(event);

        assertTrue(applied.get(), "applyLunarAttributes should be called when teleporting to Lunar");
        assertFalse(removed.get(), "removeLunarAttributes should not be called when teleporting to Lunar");
    }

    @Test
    @DisplayName("Test onEntityTeleport calls removeLunarAttributes when moving to Overworld")
    public void testEntityTeleportFromLunarRemovesAttributes() {
        HaoHanLunarPlugin plugin = createMockPlugin();
        AtomicBoolean applied = new AtomicBoolean(false);
        AtomicBoolean removed = new AtomicBoolean(false);

        GravityMechanic mechanic = new GravityMechanic(plugin) {
            @Override
            public void applyLunarAttributes(LivingEntity entity) {
                applied.set(true);
            }

            @Override
            public void removeLunarAttributes(LivingEntity entity) {
                removed.set(true);
            }
        };

        LivingEntity entity = mock(LivingEntity.class);

        World lunarWorld = mock(World.class);
        when(lunarWorld.getKey()).thenReturn(HaoHanLunarPlugin.LUNAR_WORLD_KEY);

        World overworld = mock(World.class);
        when(overworld.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));

        Location from = new Location(lunarWorld, 50, 70, 50);
        Location to = new Location(overworld, 100, 64, 100);

        EntityTeleportEvent event = new EntityTeleportEvent(entity, from, to);
        mechanic.onEntityTeleport(event);

        assertTrue(removed.get(), "removeLunarAttributes should be called when teleporting away from Lunar");
        assertFalse(applied.get(), "applyLunarAttributes should not be called when teleporting away from Lunar");
    }
}
