package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

public class SignalCommand implements ICommand {

    private final LunarMobManager manager;
    private final BiConsumer<ActiveMob, String> signalHandler;
    private final MobDefinitionRegistry definitions;

    public SignalCommand(LunarMobManager manager, BiConsumer<ActiveMob, String> signalHandler, MobDefinitionRegistry definitions) {
        this.manager = manager;
        this.signalHandler = signalHandler;
        this.definitions = definitions;
    }

    public SignalCommand() {
        this(null, null, null);
    }

    private LunarMobManager getManager() {
        return manager != null ? manager : defaultMobManager();
    }

    private MobDefinitionRegistry getRegistry() {
        return definitions != null ? definitions : defaultMobRegistry();
    }

    @Override
    public String name() {
        return "signal";
    }

    @Override
    public String description() {
        return "Gửi tín hiệu kích hoạt kỹ năng cho quái vật";
    }

    @Override
    public String usage() {
        return "/hhl signal <id> <signal>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 2) {
            sender.sendMessage("§eUsage: " + p + " <id> <signal>");
            return true;
        }

        MobDefinitionRegistry reg = getRegistry();
        MobDefinition def = (reg != null) ? reg.get(effectiveArgs[0]).orElse(null) : null;
        if (def == null) {
            sender.sendMessage("§cUnknown mob ID: " + effectiveArgs[0]);
            return true;
        }

        String signal = effectiveArgs[1];
        LunarMobManager mgr = getManager();
        if (mgr != null && signalHandler != null) {
            mgr.getActiveMobs().stream()
                    .filter(m -> m.definition().id().equals(def.id()))
                    .forEach(m -> signalHandler.accept(m, signal));
        }

        sender.sendMessage("§aSent signal " + signal + " to " + def.id());
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
