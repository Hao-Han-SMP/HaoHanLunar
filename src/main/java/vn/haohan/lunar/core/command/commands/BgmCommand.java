package vn.haohan.lunar.core.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.features.boss.warden.ui.WardenBGMManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BgmCommand implements ICommand {

    @Override
    public String name() {
        return "bgm";
    }

    @Override
    public String description() {
        return "Quản lý nhạc nền Boss";
    }

    @Override
    public String usage() {
        return "/hhl bgm <play|stop|list> [soundKey] [player]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (args == null || args.length == 0) {
            sender.sendMessage("§6================ §bWarden BGM Manager §6================");
            sender.sendMessage("§e/hhl bgm play <soundKey> [player] §7- Phát nhạc nền chỉ định");
            sender.sendMessage("§e/hhl bgm stop [player] §7- Dừng tất cả nhạc nền đang phát");
            sender.sendMessage("§e/hhl bgm list §7- Liệt kê các sound ID nhạc nền khả dụng");
            sender.sendMessage("§6=========================================================");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage("§6Danh sách BGM Sound IDs khả dụng:");
                for (String key : WardenBGMManager.getCandidateBgmKeys()) {
                    sender.sendMessage(" §7- §a" + key);
                }
                return true;
            }
            case "play" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cSử dụng: §e/hhl bgm play <soundKey> [player]");
                    return true;
                }
                String soundKey = args[1];
                Player target = null;
                if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage("§cKhông tìm thấy người chơi: " + args[2]);
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("§cConsole phải chỉ định người chơi: /hhl bgm play <soundKey> <player>");
                    return true;
                }

                boolean success = WardenBGMManager.playCustomBGM(target, soundKey);
                if (success) {
                    sender.sendMessage("§aĐang phát BGM §e" + soundKey + "§a cho §f" + target.getName());
                } else {
                    sender.sendMessage("§cKhông thể phát âm thanh này!");
                }
                return true;
            }
            case "stop" -> {
                Player target = null;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage("§cKhông tìm thấy người chơi: " + args[1]);
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("§cConsole phải chỉ định người chơi: /hhl bgm stop <player>");
                    return true;
                }

                WardenBGMManager.stopAllBGM(target);
                sender.sendMessage("§aĐã dừng BGM cho §f" + target.getName());
                return true;
            }
            default -> {
                sender.sendMessage("§cSử dụng: §e/hhl bgm <play|stop|list>");
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        if (args.length <= 1) {
            String token = (args.length == 1) ? args[0] : "";
            return StringUtil.copyPartialMatches(token, List.of("play", "stop", "list"), new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            return StringUtil.copyPartialMatches(args[1], Arrays.asList(WardenBGMManager.getCandidateBgmKeys()), new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return null; // suggest players
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            return null; // suggest players
        }
        return List.of();
    }
}
