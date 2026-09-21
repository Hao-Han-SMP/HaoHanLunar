package vn.haohan.lunar.core.subsystem.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.LunarAPI;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.equipment.ItemProviderRegistry;
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
import vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.core.command.LunarCommands;
import vn.haohan.lunar.core.command.MobCommand;
import vn.haohan.lunar.core.command.commands.LunarMobSubsystemCommand;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;
import vn.haohan.lunar.core.subsystem.mob.MobSkillRuntime;
import vn.haohan.lunar.core.system.throttle.DynamicThrottlingEngine;
import vn.haohan.lunar.core.system.variable.VariableManager;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Subsystem initializing the mob runtime, skill engine, combat pipeline,
 * auras, projectiles, and variables, then binding them to the public {@link LunarAPI} service locator.
 */
public final class MobCoreSubSystem implements LunarSubSystem {

    private LunarMobManager mobManager;
    private MobDefinitionRegistry mobRegistry;
    private SkillRegistry skillRegistry;
    private DamagePipeline damagePipeline;
    private ConditionRegistry conditionRegistry;
    private MechanicRegistry mechanicRegistry;
    private TargeterRegistry targeterRegistry;
    private DropManager dropManager;
    private FixedSpawnerManager fixedSpawnerManager;
    private ItemProviderRegistry itemProviderRegistry;
    private MobCommand mobCommand;
    private final vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics performanceMetrics = new vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics();
    private vn.haohan.lunar.core.system.config.reload.HotReloadEngine hotReloadEngine;

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
        targeterRegistry = new TargeterRegistry();
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

        // Wire dependency injection into registries
        conditionRegistry.setAuraScheduler(auraScheduler);
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

        // Publish to LunarAPI
        LunarAPI.setMobManager(mobManager);
        LunarAPI.setSkillManager(skillRegistry);
        LunarAPI.setCombatManager(damagePipeline);
        LunarAPI.setLootManager(dropManager);
        LunarAPI.setSpawnerManager(fixedSpawnerManager);
        LunarAPI.setItemProvider(itemProviderRegistry);

        java.nio.file.Path configRoot = plugin != null ? plugin.getDataFolder().toPath() : java.nio.file.Path.of("src/main/resources");
        hotReloadEngine = new vn.haohan.lunar.core.system.config.reload.HotReloadEngine(configRoot, mobRegistry, skillRegistry, dropManager, fixedSpawnerManager, mobManager, java.util.concurrent.ForkJoinPool.commonPool());

        // Command facade
        mobCommand = new MobCommand(
                mobRegistry,
                mobManager,
                (player, def) -> true, () -> {
            var lint = vn.haohan.lunar.core.system.validator.ContentLintTool.lintDirectory(configRoot);
            if (lint.hasErrors()) {
                var issues = lint.issues().stream().map(i -> new ConfigValidationReport.Issue(i.file(), i.field(), i.message(), 0, 0)).toList();
                return new ConfigValidationReport(issues);
            }
            hotReloadEngine.applyReload(mobRegistry.snapshot().values(), skillRegistry.snapshot().values(), dropManager.snapshot().values(), Collections.emptyList(), lint.issues());
            return new ConfigValidationReport(Collections.emptyList());
        }, (mob, sig) -> {
        }, new vn.haohan.lunar.core.system.debug.validator.ConfigValidationService(), new vn.haohan.lunar.core.system.debug.trace.SkillTracer(), performanceMetrics, configRoot
        );
        LunarCommands.register(new LunarMobSubsystemCommand(mobCommand));

        // Register event listeners
        if (plugin != null && plugin.getServer() != null) {
            var pm = plugin.getServer().getPluginManager();
            pm.registerEvents(mobManager, plugin);
            pm.registerEvents(fixedSpawnerManager, plugin);
            pm.registerEvents(dropManager, plugin);
            pm.registerEvents(mobSkillRuntime, plugin);
        }
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void tick() {
        currentTick++;

        // 1. Update TPS sample periodically (every 20 ticks = 1 sec)
        if (currentTick % 20L == 0L) {
            try {
                double[] tps = org.bukkit.Bukkit.getTPS();
                if (tps != null && tps.length > 0) {
                    dynamicThrottlingEngine.updateTPS(tps[0]);
                }
            }
            catch (Throwable ignored) {
                // Ignore in unit test mock environments
            }
        }

        // 2. Tick in-flight projectiles
        if (projectileTracker != null && projectileTracker.size() > 0) {
            long start = System.nanoTime();
            projectileTracker.tick(currentTick);
            performanceMetrics.record("projectiles", System.nanoTime() - start);
        }

        // 3. Tick active auras
        if (auraScheduler != null) {
            long start = System.nanoTime();
            auraScheduler.tick(currentTick);
            performanceMetrics.record("auras", System.nanoTime() - start);
        }

        // 4. Tick delayed/repeated skill execution
        if (skillScheduler != null) {
            long start = System.nanoTime();
            skillScheduler.tick();
            performanceMetrics.record("skill_scheduler", System.nanoTime() - start);
        }

        // 5. Tick fixed spawners with LOD throttling
        if (fixedSpawnerManager != null && dynamicThrottlingEngine.shouldTickSpawner(currentTick)) {
            long start = System.nanoTime();
            fixedSpawnerManager.tickAll(currentTick);
            performanceMetrics.record("spawners", System.nanoTime() - start);
        }

        // 6. Cleanup and LOD tick for active mobs
        if (mobManager != null) {
            if (currentTick % 20L == 0L) {
                mobManager.cleanupInvalidEntities();
            }
            long start = System.nanoTime();
            mobManager.tick(currentTick, dynamicThrottlingEngine);
            performanceMetrics.record("mob_manager", System.nanoTime() - start);
        }

        // 7. Tick mob skill triggers (ON_TIMER, ON_COMBAT)
        if (mobSkillRuntime != null) {
            long start = System.nanoTime();
            mobSkillRuntime.tick(currentTick);
            performanceMetrics.record("skill_runtime", System.nanoTime() - start);
        }
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        if (skillScheduler != null) {
            skillScheduler.stop();
        }
        if (projectileTracker != null) {
            projectileTracker.clear();
        }
        if (auraScheduler != null) {
            auraScheduler.clear();
        }
        if (mobManager != null) {
            mobManager.cleanupAll();
        }
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

    public TargeterRegistry targeterRegistry() {
        return targeterRegistry;
    }

    public TargeterRegistry getTargeterRegistry() {
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

    public MobCommand mobCommand() {
        return mobCommand;
    }

    public MobCommand getMobCommand() {
        return mobCommand;
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

    public vn.haohan.lunar.core.system.config.reload.HotReloadEngine hotReloadEngine() {
        return hotReloadEngine;
    }

    public vn.haohan.lunar.core.system.config.reload.HotReloadEngine getHotReloadEngine() {
        return hotReloadEngine;
    }

    public vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics performanceMetrics() {
        return performanceMetrics;
    }

    public vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }
}
