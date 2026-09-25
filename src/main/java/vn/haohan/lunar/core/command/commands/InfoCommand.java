package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.Collections;
import java.util.List;

public class InfoCommand implements ICommand {

    private final MobDefinitionRegistry definitions;
    private final LunarMobManager manager;

    public InfoCommand(MobDefinitionRegistry definitions, LunarMobManager manager) {
        this.definitions = definitions;
        this.manager = manager;
    }

    public InfoCommand() {
        this(null, null);
    }

    private MobDefinitionRegistry getRegistry() {
        return definitions != null ? definitions : defaultMobRegistry();
    }

    private LunarMobManager getManager() {
        return manager != null ? manager : defaultMobManager();
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public String description() {
        return "Xem thông tin chi tiết của quái vật";
    }

    @Override
    public String usage() {
        return "/hhl info <id>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <id>");
            return true;
        }

        MobDefinitionRegistry reg = getRegistry();
        MobDefinition def = reg != null ? reg.get(effectiveArgs[0]).orElse(null) : null;
        if (def == null) {
            sender.sendMessage("§cUnknown mob ID: " + effectiveArgs[0]);
            return true;
        }

        LunarMobManager mgr = getManager();
        long activeCount = (mgr != null)
                ? mgr.getActiveMobs().stream()
                .filter(m -> m.definition().id().equals(def.id()))
                .count()
                : 0L;

        sender.sendMessage("§6--- Mob Info: §e" + def.id() + " §6---");
        sender.sendMessage("§7Entity Type: §f" + def.entityType());
        sender.sendMessage("§7Display Name: §f" + def.displayName());
        sender.sendMessage("§7Model ID: §f" + def.modelId().orElse("default"));
        sender.sendMessage("§7Drop Table: §f" + def.dropTableReference().orElse("none"));
        sender.sendMessage("§7Skills: §f" + def.skillReferences().size());
        sender.sendMessage("§7Active in world: §a" + activeCount);
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length == 1) {
            return tabCompleteMobIds(effectiveArgs[0], getRegistry());
        }
        return Collections.emptyList();
    }
}
