package vn.haohan.lunar.core.system.config;

import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.config.LunarYamlLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunarYamlLoaderTest {

    @Test
    void loadsYamlAndUsesFilenameWhenIdIsOmitted() throws Exception {
        Path root = Files.createTempDirectory("lunar-yaml-");
        Path mobs = Files.createDirectories(root.resolve("mobs"));
        Files.writeString(mobs.resolve("Lunar_Warden.yml"), "entity-type: IRON_GOLEM\nhealth: 500\n");

        LunarYamlLoader loader = new LunarYamlLoader();
        ConfigValidationReport report = loader.reload(root);

        assertTrue(report.isValid(), report::toString);
        assertTrue(loader.snapshot().directories().get("mobs").containsKey("lunar_warden"));
    }

    @Test
    void reportsSyntaxErrorWithFileAndLine() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("lunar-yaml-").resolve("skills"));
        Path file = root.resolve("broken.yml");
        Files.writeString(file, "trigger: [onSpawn\n");

        ConfigValidationReport report = new LunarYamlLoader().reload(root.getParent());

        assertFalse(report.isValid());
        assertEquals(file.toString(), report.issues().getFirst().file());
        assertTrue(report.issues().getFirst().line() > 0);
    }

    @Test
    void rejectsDuplicateIds() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("lunar-yaml-").resolve("drops"));
        Files.writeString(root.resolve("first.yml"), "id: shared\nitems: []\n");
        Files.writeString(root.resolve("second.yml"), "id: shared\nitems: []\n");

        ConfigValidationReport report = new LunarYamlLoader().reload(root.getParent());

        assertFalse(report.isValid());
        assertTrue(report.issues().stream().anyMatch(issue -> issue.message().contains("Duplicate")));
    }

    @Test
    void failedReloadKeepsLastGoodSnapshot() throws Exception {
        Path root = Files.createDirectories(Files.createTempDirectory("lunar-yaml-").resolve("mobs"));
        Path valid = root.resolve("warden.yml");
        Files.writeString(valid, "id: warden\nentity-type: IRON_GOLEM\n");
        LunarYamlLoader loader = new LunarYamlLoader();
        assertTrue(loader.reload(root.getParent()).isValid());

        Files.writeString(valid, "id: warden\nentity-type: [broken\n");
        ConfigValidationReport report = loader.reload(root.getParent());

        assertFalse(report.isValid());
        assertTrue(loader.snapshot().directories().get("mobs").containsKey("warden"));
    }
}
