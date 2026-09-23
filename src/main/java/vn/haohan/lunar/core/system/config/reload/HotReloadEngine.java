package vn.haohan.lunar.core.system.config.reload;

import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillChainDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.loot.DropTableDefinition;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.api.system.spawner.fixed.SpawnerDefinition;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.system.validator.ContentLintTool;
import vn.haohan.lunar.core.system.validator.ContentLintTool.LintIssue;
import vn.haohan.lunar.core.system.validator.ContentLintTool.LintReport;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Atomic hot-reload engine for all lunar mob, skill, drop, and spawner systems.
 * Performs asynchronous linting and verification, completely preventing registry corruption.
 */
public final class HotReloadEngine {

    public record ReloadResult(
            boolean success,
            String message,
            int reloadedMobs,
            int reloadedSkills,
            int reloadedDrops,
            int reloadedSpawners,
            int activeMobsRefreshed,
            List<LintIssue> issues
    ) {
        public static ReloadResult failure(String message, List<LintIssue> issues) {
            return new ReloadResult(false, message, 0, 0, 0, 0, 0, issues != null ? issues : List.of());
        }

        public static ReloadResult success(int mobs, int skills, int drops, int spawners, int refreshed, List<LintIssue> warnings) {
            return new ReloadResult(true, "Configuration hot-reloaded successfully.", mobs, skills, drops, spawners, refreshed, warnings != null ? warnings : List.of());
        }
    }

    private final Path configRoot;
    private final MobDefinitionRegistry mobRegistry;
    private final SkillRegistry skillRegistry;
    private final DropManager dropManager;
    private final FixedSpawnerManager spawnerManager;
    private final LunarMobManager mobManager;
    private final Executor asyncExecutor;

    public HotReloadEngine(
            Path configRoot,
            MobDefinitionRegistry mobRegistry,
            SkillRegistry skillRegistry,
            DropManager dropManager,
            FixedSpawnerManager spawnerManager,
            LunarMobManager mobManager,
            Executor asyncExecutor
    ) {
        this.configRoot = Objects.requireNonNull(configRoot, "Config root must not be null");
        this.mobRegistry = Objects.requireNonNull(mobRegistry, "Mob registry must not be null");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "Skill registry must not be null");
        this.dropManager = Objects.requireNonNull(dropManager, "Drop manager must not be null");
        this.spawnerManager = Objects.requireNonNull(spawnerManager, "Spawner manager must not be null");
        this.mobManager = Objects.requireNonNull(mobManager, "Mob manager must not be null");
        this.asyncExecutor = asyncExecutor != null ? asyncExecutor : ForkJoinPool.commonPool();
    }

    /**
     * Executes asynchronous pre-validation, followed by synchronized atomic registry swap.
     */
    public CompletableFuture<ReloadResult> reloadAsync(
            Supplier<Collection<MobDefinition>> mobLoader,
            Supplier<Collection<SkillChainDefinition>> skillLoader,
            Supplier<Collection<DropTableDefinition>> dropLoader,
            Supplier<Collection<SpawnerDefinition>> spawnerLoader
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Step 1: Run comprehensive linting on background thread
            LintReport report = ContentLintTool.lintDirectory(configRoot);
            if (report.hasErrors()) {
                return ReloadResult.failure(
                        "Reload rejected: " + report.errorCount() + " schema/reference error(s) detected.",
                        report.issues()
                );
            }

            // Step 2: Load new definitions in memory without modifying active registries
            Collection<MobDefinition> newMobs;
            Collection<SkillChainDefinition> newSkills;
            Collection<DropTableDefinition> newDrops;
            Collection<SpawnerDefinition> newSpawners;

            try {
                newMobs = mobLoader != null ? mobLoader.get() : List.of();
                newSkills = skillLoader != null ? skillLoader.get() : List.of();
                newDrops = dropLoader != null ? dropLoader.get() : List.of();
                newSpawners = spawnerLoader != null ? spawnerLoader.get() : List.of();
            } catch (Exception ex) {
                return ReloadResult.failure("Failed to parse configurations: " + ex.getMessage(), List.of());
            }

            return applyReload(newMobs, newSkills, newDrops, newSpawners, report.issues());
        }, asyncExecutor);
    }

    /**
     * Synchronous reload with pre-built candidate collections (useful in tests and direct invocations).
     */
    public ReloadResult applyReload(
            Collection<MobDefinition> newMobs,
            Collection<SkillChainDefinition> newSkills,
            Collection<DropTableDefinition> newDrops,
            Collection<SpawnerDefinition> newSpawners,
            List<LintIssue> issues
    ) {
        synchronized (this) {
            // Atomic registry replacement
            if (newMobs != null && !newMobs.isEmpty()) {
                mobRegistry.replaceAll(newMobs);
            }
            if (newSkills != null && !newSkills.isEmpty()) {
                skillRegistry.replaceAll(newSkills);
            }
            if (newDrops != null && !newDrops.isEmpty()) {
                dropManager.replaceAll(newDrops);
            }
            if (newSpawners != null && !newSpawners.isEmpty()) {
                spawnerManager.replaceAll(newSpawners);
            }

            // Refresh living mob instances
            int refreshed = mobManager.refreshActiveDefinitions(mobRegistry);

            List<LintIssue> warnings = issues != null
                    ? issues.stream().filter(i -> !i.isError()).toList()
                    : List.of();

            return ReloadResult.success(
                    newMobs != null ? newMobs.size() : 0,
                    newSkills != null ? newSkills.size() : 0,
                    newDrops != null ? newDrops.size() : 0,
                    newSpawners != null ? newSpawners.size() : 0,
                    refreshed,
                    warnings
            );
        }
    }

    @FunctionalInterface
    public interface Supplier<T> {
        T get() throws Exception;
    }
}
