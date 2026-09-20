package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.LunarCommand;
import vn.haohan.lunar.core.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.core.features.boss.warden.WardenSpawner;
import vn.haohan.lunar.core.features.boss.warden.showcase.WardenShowcaseHandler;
import vn.haohan.lunar.core.subsystem.LunarSubSystems;

import java.util.List;

public class SpawnWardenCommand implements LunarCommand {

    @Override
    public String name() {
        return "spawnwarden";
    }

    @Override
    public String description() {
        return "Triệu hồi Boss The Lunar Warden";
    }

    @Override
    public String permission() {
        return "haohan.admin";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ có người chơi mới dùng được lệnh này!");
            return true;
        }

        LunarWardenMechanic wardenMechanic = LunarSubSystems.get(LunarWardenMechanic.class);
        if (wardenMechanic == null) {
            wardenMechanic = plugin.getLunarWardenMechanic();
        }

        if (wardenMechanic == null) {
            player.sendMessage("§c[Lunar Warden] Hệ thống Lunar Warden chưa sẵn sàng!");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("clear")) {
                int cleared = wardenMechanic.clearAllWardens();
                player.sendMessage("§a[Lunar Warden] Đã xóa thành công §e" + cleared + " §aboss và toàn bộ thực thể hiển thị!");
                return true;
            }
            if (args[0].equalsIgnoreCase("showcase") || args[0].equalsIgnoreCase("dummy")) {
                double spacing = 15.0;
                if (args.length > 1) {
                    try {
                        spacing = Math.max(3.0, Double.parseDouble(args[1]));
                    } catch (NumberFormatException ignored) {}
                }
                List<IronGolem> dummies = WardenShowcaseHandler.spawnShowcaseLine(
                        plugin, wardenMechanic, player.getLocation(), spacing, player);
                player.sendMessage("§a[Lunar Warden] Đã triệu hồi thành công hàng §e" + dummies.size()
                        + " §aBoss Showcase liên tục thi triển chiêu thức vào không khí!");
                return true;
            }
        }

        WardenSpawner.spawnWarden(plugin, wardenMechanic, player.getLocation(), player);
        player.sendMessage("§a[Lunar Warden] Đã triệu hồi Boss The Lunar Warden!");
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return List.of("showcase", "dummy", "clear").stream()
                    .filter(s -> s.startsWith(lower))
                    .toList();
        }
        return List.of();
    }
}
