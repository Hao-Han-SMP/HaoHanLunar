package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.LunarCommand;
import vn.haohan.lunar.core.features.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.core.subsystem.LunarSubSystems;

import java.util.List;

public class ClearWardenCommand implements LunarCommand {

    @Override
    public String name() {
        return "clearwarden";
    }

    @Override
    public String description() {
        return "Xóa toàn bộ Boss The Lunar Warden và các Boss Showcase";
    }

    @Override
    public String permission() {
        return "haohan.admin";
    }

    @Override
    public List<String> aliases() {
        return List.of("wardenclear", "cleardummy", "wardencleardummy", "killwarden");
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        LunarWardenMechanic wardenMechanic = LunarSubSystems.get(LunarWardenMechanic.class);
        if (wardenMechanic == null) {
            wardenMechanic = plugin.getLunarWardenMechanic();
        }

        if (wardenMechanic == null) {
            sender.sendMessage("§c[Lunar Warden] Hệ thống Lunar Warden chưa sẵn sàng!");
            return true;
        }

        int cleared = wardenMechanic.clearAllWardens();
        sender.sendMessage("§a[Lunar Warden] Đã xóa thành công §e" + cleared + " §aboss và toàn bộ hiệu ứng / mô hình liên quan!");
        return true;
    }
}
