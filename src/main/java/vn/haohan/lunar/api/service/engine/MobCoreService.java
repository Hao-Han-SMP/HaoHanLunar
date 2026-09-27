package vn.haohan.lunar.api.service.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.LunarAPI;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.equipment.ItemProviderRegistry;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillScheduler;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraRegistry;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraScheduler;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.projectile.ProjectileTracker;
import vn.haohan.lunar.api.system.combat.skill.target.BasicTargeterRegistry;
import vn.haohan.lunar.api.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.core.config.reload.HotReloadEngine;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.service.IService;
import vn.haohan.lunar.core.mob.MobSkillRuntime;
import vn.haohan.lunar.api.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.api.system.throttle.DynamicThrottlingEngine;
import vn.haohan.lunar.api.system.variable.VariableManager;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Subsystem initializing the mob runtime, skill engine, combat pipeline,
 * auras, projectiles, and variables, then binding them to the public {@link LunarAPI} service locator.
 */
public class MobCoreService implements IService {

    private LunarMobManager mobManager;
    private MobDefinitionRegistry mobRegistry;
    private SkillRegistry skillRegistry;
    private DamagePipeline damagePipeline;
    private ConditionRegistry conditionRegistry;
    private MechanicRegistry mechanicRegistry;
    private BasicTargeterRegistry targeterRegistry;
    private DropManager dropManager;
    private FixedSpawnerManager fixedSpawnerManager;
    private ItemProviderRegistry itemProviderRegistry;
    private Path configRoot;
    private final PerformanceMetrics performanceMetrics = new PerformanceMetrics();
    private HotReloadEngine hotReloadEngine;

    private AuraRegistry auraRegistry;
    private AuraScheduler auraScheduler;
    private ProjectileTracker projectileTracker;
    private VariableManager variableManager;
    private DynamicThrottlingEngine dynamicThrottlingEngine;
    private SkillScheduler skillScheduler;
    private MobSkillRuntime mobSkillRuntime;

    private long currentTick = 0L;

    @Override
    public String name() {
        return "MobCore";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void init(HaoHanLunarPlugin plugin) {
        mobManager = new LunarMobManager();
        mobRegistry = new MobDefinitionRegistry();
        skillRegistry = new SkillRegistry();
        damagePipeline = new DamagePipeline();
        conditionRegistry = new ConditionRegistry();
        mechanicRegistry = new MechanicRegistry();
        targeterRegistry = new BasicTargeterRegistry();
        skillRegistry.setRegistries(mechanicRegistry, conditionRegistry, targeterRegistry);

        auraRegistry = new AuraRegistry();
        auraScheduler = new AuraScheduler();
        projectileTracker = new ProjectileTracker();
        variableManager = new VariableManager();
        dynamicThrottlingEngine = new DynamicThrottlingEngine();
        skillScheduler = new SkillScheduler(plugin, warning -> {
            if (plugin != null) {
                plugin.getLogger().warning(warning);
            }
        });

        conditionRegistry.setAuraScheduler(auraScheduler);
        conditionRegistry.setMobManagerSupplier(() -> mobManager);
        mechanicRegistry.setAuraScheduler(auraScheduler);
        mechanicRegistry.setAuraRegistry(auraRegistry);
        mechanicRegistry.setProjectileTracker(projectileTracker);
        mechanicRegistry.setVariableManager(variableManager);
        mechanicRegistry.setMobManager(mobManager);

        mobSkillRuntime = new MobSkillRuntime(mobManager, skillRegistry, mechanicRegistry, conditionRegistry, targeterRegistry, new CooldownRegistry(), skillScheduler, dynamicThrottlingEngine, plugin != null ? plugin.getLogger() : null);

        mechanicRegistry.signalBus().registerListener((delivery, depth) -> {
            if (mobSkillRuntime != null && delivery.recipient() != null) {
                mobSkillRuntime.onSignal(delivery.recipient(), delivery.signal(), currentTick);
            }
        });

        mechanicRegistry.setSubskillInvoker((skillId, context) -> {
            if (skillId == null || skillId.isBlank()) return false;
            var optChain = skillRegistry.get(skillId);
            if (optChain.isEmpty()) return false;
            var chain = optChain.get();

            for (var step : chain.mechanics()) {
                if (context != null && context.isCancelled()) return false;

                if (step.delayTicks() > 0) {
                    skillScheduler.schedule(context != null ? context.casterId() : UUID.randomUUID(), step.delayTicks(), 1L, step.repeat(), () -> {
                        if (context == null || !context.isCancelled()) {
                            mechanicRegistry.execute(step.id(), new MechanicContext(context, List.of()), step.parameters());
                        }
                    });
                } else {
                    for (int rep = 0 ; rep < step.repeat() ; rep++) {
                        mechanicRegistry.execute(step.id(), new MechanicContext(context, List.of()), step.parameters());
                    }
                }
            }
            return true;
        });

        dropManager = new DropManager(
                mobRegistry, mobManager,
                HaoHanItemBridge.get(),
                conditionRegistry
        );
        fixedSpawnerManager = new FixedSpawnerManager(mobRegistry, mobManager);
        itemProviderRegistry = new ItemProviderRegistry();
        mobManager.setItemRegistry(itemProviderRegistry);
        damagePipeline.setMobManager(mobManager);
        mobManager.addUnregisterCallback(mob -> {
            UUID entityId = mob.entityId();
            if (auraScheduler != null) auraScheduler.cancelAll(entityId);
            if (projectileTracker != null) projectileTracker.cleanupShooter(entityId);
        });

        LunarAPI.setMobManager(mobManager);
        LunarAPI.setSkillManager(skillRegistry);
        LunarAPI.setCombatManager(damagePipeline);
        LunarAPI.setLootManager(dropManager);
        LunarAPI.setSpawnerManager(fixedSpawnerManager);
        LunarAPI.setItemProvider(itemProviderRegistry);

        this.configRoot = plugin != null ? plugin.getDataFolder().toPath() : Path.of("src/main/resources");
        hotReloadEngine = new HotReloadEngine(configRoot, mobRegistry, skillRegistry, dropManager, fixedSpawnerManager, mobManager, java.util.concurrent.ForkJoinPool.commonPool());

        if (plugin != null && plugin.getServer() != null) {
            var pm = plugin.getServer().getPluginManager();
            pm.registerEvents(mobManager, plugin);
            pm.registerEvents(fixedSpawnerManager, plugin);
            pm.registerEvents(dropManager, plugin);
            pm.registerEvents(mobSkillRuntime, plugin);
        }
    }

    public ConfigValidationReport reload() {
        Path root = configRoot != null ? configRoot : Path.of("src/main/resources");
        var lint = vn.haohan.lunar.core.system.validator.ContentLintTool.lintDirectory(root);
        if (lint.hasErrors()) {
            var issues = lint.issues().stream().map(i -> new ConfigValidationReport.Issue(i.file(), i.field(), i.message(), 0, 0)).toList();
            return new ConfigValidationReport(issues);
        }
        if (hotReloadEngine != null) {
            hotReloadEngine.applyReload(mobRegistry.snapshot().values(), skillRegistry.snapshot().values(), dropManager.snapshot().values(), Collections.emptyList(), lint.issues());
        }
        return new ConfigValidationReport(Collections.emptyList());
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void tick() {
        currentTick++;

        if (currentTick % 20L == 0L) {
            try {
                double[] tps = org.bukkit.Bukkit.getTPS();
                if (tps != null && tps.length > 0) {
                    dynamicThrottlingEngine.updateTPS(tps[0]);
                }
            }
            catch (Throwable ignored) {
            }
        }

        if (projectileTracker != null && projectileTracker.size() > 0) {
            long start = System.nanoTime();
            projectileTracker.tick(currentTick);
            performanceMetrics.record("projectiles", System.nanoTime() - start);
        }

        if (auraScheduler != null) {
            long start = System.nanoTime();
            auraScheduler.tick(currentTick);
            performanceMetrics.record("auras", System.nanoTime() - start);
        }

        if (skillScheduler != null) {
            long start = System.nanoTime();
            skillScheduler.tick();
            performanceMetrics.record("skill_scheduler", System.nanoTime() - start);
        }

        if (fixedSpawnerManager != null && dynamicThrottlingEngine.shouldTickSpawner(currentTick)) {
            long start = System.nanoTime();
            fixedSpawnerManager.tickAll(currentTick);
            performanceMetrics.record("spawners", System.nanoTime() - start);
        }

        if (mobManager != null) {
            if (currentTick % 20L == 0L) {
                mobManager.cleanupInvalidEntities();
            }
            long start = System.nanoTime();
            mobManager.tick(currentTick, dynamicThrottlingEngine);
            performanceMetrics.record("mob_manager", System.nanoTime() - start);
        }

        if (mobSkillRuntime != null) {
            long start = System.nanoTime();
            mobSkillRuntime.tick(currentTick);
            performanceMetrics.record("skill_runtime", System.nanoTime() - start);
        }

        try {
            vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge.tickAll();
        } catch (Throwable ignored) {}

        try {
            vn.haohan.lunar.api.presentation.volatilefx.VolatileVisualEngine.tick(currentTick);
        } catch (Throwable ignored) {}
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        if (skillScheduler != null) {
            skillScheduler.stop();
        }
        if (auraScheduler != null) {
            auraScheduler.clear();
        }
        if (projectileTracker != null) {
            projectileTracker.clear();
        }
        if (mobManager != null) {
            mobManager.cleanupAll();
        }
        try {
            vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge.clearAll();
        } catch (Throwable ignored) {}
        try {
            vn.haohan.lunar.api.presentation.volatilefx.VolatileVisualEngine.clearAll();
        } catch (Throwable ignored) {}
        currentTick = 0L;
    }

    public LunarMobManager mobManager() {
        return mobManager;
    }

    public LunarMobManager getMobManager() {
        return mobManager;
    }

    public MobDefinitionRegistry mobRegistry() {
        return mobRegistry;
    }

    public MobDefinitionRegistry getMobRegistry() {
        return mobRegistry;
    }

    public SkillRegistry skillRegistry() {
        return skillRegistry;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public DamagePipeline damagePipeline() {
        return damagePipeline;
    }

    public DamagePipeline getDamagePipeline() {
        return damagePipeline;
    }

    public ConditionRegistry conditionRegistry() {
        return conditionRegistry;
    }

    public ConditionRegistry getConditionRegistry() {
        return conditionRegistry;
    }

    public MechanicRegistry mechanicRegistry() {
        return mechanicRegistry;
    }

    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public BasicTargeterRegistry targeterRegistry() {
        return targeterRegistry;
    }

    public BasicTargeterRegistry getTargeterRegistry() {
        return targeterRegistry;
    }

    public DropManager dropManager() {
        return dropManager;
    }

    public DropManager getDropManager() {
        return dropManager;
    }

    public FixedSpawnerManager fixedSpawnerManager() {
        return fixedSpawnerManager;
    }

    public FixedSpawnerManager getFixedSpawnerManager() {
        return fixedSpawnerManager;
    }

    public ItemProviderRegistry itemProviderRegistry() {
        return itemProviderRegistry;
    }

    public ItemProviderRegistry getItemProviderRegistry() {
        return itemProviderRegistry;
    }

    public AuraRegistry auraRegistry() {
        return auraRegistry;
    }

    public AuraRegistry getAuraRegistry() {
        return auraRegistry;
    }

    public AuraScheduler auraScheduler() {
        return auraScheduler;
    }

    public AuraScheduler getAuraScheduler() {
        return auraScheduler;
    }

    public ProjectileTracker projectileTracker() {
        return projectileTracker;
    }

    public ProjectileTracker getProjectileTracker() {
        return projectileTracker;
    }

    public VariableManager variableManager() {
        return variableManager;
    }

    public VariableManager getVariableManager() {
        return variableManager;
    }

    public DynamicThrottlingEngine dynamicThrottlingEngine() {
        return dynamicThrottlingEngine;
    }

    public DynamicThrottlingEngine getDynamicThrottlingEngine() {
        return dynamicThrottlingEngine;
    }

    public SkillScheduler skillScheduler() {
        return skillScheduler;
    }

    public SkillScheduler getSkillScheduler() {
        return skillScheduler;
    }

    public MobSkillRuntime skillRuntime() {
        return mobSkillRuntime;
    }

    public MobSkillRuntime getSkillRuntime() {
        return mobSkillRuntime;
    }

    public HotReloadEngine hotReloadEngine() {
        return hotReloadEngine;
    }

    public HotReloadEngine getHotReloadEngine() {
        return hotReloadEngine;
    }

    public PerformanceMetrics performanceMetrics() {
        return performanceMetrics;
    }

    public PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }
}
