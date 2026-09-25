package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.system.debug.trace.SkillTracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TraceCommand implements ICommand {

    private final LunarMobManager manager;
    private final SkillTracer tracer;

    public TraceCommand(LunarMobManager manager, SkillTracer tracer) {
        this.manager = manager;
        this.tracer = tracer != null ? tracer : new SkillTracer();
    }

    public TraceCommand() {
        this(new LunarMobManager(), new SkillTracer());
    }

    @Override
    public String name() {
        return "trace";
    }

    @Override
    public String description() {
        return "Theo dõi quá trình thi triển kỹ năng của quái vật";
    }

    @Override
    public String usage() {
        return "/hhl trace <uuid|target> [on|off]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <uuid|target> [on|off]");
            return true;
        }

        UUID targetMobId;
        if (effectiveArgs[0].equalsIgnoreCase("target")) {
            if (sender instanceof Player player) {
                Entity target = player.getTargetEntity(20);
                if (target != null && manager != null && manager.get(target.getUniqueId()) != null) {
                    targetMobId = target.getUniqueId();
                } else {
                    sender.sendMessage("§cNo active Lunar mob targeted within 20 blocks.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players can use 'target' option.");
                return true;
            }
        } else {
            try {
                targetMobId = UUID.fromString(effectiveArgs[0]);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid UUID format: " + effectiveArgs[0]);
                return true;
            }
        }

        boolean enable = true;
        if (effectiveArgs.length > 1) {
            enable = !effectiveArgs[1].equalsIgnoreCase("off");
        }

        if (enable) {
            tracer.enableTrace(targetMobId);
            sender.sendMessage("§aTracing ENABLED for mob " + targetMobId);
        } else {
            tracer.disableTrace(targetMobId);
            sender.sendMessage("§cTracing DISABLED for mob " + targetMobId);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length == 2) {
            List<String> matches = StringUtil.copyPartialMatches(
                    effectiveArgs[1], List.of("on", "off"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return List.of();
    }

    public SkillTracer tracer() {
        return tracer;
    }
}
