package vn.haohan.lunar.core.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;
import vn.haohan.lunar.core.command.commands.*;
import vn.haohan.lunar.core.features.boss.warden.ui.WardenBGMManager;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HaoHanCommandTest {

    @Test
    void testCommandDispatchingAndAliases() {
        HaoHanCommand cmd = new HaoHanCommand();
        Sender sender = new Sender(true);

        // 1. Root /hhl without args shows help
        boolean handled = cmd.execute(null, sender.proxy, "hhl", new String[]{});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("HaoHan Lunar Command")));

        // 2. Help subcommand shows help
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"help"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("HaoHan Lunar Command")));

        // 3. Invalid subcommand shows error
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"invalid_sub"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Lệnh con không hợp lệ")));

        // 4. Test tp subcommand
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"tp"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Chỉ có người chơi")));

        // 5. Test alias shortcut /tplunar directly
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "tplunar", new String[]{});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("Chỉ có người chơi")));

        // 6. Test warden spawn without world
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"warden", "spawn"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("chưa sẵn sàng") || m.contains("người chơi")));

        // 7. Test warden alias /spawnwarden shortcut
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "spawnwarden", new String[]{});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("chưa sẵn sàng") || m.contains("người chơi")));

        // 8. Test bgm list
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"bgm", "list"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("haohan:")));

        // 9. Test reload subcommand
        sender.messages.clear();
        handled = cmd.execute(null, sender.proxy, "hhl", new String[]{"reload"});
        assertTrue(handled);
        assertTrue(sender.messages.stream().anyMatch(m -> m.contains("thành công")));
    }

    @Test
    void testTabCompletionRootAndSubcommands() {
        HaoHanCommand cmd = new HaoHanCommand();
        Sender sender = new Sender(true);

        // Root completions (all subcommands are registered in HaoHanCommand constructor)
        List<String> rootTabs = cmd.tabComplete(null, sender.proxy, "hhl", new String[]{""});
        assertTrue(rootTabs.contains("tp"));
        assertTrue(rootTabs.contains("warden"));
        assertTrue(rootTabs.contains("bgm"));
        assertTrue(rootTabs.contains("item"));
        assertTrue(rootTabs.contains("pins"));
        assertTrue(rootTabs.contains("reload"));
        assertTrue(rootTabs.contains("pack"));
        assertTrue(rootTabs.contains("spawn"));
        assertTrue(rootTabs.contains("kill"));
        assertTrue(rootTabs.contains("info"));
        assertTrue(rootTabs.contains("signal"));
        assertTrue(rootTabs.contains("validate"));
        assertTrue(rootTabs.contains("trace"));
        assertTrue(rootTabs.contains("metrics"));
        assertTrue(rootTabs.contains("help"));

        // Prefix filter
        List<String> wTabs = cmd.tabComplete(null, sender.proxy, "hhl", new String[]{"w"});
        assertTrue(wTabs.contains("warden"));

        // Subcommand warden tab completion
        List<String> wardenTabs = cmd.tabComplete(null, sender.proxy, "hhl", new String[]{"warden", ""});
        assertTrue(wardenTabs.contains("spawn"));
        assertTrue(wardenTabs.contains("clear"));

        // Subcommand bgm tab completion
        List<String> bgmTabs = cmd.tabComplete(null, sender.proxy, "hhl", new String[]{"bgm", ""});
        assertTrue(bgmTabs.contains("play"));
        assertTrue(bgmTabs.contains("stop"));
        assertTrue(bgmTabs.contains("list"));

        List<String> bgmPlayTabs = cmd.tabComplete(null, sender.proxy, "hhl", new String[]{"bgm", "play", ""});
        for (String soundKey : WardenBGMManager.getCandidateBgmKeys()) {
            assertTrue(bgmPlayTabs.contains(soundKey));
        }

        // Without permission should return empty
        Sender noPermSender = new Sender(false);
        assertTrue(cmd.tabComplete(null, noPermSender.proxy, "hhl", new String[]{""}).isEmpty());
    }

    @Test
    void testHaoHanCommandMobSubcommandRouting() {
        HaoHanCommand cmd = new HaoHanCommand();
        MobDefinitionRegistry registry = new MobDefinitionRegistry();
        LunarMobManager manager = new LunarMobManager();

        cmd.registerCommand(new SpawnCommand(registry, (p, d) -> true));
        cmd.registerCommand(new KillCommand(manager, registry));
        cmd.registerCommand(new InfoCommand(registry, manager));

        Sender adminSender = new Sender(true);

        // 1. Calling via /hhl mob with no args shows help
        cmd.execute(null, adminSender.proxy, "hhl", new String[]{"mob"});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("HaoHan Lunar Command")));

        // 2. Calling via /hhl mob kill with no ID
        adminSender.messages.clear();
        cmd.execute(null, adminSender.proxy, "hhl", new String[]{"mob", "kill"});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("/hhl") && m.contains("kill <id>")));

        // 3. Calling via direct shortcut /hhl kill with no ID
        adminSender.messages.clear();
        cmd.execute(null, adminSender.proxy, "hhl", new String[]{"kill"});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("/hhl") && m.contains("kill <id>")));

        // 4. Calling via /hhl item give with missing args
        adminSender.messages.clear();
        cmd.execute(null, adminSender.proxy, "hhl", new String[]{"item", "give"});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("Usage: /hhl item give <player> <item_id> [amount]")));

        // 5. Calling via /hhl pins with no subargs
        adminSender.messages.clear();
        cmd.execute(null, adminSender.proxy, "hhl", new String[]{"pins"});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("Usage: /hhl pins <wand|add|remove")));

        // 6. Calling via legacy /lunarmob
        adminSender.messages.clear();
        cmd.execute(null, adminSender.proxy, "lunarmob", new String[]{});
        assertTrue(adminSender.messages.stream().anyMatch(m -> m.contains("HaoHan Lunar Command")));

        // 7. Sender without permission
        Sender noPermSender = new Sender(false);
        cmd.execute(null, noPermSender.proxy, "hhl", new String[]{"kill"});
        assertTrue(noPermSender.messages.stream().anyMatch(m -> m.contains("Bạn không có quyền")));
    }

    private static final class Sender {
        private final List<String> messages = new ArrayList<>();
        private final CommandSender proxy;

        private Sender(boolean permission) {
            this.proxy = (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (object, method, args) -> switch (method.getName()) {
                        case "sendMessage" -> {
                            if (args.length == 1 && args[0] instanceof String msg) {
                                messages.add(msg);
                            }
                            yield null;
                        }
                        case "hasPermission" -> permission;
                        case "isOp" -> permission;
                        case "getName" -> "TestSender";
                        default -> null;
                    }
            );
        }
    }
}
