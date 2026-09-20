package vn.haohan.lunar.core.command;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Interface representing a modular command in HaoHanLunar,
 * inspired by LavaHack's Command architecture.
 */
public interface LunarCommand {

    String name();

    default String description() {
        return "";
    }

    default String usage() {
        return "/" + name();
    }

    default String permission() {
        return "haohan.admin";
    }

    default List<String> aliases() {
        return Collections.emptyList();
    }

    boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args);

    default List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }
}
