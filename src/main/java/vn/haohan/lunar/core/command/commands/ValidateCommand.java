package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.api.system.debug.validator.ConfigValidationService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ValidateCommand implements ICommand {

    private final ConfigValidationService validationService;
    private Path configRoot;

    public ValidateCommand(ConfigValidationService validationService, Path configRoot) {
        this.validationService = validationService != null ? validationService : new ConfigValidationService();
        this.configRoot = configRoot;
    }

    public ValidateCommand() {
        this(new ConfigValidationService(), Path.of("src/main/resources"));
    }

    @Override
    public String name() {
        return "validate";
    }

    @Override
    public String description() {
        return "Kiểm tra tính hợp lệ của cấu hình hệ thống";
    }

    @Override
    public String usage() {
        return "/hhl validate [category]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);

        if (configRoot == null) {
            sender.sendMessage("§cConfiguration directory root is not configured for validation.");
            return true;
        }
        String category = (effectiveArgs.length > 0) ? effectiveArgs[0].toLowerCase(Locale.ROOT) : "all";
        ConfigValidationService.ValidationResult result = validationService.validate(configRoot, category);
        for (String line : result.formatSummary(category).split("\n")) {
            sender.sendMessage(line);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length <= 1) {
            String input = effectiveArgs.length == 1 ? effectiveArgs[0] : "";
            List<String> matches = StringUtil.copyPartialMatches(
                    input, List.of("all", "mobs", "skills", "drops", "spawners"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return List.of();
    }

    public void setConfigRoot(Path configRoot) {
        this.configRoot = configRoot;
    }

    public ConfigValidationService validationService() {
        return validationService;
    }
}
