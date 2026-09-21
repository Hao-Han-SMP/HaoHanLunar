package vn.haohan.lunar.api.system.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.pack.PackDefinition;
import vn.haohan.lunar.api.mob.pack.PackManager;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.PinRegion;
import vn.haohan.lunar.api.system.world.pin.SinglePin;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.core.system.debug.trace.SkillTracer;
import vn.haohan.lunar.core.system.debug.validator.ConfigValidationService;
import vn.haohan.lunar.core.system.item.ItemDefinitionRegistry;
import vn.haohan.lunar.core.system.item.MythicItemDefinition;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Administrative command facade for configured mobs and custom items. */
public class LunarMobCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "haohan.lunarmob.admin";
    private final MobDefinitionRegistry definitions;
    private final LunarMobManager manager;
    private final MobSpawner spawner;
    private final Supplier<ConfigValidationReport> reloader;
    private final BiConsumer<ActiveMob, String> signalHandler;
    private final ConfigValidationService validationService;
    private final SkillTracer tracer;
    private final PerformanceMetrics metrics;
    private final Path configRoot;
    private PackManager packManager;
    private SkillRegistry skillRegistry;
    private DropManager dropManager;
    private ItemDefinitionRegistry itemRegistry = new ItemDefinitionRegistry();
    private Function<String, Player> playerResolver = (name) -> {
        try {
            return Bukkit.getPlayer(name);
        } catch (Throwable ignored) {
            return null;
        }
    };

    public LunarMobCommand(MobDefinitionRegistry definitions, LunarMobManager manager,
                           MobSpawner spawner, Supplier<ConfigValidationReport> reloader,
                           BiConsumer<ActiveMob, String> signalHandler,
                           ConfigValidationService validationService,
                           SkillTracer tracer,
                           PerformanceMetrics metrics,
                           Path configRoot) {
        this.definitions = Objects.requireNonNull(definitions, "Definition registry must not be null");
        this.manager = Objects.requireNonNull(manager, "Mob manager must not be null");
        this.spawner = Objects.requireNonNull(spawner, "Mob spawner must not be null");
        this.reloader = Objects.requireNonNull(reloader, "Reload callback must not be null");
        this.signalHandler = Objects.requireNonNull(signalHandler, "Signal handler must not be null");
        this.validationService = validationService != null ? validationService : new ConfigValidationService();
        this.tracer = tracer != null ? tracer : new SkillTracer();
        this.metrics = metrics != null ? metrics : new PerformanceMetrics();
        this.configRoot = configRoot;
    }

    public LunarMobCommand(MobDefinitionRegistry definitions, LunarMobManager manager,
                           MobSpawner spawner, Supplier<ConfigValidationReport> reloader,
                           BiConsumer<ActiveMob, String> signalHandler) {
        this(definitions, manager, spawner, reloader, signalHandler,
                new ConfigValidationService(), new SkillTracer(), new PerformanceMetrics(), null);
    }

    public void setItemRegistry(ItemDefinitionRegistry itemRegistry) {
        if (itemRegistry != null) {
            this.itemRegistry = itemRegistry;
        }
    }

    public void setPackManager(PackManager packManager, SkillRegistry skillRegistry, DropManager dropManager) {
        this.packManager = packManager;
        this.skillRegistry = skillRegistry;
        this.dropManager = dropManager;
    }

    public PackManager packManager() {
        return packManager;
    }

    public ItemDefinitionRegistry itemRegistry() {
        return itemRegistry;
    }

    public void setPlayerResolver(Function<String, Player> playerResolver) {
        if (playerResolver != null) {
            this.playerResolver = playerResolver;
        }
    }

    public ConfigValidationService validationService() {
        return validationService;
    }

    public SkillTracer tracer() {
        return tracer;
    }

    public PerformanceMetrics metrics() {
        return metrics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cYou do not have permission to use /lunarmob.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /lunarmob <spawn|kill|info|reload|signal|validate|trace|metrics|item|pack>");
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> spawn(sender, args);
            case "kill" -> kill(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            case "signal" -> signal(sender, args);
            case "validate" -> validate(sender, args);
            case "trace" -> trace(sender, args);
            case "metrics" -> metrics(sender, args);
            case "item" -> item(sender, args);
            case "pack" -> pack(sender, args);
            case "pins", "pin" -> pins(sender, args);
            default -> { sender.sendMessage("§cUnknown subcommand. Use spawn, kill, info, reload, signal, validate, trace, metrics, item, or pack."); yield true; }
        };
    }

    private boolean pack(CommandSender sender, String[] args) {
        if (packManager == null) {
            sender.sendMessage("§cContent Pack Manager is not initialized.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /lunarmob pack <list|reload|disable> [name]");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
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
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /lunarmob pack reload <name>");
                    return true;
                }
                String targetPack = args[2];
                Path root = configRoot != null ? configRoot.resolve("packs") : Path.of("plugins/HaoHanLunar/packs");
                var report = packManager.reloadPack(root, targetPack, definitions, skillRegistry, dropManager);
                if (report.success()) {
                    sender.sendMessage("§aSuccessfully reloaded pack: §f" + targetPack);
                } else {
                    sender.sendMessage("§cFailed to reload pack §f" + targetPack + ":");
                    for (String err : report.errors()) {
                        sender.sendMessage("§c - " + err);
                    }
                }
                return true;
            }
            case "disable" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /lunarmob pack disable <name>");
                    return true;
                }
                String targetPack = args[2];
                boolean disabled = packManager.disablePack(targetPack, definitions, skillRegistry, dropManager);
                if (disabled) {
                    sender.sendMessage("§aSuccessfully disabled pack: §f" + targetPack);
                } else {
                    sender.sendMessage("§cPack not found or not active: " + targetPack);
                }
                return true;
            }
            default -> {
                sender.sendMessage("§cUnknown pack action: " + sub + ". Use list, reload, or disable.");
                return true;
            }
        }
    }

    private boolean spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cOnly players can spawn a mob here."); return true; }
        if (args.length < 2) { sender.sendMessage("§eUsage: /lunarmob spawn <id>"); return true; }
        MobDefinition definition = definitions.get(args[1]).orElse(null);
        if (definition == null) { sender.sendMessage("§cUnknown mob ID: " + args[1]); return true; }
        if (spawner.spawn(player, definition)) sender.sendMessage("§aSpawned mob: " + definition.id());
        else sender.sendMessage("§cMob spawn failed: " + definition.id());
        return true;
    }

    private boolean kill(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eUsage: /lunarmob kill <id>"); return true; }
        List<ActiveMob> mobs = new ArrayList<>(manager.findByDefinitionId(args[1]));
        for (ActiveMob mob : mobs) { mob.entity().remove(); manager.unregister(mob.entityId()); }
        sender.sendMessage("§aRemoved " + mobs.size() + " mob(s) matching " + args[1] + ".");
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage("§eUsage: /lunarmob info <id>"); return true; }
        MobDefinition definition = definitions.get(args[1]).orElse(null);
        if (definition == null) { sender.sendMessage("§cUnknown mob ID: " + args[1]); return true; }
        sender.sendMessage("§eMob §f" + definition.id() + "§e: type=" + definition.entityType()
                + ", model=" + definition.modelId().orElse("none") + ", active=" + manager.findByDefinitionId(definition.id().value()).size());
        return true;
    }

    private boolean reload(CommandSender sender) {
        ConfigValidationReport report = reloader.get();
        if (report.isValid()) sender.sendMessage("§aLunar mob configuration reloaded.");
        else sender.sendMessage("§cReload rejected; active configuration was kept. " + report.issues().getFirst());
        return true;
    }

    private boolean signal(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§eUsage: /lunarmob signal <id> <signal>"); return true; }
        List<ActiveMob> mobs = new ArrayList<>(manager.findByDefinitionId(args[1]));
        for (ActiveMob mob : mobs) signalHandler.accept(mob, args[2]);
        sender.sendMessage("§aSent signal " + args[2] + " to " + mobs.size() + " mob(s).");
        return true;
    }

    private boolean validate(CommandSender sender, String[] args) {
        if (configRoot == null) {
            sender.sendMessage("§cConfiguration directory root is not configured for validation.");
            return true;
        }
        String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        ConfigValidationService.ValidationResult result = validationService.validate(configRoot, category);
        for (String line : result.formatSummary(category).split("\n")) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean trace(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /lunarmob trace <uuid|target> [on|off]");
            return true;
        }

        UUID targetMobId = null;
        if (args[1].equalsIgnoreCase("target")) {
            if (sender instanceof Player player) {
                Entity target = player.getTargetEntity(20);
                if (target != null && manager.get(target.getUniqueId()) != null) {
                    targetMobId = target.getUniqueId();
                } else {
                    sender.sendMessage("§cNo active Lunar mob targeted within 20 blocks.");
                    return true;
                }
            } else {
                sender.sendMessage("§cOnly players can use 'target' option.");
                return true;
            }
        } else {
            try {
                targetMobId = UUID.fromString(args[1]);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid UUID format: " + args[1]);
                return true;
            }
        }

        boolean enable = true;
        if (args.length > 2) {
            enable = !args[2].equalsIgnoreCase("off");
        }

        if (enable) {
            tracer.enableTrace(targetMobId);
            sender.sendMessage("§aTracing ENABLED for mob " + targetMobId);
        } else {
            tracer.disableTrace(targetMobId);
            sender.sendMessage("§cTracing DISABLED for mob " + targetMobId);
        }
        return true;
    }

    private boolean metrics(CommandSender sender, String[] args) {
        String system = args.length > 1 ? args[1] : "all";
        for (String line : metrics.formatReport(system).split("\n")) {
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean pins(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /lunarmob pins <wand|add|remove|create_region|delete_region|list>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        PinManager pinMgr = PinManager.get();

        switch (sub) {
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cOnly players can receive the Pins Wand.");
                    return true;
                }
                org.bukkit.inventory.ItemStack wand = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_ROD);
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
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /lunarmob pins add <name>");
                    return true;
                }
                String name = args[2];
                SinglePin pin = SinglePin.fromLocation(name, player.getLocation());
                pinMgr.addPin(pin);
                sender.sendMessage("§aPin '§e" + name + "§a' saved at " + String.format("%.1f, %.1f, %.1f", pin.x(), pin.y(), pin.z()) + " in " + pin.worldName());
                if (configRoot != null) {
                    try { pinMgr.save(configRoot.resolve("regions.yml")); } catch (Exception ignored) {}
                }
                return true;
            }
            case "remove", "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /lunarmob pins remove <name>");
                    return true;
                }
                String name = args[2];
                if (pinMgr.removePin(name)) {
                    sender.sendMessage("§aPin '§e" + name + "§a' removed.");
                    if (configRoot != null) {
                        try { pinMgr.save(configRoot.resolve("regions.yml")); } catch (Exception ignored) {}
                    }
                } else {
                    sender.sendMessage("§cPin '§e" + name + "§c' not found.");
                }
                return true;
            }
            case "create_region" -> {
                if (args.length < 6) {
                    sender.sendMessage("§eUsage: /lunarmob pins create_region <name> <pin1> <pin2> <pin3>... [minY] [maxY]");
                    return true;
                }
                String regionName = args[2];
                List<String> pinNames = new ArrayList<>();
                Double minY = null;
                Double maxY = null;

                for (int i = 3; i < args.length; i++) {
                    try {
                        double val = Double.parseDouble(args[i]);
                        if (minY == null) {
                            minY = val;
                        } else if (maxY == null) {
                            maxY = val;
                        }
                    } catch (NumberFormatException e) {
                        pinNames.add(args[i]);
                    }
                }

                if (pinNames.size() < 3) {
                    sender.sendMessage("§cAt least 3 valid pins are required to define a region.");
                    return true;
                }

                String world = sender instanceof Player p ? p.getWorld().getName() : "world";
                var firstPin = pinMgr.getPin(pinNames.getFirst());
                if (firstPin.isPresent()) {
                    world = firstPin.get().worldName();
                }

                if (minY == null) minY = -64.0;
                if (maxY == null) maxY = 320.0;

                try {
                    PinRegion reg = pinMgr.createRegion(regionName, world, pinNames, minY, maxY);
                    sender.sendMessage("§aCreated Pin Region '§e" + regionName + "§a' with " + reg.pins().size() + " pins (Y: " + minY + " to " + maxY + ").");
                    if (configRoot != null) {
                        try { pinMgr.save(configRoot.resolve("regions.yml")); } catch (Exception ignored) {}
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage("§cFailed to create region: " + e.getMessage());
                }
                return true;
            }
            case "delete_region", "remove_region" -> {
                if (args.length < 3) {
                    sender.sendMessage("§eUsage: /lunarmob pins delete_region <name>");
                    return true;
                }
                String name = args[2];
                if (pinMgr.removeRegion(name)) {
                    sender.sendMessage("§aPin Region '§e" + name + "§a' removed.");
                    if (configRoot != null) {
                        try { pinMgr.save(configRoot.resolve("regions.yml")); } catch (Exception ignored) {}
                    }
                } else {
                    sender.sendMessage("§cPin Region '§e" + name + "§c' not found.");
                }
                return true;
            }
            case "list" -> {
                sender.sendMessage("§6--- Registered Pins (" + pinMgr.allPins().size() + ") ---");
                for (var pin : pinMgr.allPins().values()) {
                    sender.sendMessage("§7- §e" + pin.name() + "§7: (" + String.format("%.1f, %.1f, %.1f", pin.x(), pin.y(), pin.z()) + ") in " + pin.worldName());
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

    private boolean item(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage("§eUsage: /lunarmob item give <player> <item_id> [amount]");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /lunarmob item give <player> <item_id> [amount]");
            return true;
        }

        String playerName = args[2];
        String itemId = args[3];
        int amount = 1;
        if (args.length > 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[4]));
            } catch (NumberFormatException e) {
                sender.sendMessage("§cInvalid item amount: " + args[4]);
                return true;
            }
        }

        Player targetPlayer = playerResolver.apply(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§cPlayer not found: " + playerName);
            return true;
        }

        Optional<MythicItemDefinition> defOpt = itemRegistry.get(itemId);
        if (defOpt.isEmpty()) {
            sender.sendMessage("§cUnknown custom item ID: " + itemId);
            return true;
        }

        Optional<ItemStack> itemOpt = itemRegistry.buildItemStack(itemId, amount);
        if (itemOpt.isEmpty()) {
            sender.sendMessage("§cFailed to generate item: " + itemId);
            return true;
        }

        targetPlayer.getInventory().addItem(itemOpt.get());
        sender.sendMessage("§aGave " + amount + "x §e" + itemId + "§a to §f" + targetPlayer.getName() + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("spawn", "kill", "info", "reload", "signal", "validate", "trace", "metrics", "item", "pack", "pins"));
        }
        if (args.length == 2 && ("pins".equalsIgnoreCase(args[0]) || "pin".equalsIgnoreCase(args[0]))) {
            return partial(args[1], List.of("wand", "add", "remove", "create_region", "delete_region", "list"));
        }
        if (args.length == 3 && ("pins".equalsIgnoreCase(args[0]) || "pin".equalsIgnoreCase(args[0]))) {
            if ("remove".equalsIgnoreCase(args[1]) || "delete".equalsIgnoreCase(args[1])) {
                return partial(args[2], new ArrayList<>(PinManager.get().allPins().keySet()));
            }
            if ("delete_region".equalsIgnoreCase(args[1]) || "remove_region".equalsIgnoreCase(args[1])) {
                return partial(args[2], new ArrayList<>(PinManager.get().allRegions().keySet()));
            }
        }
        if (args.length >= 4 && ("pins".equalsIgnoreCase(args[0]) || "pin".equalsIgnoreCase(args[0])) && "create_region".equalsIgnoreCase(args[1])) {
            return partial(args[args.length - 1], new ArrayList<>(PinManager.get().allPins().keySet()));
        }
        if (args.length == 2 && "pack".equalsIgnoreCase(args[0])) {
            return partial(args[1], List.of("list", "reload", "disable"));
        }
        if (args.length == 3 && "pack".equalsIgnoreCase(args[0]) && ("reload".equalsIgnoreCase(args[1]) || "disable".equalsIgnoreCase(args[1]))) {
            if (packManager != null) {
                return partial(args[2], new ArrayList<>(packManager.loadedPacks().keySet()));
            }
        }
        if (args.length == 2) {
            if ("validate".equalsIgnoreCase(args[0])) {
                return partial(args[1], List.of("all", "mobs", "skills", "drops", "spawners"));
            }
            if ("metrics".equalsIgnoreCase(args[0])) {
                return partial(args[1], List.of("all", "SkillScheduler", "ProjectileTracker", "SpawnerManager"));
            }
            if ("item".equalsIgnoreCase(args[0])) {
                return partial(args[1], List.of("give"));
            }
        }
        if (args.length == 3 && "item".equalsIgnoreCase(args[0])) {
            List<String> playerNames = new ArrayList<>();
            try {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    playerNames.add(p.getName());
                }
            } catch (Throwable ignored) {}
            return partial(args[2], playerNames);
        }
        if (args.length == 4 && "item".equalsIgnoreCase(args[0])) {
            return partial(args[3], itemRegistry.allDefinitions().stream().map(MythicItemDefinition::id).toList());
        }
        if (args.length == 5 && "item".equalsIgnoreCase(args[0])) {
            return partial(args[4], List.of("1", "16", "64"));
        }
        if (args.length == 3 && "trace".equalsIgnoreCase(args[0])) {
            return partial(args[2], List.of("on", "off"));
        }
        return List.of();
    }

    private static List<String> partial(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    @FunctionalInterface
    public interface MobSpawner { boolean spawn(Player player, MobDefinition definition); }
}
