package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.LunarCommand;
import vn.haohan.lunar.core.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.core.features.boss.warden.showcase.WardenShowcaseHandler;
import vn.haohan.lunar.core.subsystem.LunarSubSystems;

import java.util.List;

public class WardenShowcaseCommand implements LunarCommand {

    @Override
    public String name() {
        return "wardenshowcase";
    }

    @Override
    public String description() {
        return "Triệu hồi hàng Boss biểu diễn tất cả các chiêu thức The Lunar Warden liên tục tại chỗ";
    }

    @Override
    public String permission() {
        return "haohan.admin";
    }

    @Override
    public List<String> aliases() {
        return List.of("spawnwardenshowcase", "wardendummy", "showcasewarden", "wardenline");
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

        double spacing = 15.0;
        if (args.length > 0) {
            try {
                spacing = Math.max(3.0, Double.parseDouble(args[0]));
            } catch (NumberFormatException ignored) {}
        }

        List<IronGolem> dummies = WardenShowcaseHandler.spawnShowcaseLine(
                plugin, wardenMechanic, player.getLocation(), spacing, player);
        player.sendMessage("§a[Lunar Warden] Đã triệu hồi hàng §e" + dummies.size()
                + " §aBoss Showcase (Khoảng cách: §e" + spacing + "m§a)! Dùng §e/clearwarden §ađể xóa.");
        return true;
    }
}
