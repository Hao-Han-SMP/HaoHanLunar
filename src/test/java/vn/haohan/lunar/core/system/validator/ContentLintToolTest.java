package vn.haohan.lunar.core.system.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContentLintToolTest {

    @Test
    void testStandardResourcesPassValidation() {
        Path resourcesPath = Path.of("src/main/resources");
        ContentLintTool.LintReport report = ContentLintTool.lintDirectory(resourcesPath);
        assertFalse(report.hasErrors(), "Standard resources should have 0 validation errors");
        assertEquals(0, report.errorCount());
    }

    @Test
    void testDetectsMissingRequiredFieldsAndOrphans(@TempDir Path tempDir) throws IOException {
        Path mobsDir = tempDir.resolve("mobs");
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(mobsDir);
        Files.createDirectories(skillsDir);

        // Mob with missing type and orphan skill
        String invalidMobYaml = """
                id: broken_boss
                skills:
                  - non_existent_skill_xyz
                """;
        Files.writeString(mobsDir.resolve("broken_boss.yml"), invalidMobYaml);

        ContentLintTool.LintReport report = ContentLintTool.lintDirectory(tempDir);
        assertTrue(report.hasErrors(), "Should detect errors in broken mob");
        assertTrue(report.issues().stream().anyMatch(i -> i.field().equals("type")), "Should report missing 'type'");
        assertTrue(report.issues().stream().anyMatch(i -> i.field().equals("skills") && i.message().contains("non_existent_skill_xyz")),
                "Should report orphan skill reference");
    }

    @Test
    void testValidCustomMobAndSkill(@TempDir Path tempDir) throws IOException {
        Path mobsDir = tempDir.resolve("mobs");
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(mobsDir);
        Files.createDirectories(skillsDir);

        String skillYaml = """
                id: fiery_breath
                trigger: onCombat
                cooldown: 15
                mechanics:
                  - type: damage
                    amount: 25.0
                """;
        Files.writeString(skillsDir.resolve("fiery_breath.yml"), skillYaml);

        String mobYaml = """
                id: fire_drake
                type: ENDER_DRAGON
                skills:
                  - fiery_breath
                """;
        Files.writeString(mobsDir.resolve("fire_drake.yml"), mobYaml);

        ContentLintTool.LintReport report = ContentLintTool.lintDirectory(tempDir);
        assertFalse(report.hasErrors());
        assertEquals(0, report.errorCount());
    }
}
