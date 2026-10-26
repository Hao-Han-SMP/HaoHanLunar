package vn.haohan.lunar.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.command.commands.RobotCommand;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RobotCommandTest {

    @Test
    void testCommandMetadata() {
        RobotCommand cmd = new RobotCommand();
        assertEquals("robot", cmd.name());
        assertTrue(cmd.aliases().contains("quadruped"));
        assertEquals("/hhl robot <spawn|tablet>", cmd.usage());
    }

    @Test
    void testNonPlayerSender() {
        RobotCommand cmd = new RobotCommand();
        CommandSender sender = mock(CommandSender.class);
        boolean res = cmd.execute(null, sender, "robot", new String[]{"spawn"});
        assertTrue(res);
    }

    @Test
    void testTabCompletion() {
        RobotCommand cmd = new RobotCommand();
        Player player = mock(Player.class);
        List<String> tabs = cmd.tabComplete(null, player, "robot", new String[]{""});
        assertTrue(tabs.contains("spawn"));
        assertTrue(tabs.contains("tablet"));

        List<String> sTabs = cmd.tabComplete(null, player, "robot", new String[]{"s"});
        assertTrue(sTabs.contains("spawn"));
        assertFalse(sTabs.contains("tablet"));
    }
}
