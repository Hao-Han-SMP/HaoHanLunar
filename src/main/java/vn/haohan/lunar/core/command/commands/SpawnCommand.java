package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.command.ICommand;
import vn.haohan.lunar.core.service.LunarServices;
import vn.haohan.lunar.core.service.engine.MobCoreService;

import java.util.Collections;
import java.util.List;

public class SpawnCommand implements ICommand {

    @FunctionalInterface
    public interface IMobSpawner {
        boolean spawn(Player player, MobDefinition definition);
    }

    private final MobDefinitionRegistry definitions;
    private final IMobSpawner spawner;

    public SpawnCommand(MobDefinitionRegistry definitions, IMobSpawner spawner) {
        this.definitions = definitions;
        this.spawner = spawner;
    }

    public SpawnCommand() {
        this(null, null);
    }

    private MobDefinitionRegistry getRegistry() {
        return definitions != null ? definitions : defaultMobRegistry();
    }

    private IMobSpawner getSpawner() {
        if (spawner != null) return spawner;
        return (player, def) -> {
            try {
                if (player == null || player.getWorld() == null) return false;
                EntityType type = def.entityType();
                var entity = player.getWorld().spawnEntity(player.getLocation(), type);
                if (entity instanceof LivingEntity living) {
                    MobCoreService subSystem = LunarServices.get(MobCoreService.class);
                    if (subSystem != null && subSystem.getMobManager() != null) {
                        subSystem.getMobManager().register(living, def, "1.0", null);
                    }
                    return true;
                }
            } catch (Throwable ignored) {
            }
            return true;
        };
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public String description() {
        return "Triệu hồi quái vật theo ID";
    }

    @Override
    public String usage() {
        return "/hhl spawn <id>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can spawn a mob here.");
            return true;
        }
        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <id>");
            return true;
        }
        MobDefinitionRegistry reg = getRegistry();
        MobDefinition definition = (reg != null) ? reg.get(effectiveArgs[0]).orElse(null) : null;
        if (definition == null) {
            sender.sendMessage("§cUnknown mob ID: " + effectiveArgs[0]);
            return true;
        }
        IMobSpawner activeSpawner = getSpawner();
        if (activeSpawner != null && activeSpawner.spawn(player, definition)) {
            sender.sendMessage("§aSpawned mob: " + definition.id());
        } else {
            sender.sendMessage("§cMob spawn failed: " + definition.id());
        }
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length == 1) {
            return tabCompleteMobIds(effectiveArgs[0], getRegistry());
        }
        return Collections.emptyList();
    }
}
