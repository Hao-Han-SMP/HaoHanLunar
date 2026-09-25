package vn.haohan.lunar.core.command.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.PinRegion;
import vn.haohan.lunar.api.system.world.pin.SinglePin;
import vn.haohan.lunar.core.command.ICommand;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PinsCommand implements ICommand {

    private Path configRoot;

    public PinsCommand(Path configRoot) {
        this.configRoot = configRoot;
    }

    public PinsCommand() {
        this(null);
    }

    @Override
    public String name() {
        return "pins";
    }

    @Override
    public List<String> aliases() {
        return List.of("pin");
    }

    @Override
    public String description() {
        return "Quản lý vùng Pin Boundary";
    }

    @Override
    public String usage() {
        return "/hhl pins <wand|add|remove|create_region|delete_region|list>";
    }

    @Override
    public boolean execute(HaoHanLunarPlugin plugin, CommandSender sender, String label, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        String p = usagePrefix(label);

        if (effectiveArgs.length < 1) {
            sender.sendMessage("§eUsage: " + p + " <wand|add|remove|create_region|delete_region|list>");
            return true;
        }

        String sub = effectiveArgs[0].toLowerCase(Locale.ROOT);
        PinManager pinMgr = PinManager.get();

        switch (sub) {
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can receive the Pins Wand.");
                    return true;
                }
                ItemStack wand = new ItemStack(org.bukkit.Material.BLAZE_ROD);
                org.bukkit.inventory.meta.ItemMeta meta = wand.getItemMeta();
                if (meta != null) {
                    meta.displayName(net.kyori.adventure.text.Component.text("§6[Lunar Pins Wand]"));
                    meta.lore(List.of(
                            net.kyori.adventure.text.Component.text("§7Left-Click: Save pin at target block"),
                            net.kyori.adventure.text.Component.text("§7Right-Click: List active pins")
                    ));
                    wand.setItemMeta(meta);
                }
                player.getInventory().addItem(wand);
                sender.sendMessage("§aGiven Lunar Pins Wand!");
                return true;
            }
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can add a pin at their current location.");
                    return true;
                }
                if (effectiveArgs.length < 2) {
                    sender.sendMessage("§eUsage: " + p + " add <name>");
                    return true;
                }
                String name = effectiveArgs[1];
                SinglePin pin = SinglePin.fromLocation(name, player.getLocation());
                pinMgr.addPin(pin);
                sender.sendMessage("§aPin '§e" + name + "§a' saved at " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pin.x(), pin.y(), pin.z()) + " in " + pin.worldName());
                savePinsConfig(pinMgr);
                return true;
            }
            case "remove", "delete" -> {
                if (effectiveArgs.length < 2) {
                    sender.sendMessage("§eUsage: " + p + " remove <name>");
                    return true;
                }
                String name = effectiveArgs[1];
                if (pinMgr.removePin(name)) {
                    sender.sendMessage("§aPin '§e" + name + "§a' removed.");
                    savePinsConfig(pinMgr);
                } else {
                    sender.sendMessage("§cPin '§e" + name + "§c' not found.");
                }
                return true;
            }
            case "create_region" -> {
                if (effectiveArgs.length < 5) {
                    sender.sendMessage("§eUsage: " + p + " create_region <name> <pin1> <pin2> <pin3>... [minY] [maxY]");
                    return true;
                }
                String regionName = effectiveArgs[1];
                List<String> pinNames = new ArrayList<>();
                Double minY = null;
                Double maxY = null;

                for (int i = 2; i < effectiveArgs.length; i++) {
                    try {
                        double val = Double.parseDouble(effectiveArgs[i]);
                        if (minY == null) {
                            minY = val;
                        } else if (maxY == null) {
                            maxY = val;
                        }
                    } catch (NumberFormatException e) {
                        pinNames.add(effectiveArgs[i]);
                    }
                }

                if (pinNames.size() < 3) {
                    sender.sendMessage("§cAt least 3 valid pins are required to define a region.");
                    return true;
                }

                String world = sender instanceof Player pPlayer ? pPlayer.getWorld().getName() : "world";
                var firstPin = pinMgr.getPin(pinNames.getFirst());
                if (firstPin.isPresent()) {
                    world = firstPin.get().worldName();
                }

                if (minY == null) minY = -64.0;
                if (maxY == null) maxY = 320.0;

                try {
                    PinRegion reg = pinMgr.createRegion(regionName, world, pinNames, minY, maxY);
                    sender.sendMessage("§aCreated Pin Region '§e" + regionName + "§a' with " + reg.pins().size() + " pins (Y: " + minY + " to " + maxY + ").");
                    savePinsConfig(pinMgr);
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to create region: " + e.getMessage());
                }
                return true;
            }
            case "delete_region", "remove_region" -> {
                if (effectiveArgs.length < 2) {
                    sender.sendMessage("§eUsage: " + p + " delete_region <name>");
                    return true;
                }
                String name = effectiveArgs[1];
                if (pinMgr.removeRegion(name)) {
                    sender.sendMessage("§aPin Region '§e" + name + "§a' removed.");
                    savePinsConfig(pinMgr);
                } else {
                    sender.sendMessage("§cPin Region '§e" + name + "§c' not found.");
                }
                return true;
            }
            case "list" -> {
                sender.sendMessage("§6--- Registered Pins (" + pinMgr.allPins().size() + ") ---");
                for (var pin : pinMgr.allPins().values()) {
                    sender.sendMessage("§7- §e" + pin.name() + "§7: (" + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", pin.x(), pin.y(), pin.z()) + ") in " + pin.worldName());
                }
                sender.sendMessage("§6--- Registered Regions (" + pinMgr.allRegions().size() + ") ---");
                for (var reg : pinMgr.allRegions().values()) {
                    sender.sendMessage("§7- §b" + reg.name() + "§7: " + reg.pins().size() + " pins, Y: [" + reg.minY() + " .. " + reg.maxY() + "] in " + reg.worldName());
                }
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown pins action: " + sub + ". Use wand, add, remove, create_region, delete_region, or list.");
                return true;
            }
        }
    }

    private void savePinsConfig(PinManager pinMgr) {
        if (configRoot != null) {
            try {
                pinMgr.save(configRoot.resolve("regions.yml"));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public List<String> tabComplete(HaoHanLunarPlugin plugin, CommandSender sender, String alias, String[] args) {
        String[] effectiveArgs = stripSubcommand(args);
        if (effectiveArgs.length <= 1) {
            String input = effectiveArgs.length == 1 ? effectiveArgs[0] : "";
            List<String> matches = StringUtil.copyPartialMatches(input, List.of("wand", "add", "remove", "create_region", "delete_region", "list"), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        if (effectiveArgs.length == 2) {
            if ("remove".equalsIgnoreCase(effectiveArgs[0]) || "delete".equalsIgnoreCase(effectiveArgs[0])) {
                List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[1], PinManager.get().allPins().keySet(), new ArrayList<>());
                Collections.sort(matches);
                return matches;
            }
            if ("delete_region".equalsIgnoreCase(effectiveArgs[0]) || "remove_region".equalsIgnoreCase(effectiveArgs[0])) {
                List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[1], PinManager.get().allRegions().keySet(), new ArrayList<>());
                Collections.sort(matches);
                return matches;
            }
        }
        if (effectiveArgs.length >= 3 && "create_region".equalsIgnoreCase(effectiveArgs[0])) {
            List<String> matches = StringUtil.copyPartialMatches(effectiveArgs[effectiveArgs.length - 1], PinManager.get().allPins().keySet(), new ArrayList<>());
            Collections.sort(matches);
            return matches;
        }
        return List.of();
    }

    public void setConfigRoot(Path configRoot) {
        this.configRoot = configRoot;
    }
}
