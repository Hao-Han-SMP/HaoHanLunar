package vn.haohan.lunar.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledDatapackResourceTest {

    @Test
    @DisplayName("Classpath contains bundled datapack pack.mcmeta and data directory")
    void testBundledDatapackResourceExists() {
        URL packMetaUrl = getClass().getResource("/datapack/pack.mcmeta");
        assertNotNull(packMetaUrl, "Resource /datapack/pack.mcmeta must be present in classpath");

        try (InputStream is = packMetaUrl.openStream()) {
            assertNotNull(is, "InputStream for /datapack/pack.mcmeta should not be null");
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes);
            assertTrue(content.contains("pack"), "pack.mcmeta must contain 'pack' key");
            assertTrue(content.contains("min_format"), "pack.mcmeta must contain 'min_format'");
        } catch (Exception e) {
            throw new RuntimeException("Failed reading /datapack/pack.mcmeta", e);
        }

        URL dimensionUrl = getClass().getResource("/datapack/data/haohan/dimension/lunar.json");
        assertNotNull(dimensionUrl, "Resource /datapack/data/haohan/dimension/lunar.json must be present in classpath");
    }
}
