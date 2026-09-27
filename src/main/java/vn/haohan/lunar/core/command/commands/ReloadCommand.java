package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.config.ConfigValidationReport;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.api.service.Services;
import vn.haohan.lunar.api.service.engine.MobCoreService;

public class ReloadCommand implements ICommand {

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String description() {
        return "Tải lại toàn bộ cấu hình plugin";
    }

    @Override
    public String usage() {
        return "/hhl reload";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        MobCoreService mobSystem = Services.get(MobCoreService.class);
        if (mobSystem != null) {
            ConfigValidationReport report = mobSystem.reload();
            if (report != null && report.isValid()) {
                sender.sendMessage("§aHaoHan Lunar đã nạp lại toàn bộ cấu hình thành công!");
            } else {
                sender.sendMessage("§cReload bị từ chối; giữ nguyên cấu hình cũ. "
                        + (report != null && !report.issues().isEmpty() ? report.issues().getFirst() : ""));
            }
        } else {
            sender.sendMessage("§aHaoHan Lunar đã nạp lại toàn bộ cấu hình thành công!");
        }
        return true;
    }
}
