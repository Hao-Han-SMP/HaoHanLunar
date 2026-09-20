package vn.haohan.lunar.core.lunar.boss.warden;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunarWardenConfigTest {

    @Test
    void defaultsRemainCompatibleWithLegacyConstants() {
        LunarWardenConfig config = LunarWardenConfig.defaults();
        assertEquals(WardenConstants.MODEL_ID, config.modelId());
        assertEquals(WardenConstants.BOSS_MAX_HEALTH, config.maxHealth());
        assertEquals(EntityType.IRON_GOLEM, config.entityType());
        assertEquals(WardenConstants.ATTACK_ANIMATIONS.length, config.attackAnimations().size());
    }

    @Test
    void yamlValuesOverrideOnlyConfiguredFields() {
        LunarWardenConfig config = LunarWardenConfig.fromMap(Map.of(
                "entity-type", "IRON_GOLEM",
                "model-id", "custom_warden",
                "attributes", Map.of("max_health", 1500),
                "skills", List.of("custom_skill")));

        assertEquals("custom_warden", config.modelId());
        assertEquals(1500, config.maxHealth());
        assertEquals(List.of("custom_skill"), config.skillReferences());
        assertEquals(WardenConstants.WALK_CHASE_SPEED, config.walkChaseSpeed());
        assertTrue(config.tags().contains("boss"));
    }
}
