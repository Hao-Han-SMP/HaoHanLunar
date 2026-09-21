package vn.haohan.lunar.core.system.config.reload;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillChainDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.loot.DropTableDefinition;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.api.system.spawner.fixed.SpawnerDefinition;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.system.validator.ContentLintTool;
import vn.haohan.lunar.core.system.validator.ContentLintTool.LintReport;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadAndLintingTest {

    @TempDir
    Path tempConfigDir;

    private MobDefinitionRegistry mobRegistry;
    private SkillRegistry skillRegistry;
    private DropManager dropManager;
    private FixedSpawnerManager spawnerManager;
    private LunarMobManager mobManager;
    private HotReloadEngine reloadEngine;
    private World mockWorld;

    @BeforeEach
    void setUp() {
        mobRegistry = new MobDefinitionRegistry();
        skillRegistry = new SkillRegistry();
        dropManager = new DropManager();
        spawnerManager = new FixedSpawnerManager(mobRegistry, null);
        mobManager = new LunarMobManager();
        reloadEngine = new HotReloadEngine(
                tempConfigDir,
                mobRegistry,
                skillRegistry,
                dropManager,
                spawnerManager,
                mobManager,
                ForkJoinPool.commonPool()
        );

        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    return null;
                }
        );
    }

    private LivingEntity createMockLivingEntity(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return loc.getWorld();
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    return null;
                }
        );
    }

    private MobDefinition createMobDef(String id, double maxHealth) {
        return new MobDefinition(
                new MobDefinitionId(id),
                EntityType.ZOMBIE,
                "Zombie " + id,
                null,
                Map.of("generic.max_health", new MobAttributeDefinition("generic.max_health", maxHealth)),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                null,
                List.of(),
                null,
                List.of(),
                List.of()
        );
    }

    @Test
    void testContentLintToolValidConfig() throws IOException {
        Path mobsDir = Files.createDirectories(tempConfigDir.resolve("mobs"));
        Path skillsDir = Files.createDirectories(tempConfigDir.resolve("skills"));

        Files.writeString(skillsDir.resolve("fireball.yml"), """
                id: fireball
                mechanics:
                  - type: damage
                    amount: 15
                """);

        Files.writeString(mobsDir.resolve("pyromancer.yml"), """
                id: pyromancer
                type: ZOMBIE
                skills:
                  - fireball
                """);

        LintReport report = ContentLintTool.lintDirectory(tempConfigDir);
        assertFalse(report.hasErrors(), "Lint report should have no errors for valid configurations");
        assertEquals(0, report.errorCount());
    }

    @Test
    void testContentLintToolDetectsOrphanAndMissingFields() throws IOException {
        Path mobsDir = Files.createDirectories(tempConfigDir.resolve("mobs"));
        Path spawnersDir = Files.createDirectories(tempConfigDir.resolve("spawners"));

        // Mob with missing type and orphan skill
        Files.writeString(mobsDir.resolve("bad_mob.yml"), """
                id: bad_mob
                skills:
                  - non_existent_skill
                """);

        // Spawner referencing non-existent mob
        Files.writeString(spawnersDir.resolve("spawner1.yml"), """
                id: spawner1
                mob: non_existent_mob
                """);

        LintReport report = ContentLintTool.lintDirectory(tempConfigDir);
        assertTrue(report.hasErrors(), "Lint report must detect schema and orphan errors");
        assertTrue(report.errorCount() >= 3, "Expected at least 3 errors (missing type, orphan skill, orphan mob)");
    }

    @Test
    void testContentLintToolDetectsCircularSkills() throws IOException {
        Path skillsDir = Files.createDirectories(tempConfigDir.resolve("skills"));

        // Skill A calls Skill B
        Files.writeString(skillsDir.resolve("skill_a.yml"), """
                id: skill_a
                mechanics:
                  - type: skill
                    skill: skill_b
                """);

        // Skill B calls Skill A (cycle!)
        Files.writeString(skillsDir.resolve("skill_b.yml"), """
                id: skill_b
                mechanics:
                  - type: skill
                    skill: skill_a
                """);

        LintReport report = ContentLintTool.lintDirectory(tempConfigDir);
        assertTrue(report.hasErrors(), "Lint report must detect circular skill recursion");
        boolean foundCycle = report.issues().stream()
                .anyMatch(i -> i.message().contains("Circular skill recursion detected"));
        assertTrue(foundCycle, "Report must contain circular recursion error message");
    }

    @Test
    void testHotReloadEngineRejectsWhenLintFails() throws IOException {
        Path mobsDir = Files.createDirectories(tempConfigDir.resolve("mobs"));
        Files.writeString(mobsDir.resolve("broken.yml"), "id: broken\n"); // Missing 'type'

        // Register initial valid mob
        MobDefinition initialMob = createMobDef("initial_mob", 50.0);
        mobRegistry.register(initialMob);
        assertTrue(mobRegistry.contains("initial_mob"));

        // Execute async reload
        HotReloadEngine.ReloadResult result = reloadEngine.reloadAsync(
                () -> List.of(createMobDef("new_mob", 100.0)),
                List::of,
                List::of,
                List::of
        ).join();

        assertFalse(result.success(), "Hot reload must be rejected when linting fails");
        assertTrue(result.message().contains("Reload rejected"));
        // Live registry must remain intact and unmodified!
        assertTrue(mobRegistry.contains("initial_mob"));
        assertFalse(mobRegistry.contains("new_mob"));
    }

    @Test
    void testHotReloadEngineAppliesChangesAndRefreshesLivingMobs() {
        // Initial setup
        MobDefinition v1Def = createMobDef("boss_titan", 100.0);
        mobRegistry.register(v1Def);

        LivingEntity entity = createMockLivingEntity(new Location(mockWorld, 0, 0, 0));
        ActiveMob activeMob = new ActiveMob(entity, v1Def, new LunarMobIdentity("boss_titan", "1.0", Optional.of("inst-1")));
        mobManager.register(activeMob);

        assertEquals(100.0, activeMob.definition().attributes().get("generic.max_health").baseValue());

        // Create updated definition (v2) with 500 health
        MobDefinition v2Def = createMobDef("boss_titan", 500.0);

        HotReloadEngine.ReloadResult result = reloadEngine.applyReload(
                List.of(v2Def),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        assertTrue(result.success());
        assertEquals(1, result.reloadedMobs());
        assertEquals(1, result.activeMobsRefreshed());

        // Verify live registry updated
        assertEquals(500.0, mobRegistry.get("boss_titan").orElseThrow().attributes().get("generic.max_health").baseValue());

        // Verify existing living entity in the world was refreshed without recreation
        assertEquals(500.0, activeMob.definition().attributes().get("generic.max_health").baseValue());
        assertEquals(entity.getUniqueId(), activeMob.entityId());
    }

    @Test
    void testConcurrentReadDuringHotReload() throws InterruptedException {
        int readers = 6;
        int readsPerThread = 5000;
        ExecutorService executor = Executors.newFixedThreadPool(readers + 1);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readers);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        // Prepopulate
        mobRegistry.register(createMobDef("mob_0", 10.0));

        // Reader threads continuously query registry
        for (int r = 0; r < readers; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < readsPerThread; i++) {
                        var snap = mobRegistry.snapshot();
                        assertNotNull(snap);
                    }
                } catch (Throwable t) {
                    errorOccurred.set(true);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Background reload writer thread
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 50; i++) {
                    List<MobDefinition> updated = new ArrayList<>();
                    for (int m = 0; m < 20; m++) {
                        updated.add(createMobDef("mob_" + m, 10.0 + i));
                    }
                    reloadEngine.applyReload(updated, List.of(), List.of(), List.of(), List.of());
                    Thread.sleep(2);
                }
            } catch (Throwable ignored) {
            }
        });

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        assertFalse(errorOccurred.get(), "No thread should fail or throw ConcurrentModificationException during hot reload");

        executor.shutdown();
    }
}
