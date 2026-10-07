package vn.haohan.lunar.command.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.command.ICommand;
import vn.haohan.engine.api.system.item.ItemDefinitionRegistry;
import vn.haohan.lunar.item.MythicItemDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ItemCommand implements ICommand {

    private final ItemDefinitionRegistry defaultRegistry = new ItemDefinitionRegistry();
    private ItemDefinitionRegistry itemRegistry;
    private Function<String, Player> playerResolver;

    public ItemCommand(ItemDefinitionRegistry itemRegistry, Function<String, Player> playerResolver) {
        this.itemRegistry = itemRegistry;
        this.playerResolver = playerResolver;
    }

    public ItemCommand() {
        this(null, null);
    }

    @Override
    public String name() {
        return "item";
    }

    @Override
    public String description() {
        return "Quản trị vật phẩm custom";
    }

    @Override
    public String usage() {
        return "/hhl item give <player> <item_id> [amount]";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 1 || !effectiveArgs[0].equalsIgnoreCase("give")) {
            sender.sendMessage("§eUsage: " + p + " give <player> <item_id> [amount]");
            return true;
        }
        if (effectiveArgs.length < 3) {
            sender.sendMessage("§eUsage: " + p + " give <player> <item_id> [amount]");
            return true;
        }

        String playerName = effectiveArgs[1];
        String itemId = effectiveArgs[2];
        int amount = 1;
        if (effectiveArgs.length > 3) {
            try {
                amount = Math.max(1, Integer.parseInt(effectiveArgs[3]));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid item amount: " + effectiveArgs[3]);
                return true;
            }
        }

        Player targetPlayer = resolvePlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return true;
        }

        ItemDefinitionRegistry registry = getActiveRegistry();
        Optional<MythicItemDefinition> defOpt = registry.get(itemId);
        if (defOpt.isEmpty()) {
            sender.sendMessage("§cUnknown custom item ID: " + itemId);
            return true;
        }

        Optional<ItemStack> itemOpt = registry.buildItemStack(itemId, amount);
        if (itemOpt.isEmpty()) {
            sender.sendMessage("§cFailed to generate item: " + itemId);
            return true;
        }

        targetPlayer.getInventory().addItem(itemOpt.get());
        sender.sendMessage("§aGave " + amount + "x §e" + itemId + "§a to §f" + targetPlayer.getName() + "§a.");
        return true;
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length <= 1) {
            String input = effectiveArgs.length == 1 ? effectiveArgs[0] : "";
            List<String> matches = StringUtil.copyPartialMatches(input, List.of("give"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        if (effectiveArgs.length == 2) {
            List<String> playerNames = new ArrayList<>();
            try {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
            } catch (Throwable ignored) {}
            List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[1], playerNames, new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        if (effectiveArgs.length == 3) {
            List<String> itemIds = getActiveRegistry().allDefinitions().stream().map(MythicItemDefinition::id).toList();
            List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[2], itemIds, new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        if (effectiveArgs.length == 4) {
            List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[3], List.of("1", "16", "64"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return List.of();
    }

    private Player resolvePlayer(String name) {
        if (playerResolver != null) {
            return playerResolver.apply(name);
        }
        try {
            return Bukkit.getPlayer(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public ItemDefinitionRegistry getActiveRegistry() {
        return itemRegistry != null ? itemRegistry : defaultRegistry;
    }

    public void setItemRegistry(ItemDefinitionRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public ItemDefinitionRegistry getItemRegistry() {
        return getActiveRegistry();
    }

    public void setPlayerResolver(Function<String, Player> playerResolver) {
        this.playerResolver = playerResolver;
    }
}
