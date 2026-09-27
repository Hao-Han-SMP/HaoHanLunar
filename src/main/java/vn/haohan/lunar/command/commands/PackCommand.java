package vn.haohan.lunar.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.api.system.combat.skill.SkillRegistry;
import vn.haohan.engine.api.system.loot.DropManager;
import vn.haohan.engine.api.system.mob.MobDefinitionRegistry;
import vn.haohan.engine.api.system.mob.pack.PackDefinition;
import vn.haohan.engine.api.system.mob.pack.PackManager;
import vn.haohan.lunar.command.ICommand;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PackCommand implements ICommand {

    private PackManager packManager;
    private SkillRegistry skillRegistry;
    private DropManager dropManager;
    private MobDefinitionRegistry definitions;
    private Path configRoot;

    public PackCommand(MobDefinitionRegistry definitions, Path configRoot) {
        this.definitions = definitions;
        this.configRoot = configRoot;
    }

    public PackCommand(MobDefinitionRegistry definitions, Path configRoot, PackManager packManager, SkillRegistry skillRegistry, DropManager dropManager) {
        this.definitions = definitions;
        this.configRoot = configRoot;
        this.packManager = packManager;
        this.skillRegistry = skillRegistry;
        this.dropManager = dropManager;
    }

    public PackCommand() {
        this(new MobDefinitionRegistry(), Path.of("src/main/resources"));
    }

    public void setPackManager(PackManager packManager, SkillRegistry skillRegistry, DropManager dropManager) {
        this.packManager = packManager;
        this.skillRegistry = skillRegistry;
        this.dropManager = dropManager;
    }

    @Override
    public String name() {
        return "pack";
    }

    @Override
    public String description() {
        return "Quản lý các Content Pack tùy chỉnh";
    }

    @Override
    public String usage() {
        return "/hhl pack <list|reload|disable> [name]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);

        if (packManager == null) {
            sender.sendMessage("§cContent Pack Manager is not initialized.");
            return true;
        }

        String p = usagePrefix(label);
        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <list|reload|disable> [name]");
            return true;
        }

        String sub = effectiveArgs[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                var packs = packManager.loadedPacks();
                sender.sendMessage("§6=== Loaded Content Packs (" + packs.size() + ") ===");
                for (PackDefinition pack : packs.values()) {
                    var m = pack.manifest();
                    sender.sendMessage("§e - §b" + pack.name() + " §7v" + m.version() + " §eby §f" + m.author()
                            + " §7(Mobs: " + pack.mobs().size() + ", Skills: " + pack.skills().size() + ", Drops: " + pack.drops().size() + ")");
                }
                return true;
            }
            case "reload" -> {
                if (effectiveArgs.length < 2) {
                    sender.sendMessage("§eUsage: " + p + " reload <name>");
                    return true;
                }
                String packName = effectiveArgs[1];
                Path packsRoot = (configRoot != null ? configRoot : Path.of("src/main/resources")).resolve("packs");
                var result = packManager.reloadPack(packsRoot, packName, definitions, skillRegistry, dropManager);
                if (result.success()) {
                    sender.sendMessage("§aSuccessfully reloaded pack: §e" + packName);
                } else {
                    sender.sendMessage("§cFailed to reload pack §e" + packName + "§c: " + String.join(", ", result.errors()));
                }
                return true;
            }
            case "disable" -> {
                if (effectiveArgs.length < 2) {
                    sender.sendMessage("§eUsage: " + p + " disable <name>");
                    return true;
                }
                String packName = effectiveArgs[1];
                boolean disabled = packManager.disablePack(packName, definitions, skillRegistry, dropManager);
                if (disabled) {
                    sender.sendMessage("§aSuccessfully disabled pack: §e" + packName);
                } else {
                    sender.sendMessage("§cPack not found or could not be disabled: §e" + packName);
                }
                return true;
            }
            default -> {
                sender.sendMessage("§eUsage: " + p + " <list|reload|disable> [name]");
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);

        if (effectiveArgs.length <= 1) {
            String token = (effectiveArgs.length == 1) ? effectiveArgs[0] : "";
            return StringUtil.copyPartialMatches(token, List.of("list", "reload", "disable"), new ArrayList<>());
        }

        if (effectiveArgs.length == 2 && (effectiveArgs[0].equalsIgnoreCase("reload") || effectiveArgs[0].equalsIgnoreCase("disable"))) {
            if (packManager != null) {
                return StringUtil.copyPartialMatches(effectiveArgs[1], packManager.loadedPacks().keySet(), new ArrayList<>());
            }
        }

        return Collections.emptyList();
    }
}
