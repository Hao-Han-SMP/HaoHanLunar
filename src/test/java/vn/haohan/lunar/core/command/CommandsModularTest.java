package vn.haohan.lunar.core.command;

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
import vn.haohan.lunar.core.command.commands.*;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.system.debug.metrics.PerformanceMetrics;
import vn.haohan.lunar.core.system.debug.trace.SkillTracer;
import vn.haohan.lunar.core.system.debug.validator.ConfigValidationService;
import vn.haohan.lunar.core.system.item.ItemDefinitionRegistry;
import vn.haohan.lunar.core.system.item.MythicItemDefinition;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CommandsModularTest {

    @Test
    void testSpawnAndKillCommandsTabAndExecute() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("skeleton_king"));
        LunarMobManager manager = new LunarMobManager();

        SpawnCommand spawnCmd = new SpawnCommand(registry, (p, def) -> true);
        assertEquals("spawn", spawnCmd.name());
        List<String> spawnTabs = spawnCmd.tabComplete(null, null, "spawn", new String[]{"skele"});
        assertEquals(List.of("skeleton_king"), spawnTabs);

        KillCommand killCmd = new KillCommand(manager, registry);
        assertEquals("kill", killCmd.name());
        List<String> killTabs = killCmd.tabComplete(null, null, "kill", new String[]{"skele"});
        assertEquals(List.of("skeleton_king"), killTabs);

        Sender sender = new Sender(true);
        killCmd.execute(null, sender.proxy, "hhl kill", new String[]{"skeleton_king"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Removed 0 mob(s)")));
    }

    @Test
    void testInfoAndSignalCommands() {
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        registry.register(definition("fire_demon"));
        LunarMobManager manager = new LunarMobManager();

        InfoCommand infoCmd = new InfoCommand(registry, manager);
        assertEquals("info", infoCmd.name());
        Sender sender = new Sender(true);

        infoCmd.execute(null, sender.proxy, "hhl info", new String[]{"fire_demon"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Mob") && m.contains("fire_demon")));

        List<String> capturedSignals = new ArrayList<>();
        SignalCommand signalCmd = new SignalCommand(manager, (mob, sig) -> capturedSignals.add(sig), registry);
        assertEquals("signal", signalCmd.name());

        sender.messages.clear();
        signalCmd.execute(null, sender.proxy, "hhl signal", new String[]{"fire_demon", "PHASE2"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Sent signal PHASE2")));
    }

    @Test
    void testValidateMetricsAndTraceCommands(@TempDir Path tempDir) {
        ValidateCommand valCmd = new ValidateCommand(new ConfigValidationService(), tempDir);
        assertEquals("validate", valCmd.name());
        Sender sender = new Sender(true);
        valCmd.execute(null, sender.proxy, "hhl validate", new String[]{"all"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Lunar Validation Report")));

        PerformanceMetrics metrics = new PerformanceMetrics();
        metrics.record("SkillScheduler", 5_000_000L);
        MetricsCommand metCmd = new MetricsCommand(metrics);
        assertEquals("metrics", metCmd.name());
        sender.messages.clear();
        metCmd.execute(null, sender.proxy, "hhl metrics", new String[]{"metrics", "all"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Performance Metrics")));

        TraceCommand traceCmd = new TraceCommand(new LunarMobManager(), new SkillTracer());
        assertEquals("trace", traceCmd.name());
        List<String> traceTabs = traceCmd.tabComplete(null, null, "trace", new String[]{"uuid", "o"});
        assertEquals(List.of("off", "on"), traceTabs);
    }

    @Test
    void testPackCommandDirectly(@TempDir Path tempDir) throws IOException {
        Path packsRoot = tempDir.resolve("packs");
        Files.createDirectories(packsRoot.resolve("beta_pack"));
        Files.writeString(packsRoot.resolve("beta_pack/pack.yml"), "name: beta_pack\nversion: 2.0\nauthor: Lunar\n");

        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        SkillRegistry skillRegistry = new SkillRegistry();
        DropManager dropManager = new DropManager();
        PackManager packManager = new PackManager();
        packManager.loadAll(packsRoot, registry, skillRegistry, dropManager);

        PackCommand packCmd = new PackCommand(registry, tempDir);
        packCmd.setPackManager(packManager, skillRegistry, dropManager);
        assertEquals("pack", packCmd.name());

        Sender sender = new Sender(true);
        packCmd.execute(null, sender.proxy, "hhl pack", new String[]{"list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("beta_pack")));

        List<String> tabs = packCmd.tabComplete(null, sender.proxy, "pack", new String[]{"r"});
        assertEquals(List.of("reload"), tabs);
    }

    @Test
    void testItemCommandDirectly() {
        ItemDefinitionRegistry itemRegistry = new ItemDefinitionRegistry();
        itemRegistry.register(MythicItemDefinition.builder("lunar_sword")
                .material(org.bukkit.Material.DIAMOND_SWORD)
                .displayName("§bLunar Sword")
                .build());

        ItemCommand itemCmd = new ItemCommand(itemRegistry, null);
        assertEquals("item", itemCmd.name());

        Sender sender = new Sender(true);
        // Missing arguments
        itemCmd.execute(null, sender.proxy, "hhl item", new String[]{"give"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Usage: /hhl item give <player> <item_id> [amount]")));

        // Tab complete item id
        List<String> tabs = itemCmd.tabComplete(null, sender.proxy, "item", new String[]{"give", "player1", "lunar"});
        assertEquals(List.of("lunar_sword"), tabs);
    }

    @Test
    void testPinsCommandDirectly() {
        PinsCommand pinsCmd = new PinsCommand();
        assertEquals("pins", pinsCmd.name());
        assertTrue(pinsCmd.aliases().contains("pin"));

        Sender sender = new Sender(true);
        pinsCmd.execute(null, sender.proxy, "hhl pins", new String[]{"list"});
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Registered Pins")));
    }

    private static MobDefinition definition(String id) {
        return new MobDefinition(new MobDefinitionId(id), EntityType.IRON_GOLEM, id,
                null, Map.of(), Map.of(), List.of(), null, Set.of());
    }

    private static final class Sender {
        private final List<String> messages = new ArrayList<>();
        private final CommandSender proxy;

        private Sender(boolean permission) {
            this.proxy = (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (object, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permission;
                        case "sendMessage" -> {
                            if (args != null && args.length > 0 && args[0] != null) {
                                messages.add(args[0].toString());
                            }
                            yield null;
                        }
                        default -> null;
                    }
            );
        }
    }
}
