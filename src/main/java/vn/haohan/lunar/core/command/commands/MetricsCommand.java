package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MetricsCommand implements ICommand {

    private final PerformanceMetrics metrics;

    public MetricsCommand(PerformanceMetrics metrics) {
        this.metrics = metrics != null ? metrics : new PerformanceMetrics();
    }

    public MetricsCommand() {
        this(new PerformanceMetrics());
    }

    @Override
    public String name() {
        return "metrics";
    }

    @Override
    public String description() {
        return "Báo cáo hiệu năng và tải của các subsystem";
    }

    @Override
    public String usage() {
        return "/hhl metrics [system]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);

        String system = (effectiveArgs.length > 0) ? effectiveArgs[0] : "all";
        for (String line : metrics.formatReport(system).split("\n")) {
            sender.sendMessage(line);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length <= 1) {
            String input = effectiveArgs.length == 1 ? effectiveArgs[0] : "";
            List<String> matches = StringUtil.copyPartialMatches(
                    input, List.of("all", "SkillScheduler", "ProjectileTracker", "ISpawnerManager"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return List.of();
    }

    public PerformanceMetrics metrics() {
        return metrics;
    }
}
