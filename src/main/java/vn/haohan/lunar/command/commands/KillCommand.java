package vn.haohan.lunar.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.command.ICommand;
import vn.haohan.engine.core.mob.MobManager;

import java.util.Collections;
import java.util.List;

public class KillCommand implements ICommand {

    private final MobManager manager;
    private final MobDefinitionRegistry definitions;

    public KillCommand(MobManager manager, MobDefinitionRegistry definitions) {
        this.manager = manager;
        this.definitions = definitions;
    }

    public KillCommand() {
        this(null, null);
    }

    private MobManager getManager() {
        return manager != null ? manager : defaultMobManager();
    }

    private MobDefinitionRegistry getRegistry() {
        return definitions != null ? definitions : defaultMobRegistry();
    }

    @Override
    public String name() {
        return "kill";
    }

    @Override
    public String description() {
        return "Loại bỏ quái vật theo ID";
    }

    @Override
    public String usage() {
        return "/hhl kill <id>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <id>");
            return true;
        }

        MobManager mgr = getManager();
        if (mgr == null) {
            sender.sendMessage("§cMob manager not available.");
            return true;
        }

        String target = effectiveArgs[0];
        if (target.equalsIgnoreCase("all")) {
            int count = mgr.getActiveMobs().size();
            mgr.cleanupAll();
            sender.sendMessage("§aRemoved " + count + " mob(s): all");
            return true;
        }

        MobDefinitionRegistry reg = getRegistry();
        if (reg != null && reg.get(target).isEmpty()) {
            sender.sendMessage("§cUnknown mob ID: " + target);
            return true;
        }

        final int[] count = {0};
        mgr.getActiveMobs().stream()
                .filter(mob -> mob.definition().id().toString().equalsIgnoreCase(target))
                .toList()
                .forEach(mob -> {
                    mgr.unregister(mob.entityId());
                    count[0]++;
                });

        sender.sendMessage("§aRemoved " + count[0] + " mob(s): " + target);
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length == 1) {
            List<String> list = new java.util.ArrayList<>(tabCompleteMobIds(effectiveArgs[0], getRegistry()));
            if ("all".startsWith(effectiveArgs[0].toLowerCase())) {
                list.add("all");
            }
            return list;
        }
        return Collections.emptyList();
    }
}
