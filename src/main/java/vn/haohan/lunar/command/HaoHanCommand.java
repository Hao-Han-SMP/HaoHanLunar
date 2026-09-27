package vn.haohan.lunar.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.command.commands.*;

import java.util.*;

/**
 * Root command handler for HaoHan Lunar (`/hhl` and its alias `/haohanlunar`).
 * Dispatches all subcommands directly and provides unified native Bukkit tab completion.
 */
public class HaoHanCommand implements ICommand {

    public static final String PERMISSION = "haohan.admin";
    public static final String ADMIN_PERMISSION = "haohan.lunarmob.admin";

    private final Map<String, ICommand> commands = new LinkedHashMap<>();
    private final Map<String, ICommand> aliasMap = new HashMap<>();

    public HaoHanCommand() {
        registerCommand(new ReloadCommand());
        registerCommand(new PackCommand());
        registerCommand(new TeleportCommand());
        registerCommand(new WardenCommand());
        registerCommand(new BgmCommand());
        registerCommand(new SpawnCommand());
        registerCommand(new KillCommand());
        registerCommand(new InfoCommand());
        registerCommand(new SignalCommand());
        registerCommand(new ItemCommand());
        registerCommand(new PinsCommand());
        registerCommand(new ValidateCommand());
        registerCommand(new TraceCommand());
        registerCommand(new MetricsCommand());
    }

    public void registerCommand(ICommand command) {
        if (command == null) return;
        commands.put(command.name().toLowerCase(Locale.ROOT), command);
        aliasMap.put(command.name().toLowerCase(Locale.ROOT), command);
        for (String alias : command.aliases()) {
            aliasMap.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public void unregisterCommand(String name) {
        if (name == null) return;
        ICommand cmd = commands.remove(name.toLowerCase(Locale.ROOT));
        if (cmd != null) {
            aliasMap.remove(cmd.name().toLowerCase(Locale.ROOT));
            for (String alias : cmd.aliases()) {
                aliasMap.remove(alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    public Optional<ICommand> getCommand(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(aliasMap.get(name.toLowerCase(Locale.ROOT)));
    }

    public Map<String, ICommand> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    @Override
    public String name() {
        return "hhl";
    }

    @Override
    public String description() {
        return "Lệnh quản trị chính cho plugin HaoHan Lunar";
    }

    @Override
    public String usage() {
        return "/hhl <subcommand> [args]";
    }

    @Override
    public String permission() {
        return PERMISSION;
    }

    @Override
    public List<String> aliases() {
        List<String> list = new ArrayList<>();
        list.add("haohanlunar");
        list.add("mob");
        list.add("lunarmob");
        list.add("lmob");
        for (ICommand cmd : commands.values()) {
            for (String alias : cmd.aliases()) {
                if (!list.contains(alias)) {
                    list.add(alias);
                }
            }
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage("§cBạn không có quyền sử dụng lệnh này!");
            return true;
        }

        String subKey;
        String[] subArgs;

        if (label != null && !label.equalsIgnoreCase("hhl") && !label.equalsIgnoreCase("haohanlunar")) {
            if (label.equalsIgnoreCase("mob") || label.equalsIgnoreCase("lunarmob") || label.equalsIgnoreCase("lmob")) {
                if (args == null || args.length == 0) {
                    sendHelp(sender, label);
                    return true;
                }
                subKey = args[0].toLowerCase(Locale.ROOT);
                subArgs = Arrays.copyOfRange(args, 1, args.length);
            } else {
                subKey = label.toLowerCase(Locale.ROOT);
                subArgs = (args == null) ? new String[0] : args;
            }
        } else {
            if (args == null || args.length == 0) {
                sendHelp(sender, label);
                return true;
            }
            subKey = args[0].toLowerCase(Locale.ROOT);
            subArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        if (subKey.equals("help")) {
            sendHelp(sender, label);
            return true;
        }

        if (subKey.equals("mob") || subKey.equals("lunarmob") || subKey.equals("lmob")) {
            if (subArgs.length > 0) {
                String nestedSubKey = subArgs[0].toLowerCase(Locale.ROOT);
                String[] nestedArgs = Arrays.copyOfRange(subArgs, 1, subArgs.length);
                ICommand nestedCmd = aliasMap.get(nestedSubKey);
                if (nestedCmd != null) {
                    return nestedCmd.execute(plugin, sender, "hhl " + nestedSubKey, nestedArgs);
                }
            }
            sendHelp(sender, label);
            return true;
        }

        ICommand cmd = aliasMap.get(subKey);
        if (cmd == null) {
            sender.sendMessage("§cLệnh con không hợp lệ: §e" + subKey);
            sendHelp(sender, label);
            return true;
        }

        return cmd.execute(plugin, sender, "hhl " + subKey, subArgs);
    }

    private void sendHelp(CommandSender sender, String label) {
        String base = (label != null && !label.isBlank()) ? "/" + label : "/hhl";
        sender.sendMessage("§6================ §bHaoHan Lunar Command §6================");
        for (ICommand cmd : commands.values()) {
            sender.sendMessage("§e" + base + " " + cmd.name() + " §7- " + cmd.description());
        }
        sender.sendMessage("§6=========================================================");
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        if (!hasPermission(sender)) {
            return List.of();
        }

        if (args == null || args.length <= 1) {
            String input = (args != null && args.length == 1) ? args[0] : "";
            Set<String> options = new LinkedHashSet<>(commands.keySet());
            for (ICommand cmd : commands.values()) {
                options.addAll(cmd.aliases());
            }
            options.add("help");
            options.add("mob");
            List<String> matches = StringUtil.copyPartialMatches(input, options, new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }

        String subKey = args[0].toLowerCase(Locale.ROOT);
        if (subKey.equals("mob") || subKey.equals("lunarmob") || subKey.equals("lmob")) {
            if (args.length == 2) {
                Set<String> mobOptions = new LinkedHashSet<>(commands.keySet());
                List<String> matches = StringUtil.copyPartialMatches(args[1], mobOptions, new ArrayList<>());
                Collections.sort(matches);
                return matches;
            } else if (args.length > 2) {
                String nestedSubKey = args[1].toLowerCase(Locale.ROOT);
                ICommand nestedCmd = aliasMap.get(nestedSubKey);
                if (nestedCmd != null) {
                    String[] nestedArgs = Arrays.copyOfRange(args, 2, args.length);
                    return nestedCmd.tabComplete(plugin, sender, nestedSubKey, nestedArgs);
                }
            }
            return List.of();
        }

        ICommand cmd = aliasMap.get(subKey);
        if (cmd != null) {
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            return cmd.tabComplete(plugin, sender, subKey, subArgs);
        }

        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return execute(null, sender, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tabComplete(null, sender, alias, args);
    }

    private boolean hasPermission(CommandSender sender) {
        if (sender == null) return true;
        try {
            if (sender.isOp()) return true;
        } catch (Throwable ignored) {
        }
        try {
            return sender.hasPermission(PERMISSION) || sender.hasPermission(ADMIN_PERMISSION);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
