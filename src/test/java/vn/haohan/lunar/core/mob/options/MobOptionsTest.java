package vn.haohan.lunar.core.mob.options;

import vn.haohan.lunar.api.mob.options.*;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobOptionsTest {

    @Test
    void parseAllOptionsFromMap() {
        Map<String, Object> map = Map.ofEntries(
                Map.entry("prevent-other-drops", true),
                Map.entry("prevent-random-equipment", true),
                Map.entry("prevent-sunburn", true),
                Map.entry("prevent-knockback", true),
                Map.entry("prevent-leashing", true),
                Map.entry("prevent-rename", true),
                Map.entry("prevent-enderman-teleport", true),
                Map.entry("prevent-item-pickup", true),
                Map.entry("prevent-silverfish-infection", true),
                Map.entry("prevent-exploding", true),
                Map.entry("prevent-mob-kill-drops", true),
                Map.entry("prevent-transformation", true),
                Map.entry("prevent-mounts", true),
                Map.entry("passthrough-damage", true),
                Map.entry("apply-invisibility", true),
                Map.entry("prevent-vanilla-damage", true),
                Map.entry("despawn-mode", "PERSISTENT"),
                Map.entry("no-damage-ticks", 0)
        );

        MobOptions opt = MobOptions.fromMap(map);

        assertTrue(opt.preventOtherDrops());
        assertTrue(opt.preventRandomEquipment());
        assertTrue(opt.preventSunburn());
        assertTrue(opt.preventKnockback());
        assertTrue(opt.preventLeashing());
        assertTrue(opt.preventRename());
        assertTrue(opt.preventEndermanTeleport());
        assertTrue(opt.preventItemPickup());
        assertTrue(opt.preventSilverfishInfection());
        assertTrue(opt.preventExploding());
        assertTrue(opt.preventMobKillDrops());
        assertTrue(opt.preventTransformation());
        assertTrue(opt.preventMounts());
        assertTrue(opt.passthroughDamage());
        assertTrue(opt.applyInvisibility());
        assertTrue(opt.preventVanillaDamage());
        assertEquals(MobOptions.DespawnMode.PERSISTENT, opt.despawnMode());
        assertEquals(0, opt.noDamageTicks());
    }

    @Test
    void defaultsWhenEmptyOrNull() {
        MobOptions opt = MobOptions.fromMap(Map.of());
        assertFalse(opt.preventSunburn());
        assertFalse(opt.preventKnockback());
        assertEquals(MobOptions.DespawnMode.DESPAWN, opt.despawnMode());
        assertEquals(20, opt.noDamageTicks());
    }
}
