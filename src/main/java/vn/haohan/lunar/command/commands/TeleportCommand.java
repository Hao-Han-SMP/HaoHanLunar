package vn.haohan.lunar.command.commands;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.command.ICommand;

import java.util.List;

public class TeleportCommand implements ICommand {

    @Override
    public String name() {
        return "tp";
    }

    @Override
    public List<String> aliases() {
        return List.of("teleport", "lunar", "dimension", "tplunar");
    }

    @Override
    public String description() {
        return "Dịch chuyển đến thế giới Lunar Dimension";
    }

    @Override
    public String usage() {
        return "/hhl tp";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ có người chơi mới có thể dùng lệnh này.");
            return true;
        }

        World lunarWorld = (plugin != null) ? plugin.getLunarWorld() : null;
        if (lunarWorld == null) {
            sender.sendMessage("§cThế giới Lunar hiện chưa được load hoặc không tồn tại!");
            return true;
        }

        Location target = new Location(lunarWorld, 0.5, 65, 0.5, 0, 0);
        player.teleport(target);
        player.sendMessage("§aĐã dịch chuyển tới không gian Lunar!");
        return true;
    }
}
