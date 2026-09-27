package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.core.features.boss.warden.WardenSpawner;
import vn.haohan.lunar.core.features.boss.warden.showcase.WardenShowcaseHandler;
import vn.haohan.lunar.core.service.LunarServices;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class WardenCommand implements ICommand {

    private final BgmCommand bgmCommand;

    public WardenCommand() {
        this(new BgmCommand());
    }

    public WardenCommand(BgmCommand bgmCommand) {
        this.bgmCommand = bgmCommand;
    }

    @Override
    public String name() {
        return "warden";
    }

    @Override
    public List<String> aliases() {
        return List.of("spawnwarden", "clearwarden", "wardenshowcase");
    }

    @Override
    public String description() {
        return "Quản trị Boss Warden";
    }

    @Override
    public String usage() {
        return "/hhl warden <spawn|showcase|clear|bgm>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (label != null) {
            String lowerLabel = label.toLowerCase(Locale.ROOT);
            if (lowerLabel.equals("clearwarden") || lowerLabel.equals("wardenclear") || lowerLabel.equals("cleardummy") || lowerLabel.equals("killwarden")) {
                return executeClear(plugin, sender);
            }
            if (lowerLabel.equals("spawnwarden") || lowerLabel.equals("wardenspawn")) {
                return executeSpawn(plugin, sender);
            }
            if (lowerLabel.equals("wardenshowcase") || lowerLabel.equals("showcasewarden")) {
                return executeShowcase(plugin, sender);
            }
        }

        String sub = (args != null && args.length > 0) ? args[0].toLowerCase(Locale.ROOT) : "spawn";

        return switch (sub) {
            case "spawn" -> executeSpawn(plugin, sender);
            case "showcase", "dummy" -> executeShowcase(plugin, sender);
            case "clear" -> executeClear(plugin, sender);
            case "bgm" -> {
                String[] bgmArgs = (args != null && args.length > 1) ? Arrays.copyOfRange(args, 1, args.length) : new String[0];
                yield bgmCommand.execute(plugin, sender, "hhl bgm", bgmArgs);
            }
            default -> {
                sender.sendMessage("§cSử dụng: §e/hhl warden <spawn|showcase|clear|bgm>");
                yield true;
            }
        };
    }

    private boolean executeSpawn(HaoHanLunarPlugin plugin, CommandSender sender) {
        LunarWardenMechanic wardenMechanic = getMechanic(plugin);
        if (wardenMechanic == null) {
            sender.sendMessage("§cHệ thống Lunar Warden chưa sẵn sàng.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể spawn boss.");
            return true;
        }

        WardenSpawner.spawnWarden(plugin, wardenMechanic, player.getLocation(), player);
        sender.sendMessage("§aĐã triệu hồi Lunar Warden!");
        return true;
    }

    private boolean executeShowcase(HaoHanLunarPlugin plugin, CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể gọi showcase dummy.");
            return true;
        }

        LunarWardenMechanic wardenMechanic = getMechanic(plugin);
        if (wardenMechanic == null) {
            sender.sendMessage("§cHệ thống Lunar Warden chưa sẵn sàng.");
            return true;
        }

        var dummies = WardenShowcaseHandler.spawnShowcaseLine(plugin, wardenMechanic, player.getLocation(), 15.0, player);
        sender.sendMessage("§aĐã spawn Warden Showcase Dummy! (§e" + (dummies != null ? dummies.size() : 0) + "§a dummies)");
        return true;
    }

    private boolean executeClear(HaoHanLunarPlugin plugin, CommandSender sender) {
        LunarWardenMechanic wardenMechanic = getMechanic(plugin);
        if (wardenMechanic == null) {
            sender.sendMessage("§cHệ thống Lunar Warden chưa sẵn sàng.");
            return true;
        }

        int cleared = wardenMechanic.clearAllWardens();
        sender.sendMessage("§aĐã dọn dẹp toàn bộ Boss & Dummy Warden! (§e" + cleared + "§a thực thể đã bị loại bỏ)");
        return true;
    }

    private LunarWardenMechanic getMechanic(HaoHanLunarPlugin plugin) {
        LunarWardenMechanic wardenMechanic = LunarServices.get(LunarWardenMechanic.class);
        if (wardenMechanic == null && plugin != null) {
            wardenMechanic = plugin.getLunarWardenMechanic();
        }
        return wardenMechanic;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        if (args.length <= 1) {
            String lower = (args.length == 1) ? args[0].toLowerCase(Locale.ROOT) : "";
            return Stream.of("spawn", "showcase", "clear", "bgm")
                    .filter(s -> s.startsWith(lower))
                    .toList();
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("bgm")) {
            String[] bgmArgs = Arrays.copyOfRange(args, 1, args.length);
            return bgmCommand.tabComplete(plugin, sender, "bgm", bgmArgs);
        }
        return List.of();
    }
}
