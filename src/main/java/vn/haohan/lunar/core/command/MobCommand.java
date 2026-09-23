package vn.haohan.lunar.core.command;

import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.command.LunarMobCommand;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.core.system.debug.trace.SkillTracer;
import vn.haohan.lunar.core.system.debug.validator.ConfigValidationService;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Command facade alias for LunarMobCommand.
 */
public class MobCommand extends LunarMobCommand {

    public MobCommand(MobDefinitionRegistry definitions, LunarMobManager manager,
                      MobSpawner spawner, Supplier<ConfigValidationReport> reloader,
                      BiConsumer<ActiveMob, String> signalHandler,
                      ConfigValidationService validationService,
                      SkillTracer tracer,
                      PerformanceMetrics metrics,
                      Path configRoot) {
        super(definitions, manager, spawner, reloader, signalHandler, validationService, tracer, metrics, configRoot);
    }

    public MobCommand(MobDefinitionRegistry definitions, LunarMobManager manager,
                      MobSpawner spawner, Supplier<ConfigValidationReport> reloader,
                      BiConsumer<ActiveMob, String> signalHandler) {
        super(definitions, manager, spawner, reloader, signalHandler);
    }
}
