package vn.haohan.lunar.core.mob;

import vn.haohan.lunar.api.mob.*;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDefinitionTest {

    @Test
    void idsNormalizeAndCompareByValue() {
        assertEquals(new MobDefinitionId("LUNAR_WARDEN"), new MobDefinitionId("lunar_warden"));
        assertEquals("lunar_warden", new MobDefinitionId(" Lunar_Warden ").value());
    }

    @Test
    void requiredValuesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new MobDefinitionId("bad id"));
        assertThrows(NullPointerException.class, () -> definition(null, "warden"));
        assertThrows(IllegalArgumentException.class, () -> definition("", "warden"));
    }

    @Test
    void optionalValuesAndCollectionsHaveSafeDefaults() {
        MobDefinition definition = definition("lunar_warden", "warden");

        assertTrue(definition.modelId().isEmpty());
        assertTrue(definition.dropTableReference().isEmpty());
        assertTrue(definition.attributes().isEmpty());
        assertTrue(definition.options().isEmpty());
        assertTrue(definition.skillReferences().isEmpty());
        assertTrue(definition.tags().isEmpty());
        assertFalse(definition.attributes() instanceof java.util.HashMap);
    }

    @Test
    void collectionsAreImmutableAndValuesAreRetained() {
        MobDefinition definition = new MobDefinition(
                new MobDefinitionId("warden"), EntityType.IRON_GOLEM, "Lunar Warden", "warden_model",
                Map.of("max_health", new MobAttributeDefinition("max_health", 500.0)),
                Map.of("silent", new MobOptionDefinition("silent", "false")),
                List.of("ground_slam"), "warden_drops", Set.of("boss", "lunar"));

        assertEquals(500.0, definition.attributes().get("max_health").baseValue());
        assertEquals("warden_model", definition.modelId().orElseThrow());
        assertEquals("warden_drops", definition.dropTableReference().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> definition.tags().add("changed"));
    }

    private static MobDefinition definition(String id, String displayName) {
        return new MobDefinition(id == null ? null : new MobDefinitionId(id), EntityType.IRON_GOLEM,
                displayName, null, Map.of(), Map.of(), List.of(), null, Set.of());
    }
}
