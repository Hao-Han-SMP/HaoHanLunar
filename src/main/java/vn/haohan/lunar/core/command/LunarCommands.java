package vn.haohan.lunar.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.*;

/**
 * Central registry for LunarCommands inspired by LavaHack's Commands registry.
 * Dynamically binds commands to Bukkit's CommandMap.
 */
public final class LunarCommands {

    private static final Map<String, LunarCommand> COMMANDS = new LinkedHashMap<>();

    private LunarCommands() {}

    public static void register(LunarCommand command) {
        COMMANDS.put(command.name().toLowerCase(Locale.ROOT), command);
    }

    public static void init(HaoHanLunarPlugin plugin) {
        for (LunarCommand cmd : COMMANDS.values()) {
            BukkitCommand bukkitCmd = new BukkitCommand(cmd.name()) {
                {
                    setDescription(cmd.description());
                    setUsage(cmd.usage());
                    setAliases(cmd.aliases());
                    if (cmd.permission() != null && !cmd.permission().isBlank()) {
                        setPermission(cmd.permission());
                    }
                }

                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    return cmd.execute(plugin, sender, commandLabel, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return cmd.tabComplete(plugin, sender, alias, args);
                }
            };
            Bukkit.getCommandMap().register("haohan", bukkitCmd);
        }
    }

    public static List<LunarCommand> getCommands() {
        return new ArrayList<>(COMMANDS.values());
    }

    public static void clear() {
        COMMANDS.clear();
    }
}
