package vn.haohan.lunar.core.system.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.system.mob.pack.PackManager;
import vn.haohan.lunar.core.command.HaoHanCommand;
import vn.haohan.lunar.core.command.commands.*;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobCommandTest {

    @Test
    void deniesWithoutPermissionAndCompletesConfiguredIds() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("warden"));
        LunarMobManager mobManager = new LunarMobManager();

        HaoHanCommand command = new HaoHanCommand();
        command.registerCommand(new SpawnCommand(registry, (p, d) -> true));
        command.registerCommand(new SignalCommand(mobManager, (m, s) -> {}, registry));
        command.registerCommand(new InfoCommand(registry, mobManager));

        Sender sender = new Sender(false);
        command.execute(null, sender.proxy, "lunarmob", new String[]{"info", "warden"});
        assertTrue(sender.messages.getFirst().contains("quyền"));

        List<String> sTabs = command.tabComplete(null, new Sender(true).proxy, "lunarmob", new String[]{"s"});
        assertTrue(sTabs.contains("signal"));
        assertTrue(sTabs.contains("spawn"));
    }

    @Test
    void invalidIdAndReloadFailureAreReported() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        LunarMobManager mobManager = new LunarMobManager();

        HaoHanCommand command = new HaoHanCommand();
        command.registerCommand(new InfoCommand(registry, mobManager));
        command.registerCommand(new ReloadCommand());

        Sender sender = new Sender(true);
        command.execute(null, sender.proxy, "lunarmob", new String[]{"info", "missing"});
        command.execute(null, sender.proxy, "lunarmob", new String[]{"reload"});
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Unknown mob ID")));
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Reload") || message.contains("thành công")));
    }

    @Test
    void packCommandsListReloadAndDisable(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot.resolve("alpha_pack"));
        Files.writeString(packsRoot.resolve("alpha_pack/pack.yml"), "name: alpha_pack\nversion: 1.0\nauthor: Lunar\n");

        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        SkillRegistry skillRegistry = new SkillRegistry();
        DropManager dropManager = new DropManager();
        PackManager packManager = new PackManager();
        packManager.loadAll(packsRoot, registry, skillRegistry, dropManager);

        HaoHanCommand command = new HaoHanCommand();
        command.registerCommand(new PackCommand(registry, tempDir, packManager, skillRegistry, dropManager));

        Sender sender = new Sender(true);

        // List
        command.execute(null, sender.proxy, "lunarmob", new String[]{"pack", "list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("alpha_pack")));

        // Reload
        command.execute(null, sender.proxy, "lunarmob", new String[]{"pack", "reload", "alpha_pack"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Successfully reloaded pack")));

        // Tab completion
        List<String> tabs = command.tabComplete(null, sender.proxy, "lunarmob", new String[]{"pack", "r"});
        assertEquals(List.of("reload"), tabs);

        List<String> packTabs = command.tabComplete(null, sender.proxy, "lunarmob", new String[]{"pack", "reload", "a"});
        assertEquals(List.of("alpha_pack"), packTabs);

        // Disable
        command.execute(null, sender.proxy, "lunarmob", new String[]{"pack", "disable", "alpha_pack"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Successfully disabled pack")));
        assertEquals(0, packManager.loadedPacks().size());
    }

    @Test
    void pinsCommandsListAndTabCompletion() {
        HaoHanCommand command = new HaoHanCommand();
        command.registerCommand(new PinsCommand());
        Sender sender = new Sender(true);

        // Subcommand list
        command.execute(null, sender.proxy, "lunarmob", new String[]{"pins", "list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Registered Pins")));

        // Tab completion for pins
        List<String> tabs = command.tabComplete(null, sender.proxy, "lunarmob", new String[]{"pins", "c"});
        assertEquals(List.of("create_region"), tabs);

        List<String> allTabs = command.tabComplete(null, sender.proxy, "lunarmob", new String[]{"pins", ""});
        assertTrue(allTabs.contains("wand"));
        assertTrue(allTabs.contains("add"));
        assertTrue(allTabs.contains("remove"));
        assertTrue(allTabs.contains("create_region"));
        assertTrue(allTabs.contains("delete_region"));
        assertTrue(allTabs.contains("list"));
    }

    private static MobDefinition definition(String id) {
        return new MobDefinition(new MobDefinitionId(id), EntityType.IRON_GOLEM, id,
                null, Map.of(), Map.of(), List.of(), null, Set.of());
    }

    private static final class Sender {
        private final List<String> messages = new java.util.ArrayList<>();
        private final CommandSender proxy;
        private Sender(boolean permission) {
            proxy = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class}, (object, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permission;
                        case "isOp" -> permission;
                        case "sendMessage" -> { messages.add((String) args[0]); yield null; }
                        default -> null;
                    });
        }
    }
}
