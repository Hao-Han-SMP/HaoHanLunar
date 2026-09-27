package vn.haohan.lunar.core.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.service.IService;
import vn.haohan.lunar.api.service.Services;
import vn.haohan.lunar.api.service.engine.MobCoreService;

import java.util.*;

public interface ICommand extends TabCompleter, CommandExecutor {

    String name();

    default List<String> aliases() {
        return Collections.emptyList();
    }

    default String description() {
        return "";
    }

    default String usage() {
        return "/" + name();
    }

    default String usage(String prefix) {
        return prefix + " " + name();
    }

    default String permission() {
        return "haohan.admin";
    }

    default boolean execute(CommandSender sender, String label, String[] args) {
        return execute(HaoHanLunarPlugin.getInstance(), sender, label, args);
    }

    boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return tabComplete(HaoHanLunarPlugin.getInstance(), sender, name(), args);
    }

    default List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }

    @Override
    default boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(HaoHanLunarPlugin.getInstance(), sender, label, args);
    }

    @Override
    default List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(HaoHanLunarPlugin.getInstance(), sender, alias, args);
    }

    /**
     * Registers this command with the Bukkit CommandMap dynamically.
     */
    default void register(HaoHanLunarPlugin plugin) {
        BukkitCommand bukkitCmd = new BukkitCommand(name()) {
            {
                setDescription(ICommand.this.description());
                setUsage(ICommand.this.usage());
                setAliases(ICommand.this.aliases());
                if (permission() != null && !permission().isBlank()) {
                    setPermission(permission());
                }
            }

            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return ICommand.this.execute(plugin, sender, commandLabel, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return ICommand.this.tabComplete(plugin, sender, alias, args);
            }
        };
        Bukkit.getCommandMap().register("haohan", bukkitCmd);
    }

    /**
     * Strips the subcommand's own name or alias from the first argument if present.
     */
    default String[] stripSubcommand(String[] args) {
        if (args != null && args.length > 0) {
            String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equalsIgnoreCase(name()) || aliases().stream().anyMatch(a -> a.equalsIgnoreCase(first))) {
                return Arrays.copyOfRange(args, 1, args.length);
            }
            return args;
        }
        return new String[0];
    }

    /**
     * Computes the effective usage prefix for display in help and usage messages.
     * Handles /hhl, /hhl mob, direct shortcuts (/hhl spawn), and legacy /lunarmob.
     */
    default String usagePrefix(String label) {
        if (label == null || label.isBlank()) {
            return "/hhl " + name();
        }
        String trimmed = label.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith(" " + name()) || trimmed.equalsIgnoreCase(name())) {
            return "/" + trimmed;
        }
        return "/" + trimmed + " " + name();
    }

    /**
     * Retrieves an active subsystem registered with the plugin.
     */
    default <T extends IService> T getService(Class<T> type) {
        return Services.get(type);
    }

    /**
     * Convenience method to fetch the active MobDefinitionRegistry from MobCoreSubSystem.
     */
    default MobDefinitionRegistry defaultMobRegistry() {
        MobCoreService service = getService(MobCoreService.class);
        return service != null ? service.getMobRegistry() : null;
    }

    /**
     * Convenience method to fetch the active LunarMobManager from MobCoreSubSystem.
     */
    default LunarMobManager defaultMobManager() {
        MobCoreService service = getService(MobCoreService.class);
        return service != null ? service.getMobManager() : null;
    }

    /**
     * Helper to filter and sort tab completion candidates matching the given token.
     */
    default List<String> matchCompletions(String token, Iterable<String> candidates) {
        if (candidates == null) return Collections.emptyList();
        List<String> matches = StringUtil.copyPartialMatches(token != null ? token : "", candidates, new ArrayList<>());
        Collections.sort(matches);
        return matches;
    }

    /**
     * Helper to tab complete registered mob definition IDs.
     */
    default List<String> tabCompleteMobIds(String token, MobDefinitionRegistry registry) {
        MobDefinitionRegistry reg = (registry != null) ? registry : defaultMobRegistry();
        if (reg == null) return Collections.emptyList();
        return matchCompletions(token, reg.snapshot().keySet());
    }
}
