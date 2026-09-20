package vn.haohan.lunar.core.subsystem.engine;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.LunarAPI;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.integration.bridge.item.HaoHanItemBridge;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.equipment.ItemProviderRegistry;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.target.TargeterRegistry;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.core.command.LunarCommands;
import vn.haohan.lunar.core.command.MobCommand;
import vn.haohan.lunar.core.command.commands.LunarMobSubsystemCommand;
import vn.haohan.lunar.core.subsystem.mob.MobManager;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

import java.util.Collections;

/**
 * SubSystem responsible for initializing the core mob runtime, skill engine, combat pipeline,
 * and binding them to the public {@link LunarAPI} facade.
 */
public final class MobCoreSubSystem implements LunarSubSystem {

    private MobManager mobManager;
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
        mobManager = new MobManager();
        mobRegistry = new MobDefinitionRegistry();
        skillRegistry = new SkillRegistry();
        damagePipeline = new DamagePipeline();
        conditionRegistry = new ConditionRegistry();
        mechanicRegistry = new MechanicRegistry();
        targeterRegistry = new TargeterRegistry();
        skillRegistry.setRegistries(mechanicRegistry, conditionRegistry, targeterRegistry);

        dropManager = new DropManager(
                mobRegistry, mobManager,
                HaoHanItemBridge.get(),
                conditionRegistry
        );
        fixedSpawnerManager = new FixedSpawnerManager(mobRegistry, mobManager);
        itemProviderRegistry = new ItemProviderRegistry();

        // Publish to LunarAPI
        LunarAPI.setMobManager(mobManager);
        LunarAPI.setSkillManager(skillRegistry);
        LunarAPI.setCombatManager(damagePipeline);
        LunarAPI.setLootManager(dropManager);
        LunarAPI.setSpawnerManager(fixedSpawnerManager);
        LunarAPI.setItemProvider(itemProviderRegistry);

        // Command facade
        mobCommand = new MobCommand(
                mobRegistry,
                mobManager,
                (player, def) -> true,
                () -> new ConfigValidationReport(Collections.emptyList()),
                (mob, sig) -> {}
        );
        LunarCommands.register(new LunarMobSubsystemCommand(mobCommand));
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        // Clear runtime state
        if (mobManager != null) {
            mobManager.cleanupAll();
        }
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public MobDefinitionRegistry getMobRegistry() {
        return mobRegistry;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public DamagePipeline getDamagePipeline() {
        return damagePipeline;
    }

    public ConditionRegistry getConditionRegistry() {
        return conditionRegistry;
    }

    public MechanicRegistry getMechanicRegistry() {
        return mechanicRegistry;
    }

    public TargeterRegistry getTargeterRegistry() {
        return targeterRegistry;
    }

    public DropManager getDropManager() {
        return dropManager;
    }

    public FixedSpawnerManager getFixedSpawnerManager() {
        return fixedSpawnerManager;
    }

    public ItemProviderRegistry getItemProviderRegistry() {
        return itemProviderRegistry;
    }

    public MobCommand getMobCommand() {
        return mobCommand;
    }
}
