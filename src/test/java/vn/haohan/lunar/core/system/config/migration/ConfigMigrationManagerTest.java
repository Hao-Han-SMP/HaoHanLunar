package vn.haohan.lunar.core.system.config.migration;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigrationManagerTest {

    @TempDir
    Path tempDir;

    private ConfigMigrationManager migrationManager;
    private MobDefinitionRegistry mobRegistry;
    private SkillRegistry skillRegistry;
    private DropManager dropManager;
    private LunarMobManager mobManager;

    @BeforeEach
    void setUp() {
        migrationManager = new ConfigMigrationManager();
        mobRegistry = new MobDefinitionRegistry();
        skillRegistry = new SkillRegistry();
        dropManager = new DropManager(mobRegistry, new LunarMobManager(), HaoHanItemBridge.get(), null);
        mobManager = new LunarMobManager();
    }

    @Test
    void successfulReloadUpdatesRegistriesAndActiveMobs() throws IOException {
        Path mobsDir = Files.createDirectories(tempDir.resolve("mobs"));
        Path skillsDir = Files.createDirectories(tempDir.resolve("skills"));
        Path dropsDir = Files.createDirectories(tempDir.resolve("drops"));

        // Create skill YAML using mechanics mapping
        Files.writeString(skillsDir.resolve("slam.yml"),
                "id: slam\n" +
                "trigger: onCombat\n" +
                "cooldown: 5\n" +
                "mechanics:\n" +
                "  - type: sound\n" +
                "    sound: entity_warden_heartbeat\n"
        );

        // Create drop table YAML
        Files.writeString(dropsDir.resolve("warden_loot.yml"),
                "id: warden_loot\n" +
                "rolls: 2\n"
        );

        // Create mob YAML with config-version: 1
        Files.writeString(mobsDir.resolve("lunar_boss.yml"),
                "config-version: 1\n" +
                "id: lunar_boss\n" +
                "name: 'Awakened Boss'\n" +
                "type: IRON_GOLEM\n" +
                "drop_table: warden_loot\n" +
                "skills:\n" +
                "- slam\n"
        );

        // Pre-existing active mob in combat
        UUID mobUuid = UUID.randomUUID();
        LivingEntity mockLiving = mockEntity(mobUuid);
        MobDefinition initialDef = new MobDefinition(
                new MobDefinitionId("lunar_boss"),
                EntityType.IRON_GOLEM,
                "Old Boss",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );
        ActiveLunarMob activeMob = new ActiveLunarMob(mockLiving, initialDef, new LunarMobIdentity("lunar_boss", "1.0"));
        activeMob.setStance("phase_2");
        mobManager.register(activeMob);

        // Execute Validate-then-Swap reload
        var report = migrationManager.reloadAll(tempDir, mobRegistry, skillRegistry, dropManager, mobManager);

        assertTrue(report.isSuccess(), "Reload should succeed: " + report.issues());
        assertEquals(1, report.loadedMobs());
        assertEquals(1, report.loadedSkills());
        assertEquals(1, report.loadedDrops());

        // Registry has new definition
        MobDefinition newDef = mobRegistry.get("lunar_boss").orElseThrow();
        assertEquals("Awakened Boss", newDef.displayName());

        // Active mob reference was hot-swapped without resetting stance
        assertEquals("Awakened Boss", activeMob.definition().displayName());
        assertEquals("phase_2", activeMob.stance(), "Active mob stance must be preserved across reload");
    }

    @Test
    void reloadFailsAndPreservesActiveStateWhenReferenceIsMissing() throws IOException {
        Path mobsDir = Files.createDirectories(tempDir.resolve("mobs"));
        Path skillsDir = Files.createDirectories(tempDir.resolve("skills"));

        // Mob references missing skill
        Files.writeString(mobsDir.resolve("broken_mob.yml"),
                "id: broken_mob\n" +
                "skills:\n" +
                "- non_existent_skill\n"
        );

        // Existing state
        MobDefinition existing = new MobDefinition(
                new MobDefinitionId("good_mob"),
                EntityType.IRON_GOLEM,
                "Good Mob",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );
        mobRegistry.register(existing);

        var report = migrationManager.reloadAll(tempDir, mobRegistry, skillRegistry, dropManager, mobManager);

        assertFalse(report.isSuccess(), "Reload must fail when missing cross-references");
        assertFalse(report.issues().isEmpty());

        // Good mob must remain in registry untouched
        assertTrue(mobRegistry.contains("good_mob"), "Existing registry must remain untouched on reload failure");
        assertFalse(mobRegistry.contains("broken_mob"));
    }

    @Test
    void checkConfigVersionGeneratesWarningsForMissingOrOlderVersions() {
        List<String> warnings = new java.util.ArrayList<>();

        // Missing version
        int v1 = ConfigMigrationManager.checkConfigVersion("test1", Map.of(), warnings);
        assertEquals(ConfigMigrationManager.CURRENT_CONFIG_VERSION, v1);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("missing 'config-version'"));

        // Older version
        warnings.clear();
        int v0 = ConfigMigrationManager.checkConfigVersion("test2", Map.of("config-version", 0), warnings);
        assertEquals(0, v0);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("uses older version 0"));
    }

    @Test
    void sampleResourcesLoadCleanlyWithZeroErrors() {
        Path sampleResources = Path.of("src/main/resources");
        if (!Files.isDirectory(sampleResources)) {
            return;
        }

        var report = migrationManager.reloadAll(sampleResources, mobRegistry, skillRegistry, dropManager, mobManager);
        assertTrue(report.isSuccess(), "Sample resources in src/main/resources must load cleanly without errors: " + report.issues());
        assertTrue(report.loadedMobs() >= 1, "At least 1 sample mob should be loaded");
        assertTrue(report.loadedSkills() >= 1, "At least 1 sample skill should be loaded");
        assertTrue(report.loadedDrops() >= 1, "At least 1 sample drop table should be loaded");
    }

    // --- Helpers ---

    private LivingEntity mockEntity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(
                ConfigMigrationManagerTest.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }
}
