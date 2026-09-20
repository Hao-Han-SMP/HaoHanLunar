package vn.haohan.lunar.core.system.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.api.system.command.LunarMobCommand;
import vn.haohan.lunar.api.system.config.ConfigValidationReport;
import vn.haohan.lunar.api.system.loot.DropManager;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;
import vn.haohan.lunar.api.mob.pack.PackManager;
import vn.haohan.lunar.api.system.combat.skill.SkillRegistry;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunarMobCommandTest {

    @Test
    void deniesWithoutPermissionAndCompletesConfiguredIds() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("warden"));
        LunarMobCommand command = new LunarMobCommand(registry, new LunarMobManager(), (player, mob) -> true,
                                                      () -> new ConfigValidationReport(List.of()), (mob, signal) -> { });
        Sender sender = new Sender(false);

        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"info", "warden"});
        assertTrue(sender.messages.getFirst().contains("permission"));
        assertEquals(List.of("signal", "spawn"), command.onTabComplete(new Sender(true).proxy, null, "lunarmob", new String[]{"s"}));
    }

    @Test
    void invalidIdAndReloadFailureAreReported() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        LunarMobCommand command = new LunarMobCommand(registry, new LunarMobManager(), (player, mob) -> true,
                () -> new ConfigValidationReport(List.of(new ConfigValidationReport.Issue("x", "$", "bad", 1, 1))),
                (mob, signal) -> { });
        Sender sender = new Sender(true);

        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"info", "missing"});
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"reload"});
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("Unknown mob ID")));
        assertTrue(sender.messages.stream().anyMatch(message -> message.contains("rejected")));
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

        LunarMobCommand command = new LunarMobCommand(registry, new LunarMobManager(), (player, mob) -> true,
                () -> new ConfigValidationReport(List.of()), (mob, signal) -> { },
                null, null, null, tempDir);
        command.setPackManager(packManager, skillRegistry, dropManager);

        Sender sender = new Sender(true);

        // List
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"pack", "list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("alpha_pack")));

        // Reload
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"pack", "reload", "alpha_pack"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Successfully reloaded pack")));

        // Tab completion
        List<String> tabs = command.onTabComplete(sender.proxy, null, "lunarmob", new String[]{"pack", "r"});
        assertEquals(List.of("reload"), tabs);

        List<String> packTabs = command.onTabComplete(sender.proxy, null, "lunarmob", new String[]{"pack", "reload", "a"});
        assertEquals(List.of("alpha_pack"), packTabs);

        // Disable
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"pack", "disable", "alpha_pack"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Successfully disabled pack")));
        assertEquals(0, packManager.loadedPacks().size());
    }

    @Test
    void pinsCommandsListAndTabCompletion() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        LunarMobCommand command = new LunarMobCommand(registry, new LunarMobManager(), (player, mob) -> true,
                () -> new ConfigValidationReport(List.of()), (mob, signal) -> { });
        Sender sender = new Sender(true);

        // Subcommand list
        command.onCommand(sender.proxy, null, "lunarmob", new String[]{"pins", "list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Registered Pins")));

        // Tab completion for pins
        List<String> tabs = command.onTabComplete(sender.proxy, null, "lunarmob", new String[]{"pins", "c"});
        assertEquals(List.of("create_region"), tabs);

        List<String> allTabs = command.onTabComplete(sender.proxy, null, "lunarmob", new String[]{"pins", ""});
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
                        case "sendMessage" -> { messages.add((String) args[0]); yield null; }
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
