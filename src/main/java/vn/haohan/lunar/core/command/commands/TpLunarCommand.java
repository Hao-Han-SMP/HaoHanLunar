package vn.haohan.lunar.core.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.LunarCommand;

import java.util.List;

public class TpLunarCommand implements LunarCommand {

    @Override
    public String name() {
        return "tplunar";
    }

    @Override
    public String description() {
        return "Teleport sang thế giới Mặt Trăng haohan:lunar";
    }

    @Override
    public String permission() {
        return "haohan.admin";
    }

    @Override
    public List<String> aliases() {
        return List.of("lunar", "tplunardimension", "gotolunar");
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ có người chơi mới dùng được lệnh này!");
            return true;
        }

        String lunarWorldName = plugin.getConfig().getString("skybox.world", "haohan:lunar");
        World lunarWorld = Bukkit.getWorld(lunarWorldName);

        if (lunarWorld == null) {
            for (World w : Bukkit.getWorlds()) {
                if (w.getKey().toString().equals(lunarWorldName) || w.getName().equalsIgnoreCase("lunar")) {
                    lunarWorld = w;
                    break;
                }
            }
        }

        if (lunarWorld == null) {
            player.sendMessage("§c[HaoHanLunar] Không tìm thấy thế giới Mặt Trăng (§e" + lunarWorldName + "§c)! Hãy đảm bảo thế giới đã được nạp.");
            return true;
        }

        Location spawnLoc = lunarWorld.getSpawnLocation();
        player.teleport(spawnLoc);
        player.sendMessage("§a[HaoHanLunar] Đã dịch chuyển thành công đến Mặt Trăng (§e" + lunarWorld.getName() + "§a)!");
        return true;
    }
}
