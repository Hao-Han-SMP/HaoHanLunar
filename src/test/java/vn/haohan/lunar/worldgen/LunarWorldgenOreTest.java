package vn.haohan.lunar.worldgen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.data.type.NoteBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.features.LunarOrePopulatorListener;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LunarWorldgenOreTest {

    private final Gson gson = new Gson();

    @Test
    @DisplayName("Verify all 4 configured features exist and have valid ore structure")
    void testConfiguredFeatures() {
        List<String> ores = List.of("anorthosite_ore", "ilmenite_ore", "pyroxene_ore", "kreep_basalt");

        for (String ore : ores) {
            String path = "/datapack/data/haohan/worldgen/configured_feature/" + ore + ".json";
            InputStream is = getClass().getResourceAsStream(path);
            assertNotNull(is, "Missing configured feature resource: " + path);

            JsonObject json = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            assertEquals("minecraft:ore", json.get("type").getAsString());
            assertTrue(json.has("config"));
            JsonObject config = json.getAsJsonObject("config");
            assertTrue(config.get("size").getAsInt() > 0, "Vein size must be positive");
            assertTrue(config.has("targets"));
            assertTrue(config.getAsJsonArray("targets").size() > 0, "Must contain at least 1 target");
        }
    }

    @Test
    @DisplayName("Verify all 4 placed features exist and reference correct configured feature")
    void testPlacedFeatures() {
        List<String> ores = List.of("anorthosite_ore", "ilmenite_ore", "pyroxene_ore", "kreep_basalt");

        for (String ore : ores) {
            String path = "/datapack/data/haohan/worldgen/placed_feature/" + ore + ".json";
            InputStream is = getClass().getResourceAsStream(path);
            assertNotNull(is, "Missing placed feature resource: " + path);

            JsonObject json = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            assertEquals("haohan:" + ore, json.get("feature").getAsString());
            assertTrue(json.has("placement"));
            var placement = json.getAsJsonArray("placement");
            assertTrue(placement.size() >= 3);

            for (var elem : placement) {
                var obj = elem.getAsJsonObject();
                if ("minecraft:height_range".equals(obj.get("type").getAsString())) {
                    var height = obj.getAsJsonObject("height");
                    var maxInc = height.getAsJsonObject("max_inclusive");
                    int maxAbs = maxInc.get("absolute").getAsInt();
                    assertTrue(maxAbs <= 159, ore + " max height " + maxAbs + " exceeds dimension max Y of 159");
                }
            }
        }
    }

    @Test
    @DisplayName("Verify 7 lunar biomes contain underground ore steps")
    void testBiomesContainOres() {
        List<String> biomes = List.of(
                "lunar_terrae",
                "lunar_craters",
                "lunar_maria",
                "lunar_bright_patches",
                "lunar_crystal_craters",
                "lunar_giant_crystals",
                "lunar_giant_crystal_outskirts"
        );

        for (String biome : biomes) {
            String path = "/datapack/data/haohan/worldgen/biome/" + biome + ".json";
            InputStream is = getClass().getResourceAsStream(path);
            assertNotNull(is, "Missing biome resource: " + path);

            JsonObject json = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            assertTrue(json.has("features"), "Biome " + biome + " must have features array");
            var featuresArr = json.getAsJsonArray("features");
            assertTrue(featuresArr.size() >= 7, "Features array must have at least 7 steps");
            var oreStep = featuresArr.get(6).getAsJsonArray();
            assertTrue(oreStep.size() > 0, "Biome " + biome + " must have at least one placed ore feature at step 6");
        }
    }

    @Test
    @DisplayName("Test LunarOrePopulatorListener note resolution logic")
    void testOreResolution() {
        assertEquals("haohan:anorthosite_ore", LunarOrePopulatorListener.resolveOreId("PLING", 24));
        assertEquals("haohan:ilmenite_ore", LunarOrePopulatorListener.resolveOreId("PLING", 23));
        assertEquals("haohan:pyroxene_ore", LunarOrePopulatorListener.resolveOreId("PLING", 22));
        assertEquals("haohan:kreep_basalt", LunarOrePopulatorListener.resolveOreId("PLING", 21));
        assertNull(LunarOrePopulatorListener.resolveOreId("PLING", 10));
        assertNull(LunarOrePopulatorListener.resolveOreId("BASS", 24));
        assertNull(LunarOrePopulatorListener.resolveOreId((String) null, 24));
    }
}
