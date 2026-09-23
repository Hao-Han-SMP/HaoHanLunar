package vn.haohan.lunar.core.mob.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.mob.pack.PackDefinition;
import vn.haohan.lunar.api.system.mob.pack.PackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentPackAndNamespaceTest {

    @Test
    void loadsMultiplePacksWithIdenticalInternalIdsWithoutCollisions(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot);

        // Pack 1: alpha_pack
        Path alphaDir = packsRoot.resolve("alpha_pack");
        Files.createDirectories(alphaDir.resolve("mobs"));
        Files.createDirectories(alphaDir.resolve("skills"));
        Files.createDirectories(alphaDir.resolve("drops"));
        Files.writeString(alphaDir.resolve("pack.yml"), "name: alpha_pack\nversion: 1.0.0\nauthor: LunarTeam\n");
        Files.writeString(alphaDir.resolve("mobs/boss.yml"), "boss:\n  display-name: 'Alpha Boss'\n  type: IRON_GOLEM\nscout:\n  display-name: 'Alpha Scout'\n");
        Files.writeString(alphaDir.resolve("skills/skills.yml"), """
                slam:
                  trigger: [onCombat]
                  cooldown: 50
                  targeter: self
                  mechanics:
                    - type: damage
                      amount: 10
                """);
        Files.writeString(alphaDir.resolve("drops/loot.yml"), "loot:\n  rolls: 2\n");

        // Pack 2: beta_pack
        Path betaDir = packsRoot.resolve("beta_pack");
        Files.createDirectories(betaDir.resolve("mobs"));
        Files.createDirectories(betaDir.resolve("skills"));
        Files.createDirectories(betaDir.resolve("drops"));
        Files.writeString(betaDir.resolve("pack.yml"), "name: beta_pack\nversion: 2.0.0\nauthor: LunarTeam\n");
        Files.writeString(betaDir.resolve("mobs/boss.yml"), "boss:\n  display-name: 'Beta Boss'\n  type: ZOMBIE\n");
        Files.writeString(betaDir.resolve("skills/skills.yml"), """
                slam:
                  trigger: [onTimer]
                  cooldown: 20
                  targeter: self
                  mechanics:
                    - type: heal
                      amount: 5
                """);
        Files.writeString(betaDir.resolve("drops/loot.yml"), "loot:\n  rolls: 5\n");

        PackManager packManager = new PackManager();
        MobDefinitionRegistry mobRegistry = new MobDefinitionRegistry();
        SkillRegistry skillRegistry = new SkillRegistry();
        DropManager dropManager = new DropManager();

        PackManager.PackLoadReport report = packManager.loadAll(packsRoot, mobRegistry, skillRegistry, dropManager);
        assertTrue(report.success(), "Report should succeed: " + report.errors());
        assertEquals(2, report.loadedPacksCount());

        // Namespaced queries
        assertTrue(mobRegistry.contains("alpha_pack:boss"));
        assertTrue(mobRegistry.contains("beta_pack:boss"));
        assertEquals("Alpha Boss", mobRegistry.get(new MobDefinitionId("alpha_pack:boss")).orElseThrow().displayName());
        assertEquals("Beta Boss", mobRegistry.get(new MobDefinitionId("beta_pack:boss")).orElseThrow().displayName());

        assertTrue(skillRegistry.contains("alpha_pack:slam"));
        assertTrue(skillRegistry.contains("beta_pack:slam"));

        assertTrue(dropManager.hasTable("alpha_pack:loot"));
        assertTrue(dropManager.hasTable("beta_pack:loot"));

        // Unique short alias resolution works
        assertEquals(Optional.of("alpha_pack:scout"), packManager.resolveMobId("scout"));

        // Conflicting short alias resolution is ambiguous and returns empty
        assertEquals(Optional.empty(), packManager.resolveMobId("boss"));
        assertEquals(Optional.empty(), packManager.resolveSkillId("slam"));
        assertEquals(Optional.empty(), packManager.resolveDropId("loot"));

        // Direct namespaced resolution via manager
        assertEquals(Optional.of("alpha_pack:boss"), packManager.resolveMobId("alpha_pack:boss"));
        assertEquals(Optional.of("beta_pack:boss"), packManager.resolveMobId("beta_pack:boss"));
    }

    @Test
    void reportsErrorWhenDependencyIsMissing(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot);

        Path packDir = packsRoot.resolve("addon_pack");
        Files.createDirectories(packDir);
        Files.writeString(packDir.resolve("pack.yml"), "name: addon_pack\nversion: 1.0.0\ndependencies:\n  - core_pack\n");

        PackManager packManager = new PackManager();
        PackManager.PackLoadReport report = packManager.loadAll(packsRoot, null, null, null);

        assertFalse(report.success());
        assertTrue(report.errors().stream().anyMatch(e -> e.contains("missing required dependency: core_pack")));
    }

    @Test
    void hotReloadSinglePackPreservesOtherPacks(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot);

        // Pack 1: alpha_pack
        Path alphaDir = packsRoot.resolve("alpha_pack");
        Files.createDirectories(alphaDir.resolve("mobs"));
        Files.writeString(alphaDir.resolve("pack.yml"), "name: alpha_pack\n");
        Files.writeString(alphaDir.resolve("mobs/boss.yml"), "boss:\n  display-name: 'Alpha Boss v1'\n");

        // Pack 2: beta_pack
        Path betaDir = packsRoot.resolve("beta_pack");
        Files.createDirectories(betaDir.resolve("mobs"));
        Files.writeString(betaDir.resolve("pack.yml"), "name: beta_pack\n");
        Files.writeString(betaDir.resolve("mobs/boss.yml"), "boss:\n  display-name: 'Beta Boss'\n");

        PackManager packManager = new PackManager();
        MobDefinitionRegistry mobRegistry = new MobDefinitionRegistry();

        packManager.loadAll(packsRoot, mobRegistry, null, null);
        assertEquals("Alpha Boss v1", mobRegistry.get(new MobDefinitionId("alpha_pack:boss")).orElseThrow().displayName());
        assertEquals("Beta Boss", mobRegistry.get(new MobDefinitionId("beta_pack:boss")).orElseThrow().displayName());

        // Update Alpha Boss on disk
        Files.writeString(alphaDir.resolve("mobs/boss.yml"), "boss:\n  display-name: 'Alpha Boss v2'\n");

        // Reload ONLY alpha_pack
        PackManager.PackLoadReport reloadReport = packManager.reloadPack(packsRoot, "alpha_pack", mobRegistry, null, null);
        assertTrue(reloadReport.success());

        // Alpha is updated to v2, Beta is completely preserved and untouched!
        assertEquals("Alpha Boss v2", mobRegistry.get(new MobDefinitionId("alpha_pack:boss")).orElseThrow().displayName());
        assertEquals("Beta Boss", mobRegistry.get(new MobDefinitionId("beta_pack:boss")).orElseThrow().displayName());
    }

    @Test
    void detectsModelEngineAssets(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot);

        Path packDir = packsRoot.resolve("warden_pack");
        Files.createDirectories(packDir.resolve("assets/models"));
        Files.writeString(packDir.resolve("pack.yml"), "name: warden_pack\n");
        Files.writeString(packDir.resolve("assets/models/lunar_warden.bbmodel"), "mock-model-content");

        PackManager packManager = new PackManager();
        packManager.loadAll(packsRoot, null, null, null);

        PackDefinition pack = packManager.getPack("warden_pack").orElseThrow();
        assertTrue(pack.modelAssets().contains("lunar_warden.bbmodel"));
    }
}
