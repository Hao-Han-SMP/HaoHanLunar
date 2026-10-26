package vn.haohan.lunar.command.commands;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.command.ICommand;
import vn.haohan.lunar.robot.LunarRobotEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RobotCommand implements ICommand {

    @Override
    public String name() {
        return "robot";
    }

    @Override
    public List<String> aliases() {
        return List.of("quadruped");
    }

    @Override
    public String description() {
        return "Quản trị Robot 4 Chân";
    }

    @Override
    public String usage() {
        return "/hhl robot <spawn|tablet>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ có người chơi mới có thể dùng lệnh này!");
            return true;
        }

        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <spawn|tablet>");
            return true;
        }

        String sub = effectiveArgs[0].toLowerCase(Locale.ROOT);
        if (sub.equals("spawn")) {
            Location loc = player.getLocation();
            if (plugin != null && plugin.getLunarRobotMechanic() != null) {
                LunarRobotEntity robot = plugin.getLunarRobotMechanic().spawnWildRobot(loc);
                if (robot != null) {
                    player.sendMessage("§a§l[Lunar Robot] §eĐã triệu hồi Robot 4 Chân tại vị trí của bạn!");
                    player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                }
            } else {
                player.sendMessage("§cRobot mechanic is not ready.");
            }
            return true;
        } else if (sub.equals("tablet")) {
            ItemStack tablet = null;
            try {
                tablet = HaoHanItemCore.get().getItemFactory().create("haohan:robot_tablet");
            } catch (Throwable ignored) {}

            if (tablet == null) {
                player.sendMessage("§cKhông thể tạo Tablet qua ItemCore.");
                return true;
            }

            player.getInventory().addItem(tablet);
            player.sendMessage("§a§l[Lunar Robot] §bĐã nhận Tablet điều khiển Robot!");
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
            return true;
        }

        sender.sendMessage("§eUsage: " + p + " <spawn|tablet>");
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length <= 1) {
            String input = effectiveArgs.length == 1 ? effectiveArgs[0] : "";
            List<String> matches = StringUtil.copyPartialMatches(input, List.of("spawn", "tablet"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return Collections.emptyList();
    }
}
