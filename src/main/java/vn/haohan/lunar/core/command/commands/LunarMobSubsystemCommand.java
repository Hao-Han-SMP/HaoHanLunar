package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.LunarCommand;
import vn.haohan.lunar.core.command.MobCommand;

import java.util.List;

public class LunarMobSubsystemCommand implements LunarCommand {

    private final MobCommand mobCommand;

    public LunarMobSubsystemCommand(MobCommand mobCommand) {
        this.mobCommand = mobCommand;
    }

    @Override
    public String name() {
        return "lunarmob";
    }

    @Override
    public String description() {
        return "Administrative command facade for configured mobs and custom items.";
    }

    @Override
    public String permission() {
        return MobCommand.PERMISSION;
    }

    @Override
    public List<String> aliases() {
        return List.of("lmob");
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        return mobCommand.onCommand(sender, null, label, args);
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        return mobCommand.onTabComplete(sender, null, alias, args);
    }

    public MobCommand getMobCommand() {
        return mobCommand;
    }
}
