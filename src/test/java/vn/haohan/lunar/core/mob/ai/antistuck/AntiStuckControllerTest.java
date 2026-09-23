package vn.haohan.lunar.api.mob.ai.antistuck;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.ai.antistuck.AntiStuckController;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AntiStuckControllerTest {

    private World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (p, m, a) -> null);
    }

    @Test
    @DisplayName("AntiStuckController does nothing when target is null")
    void testNoTargetDoesNotTrigger() {
        World world = createMockWorld();
        AntiStuckController controller = new AntiStuckController(60, 0.5);

        Location loc = new Location(world, 10, 64, 10);
        for (int i = 0; i < 100; i++) {
            Optional<AntiStuckController.AntiStuckAction> action = controller.tick(loc, null, i);
            assertTrue(action.isEmpty());
        }
        assertEquals(0, controller.getStuckTicks());
    }

    @Test
    @DisplayName("AntiStuckController cycles through stages when stuck")
    void testProgressiveStuckLevels() {
        World world = createMockWorld();
        AntiStuckController controller = new AntiStuckController(60, 0.5);

        Location mobLoc = new Location(world, 0, 64, 0);
        Location targetLoc = new Location(world, 10, 64, 0);

        // Tick 0 initializes lastLocation
        controller.tick(mobLoc, targetLoc, 0);

        // Run 59 ticks staying at the exact same location
        for (int i = 1; i < 60; i++) {
            Optional<AntiStuckController.AntiStuckAction> action = controller.tick(mobLoc, targetLoc, i);
            assertTrue(action.isEmpty(), "Should not trigger before reaching threshold at tick " + i);
        }

        // Tick 60: Stage 1 trigger (JUMP)
        Optional<AntiStuckController.AntiStuckAction> stage1 = controller.tick(mobLoc, targetLoc, 60);
        assertTrue(stage1.isPresent());
        assertEquals(AntiStuckController.AntiStuckLevel.LEVEL_1_JUMP, stage1.get().level());
        assertEquals(0.5, stage1.get().suggestedVelocity().getY(), 0.001);

        // Next 60 ticks still stuck: Stage 2 trigger (RECALCULATE)
        for (int i = 61; i < 120; i++) {
            assertTrue(controller.tick(mobLoc, targetLoc, i).isEmpty());
        }
        Optional<AntiStuckController.AntiStuckAction> stage2 = controller.tick(mobLoc, targetLoc, 120);
        assertTrue(stage2.isPresent());
        assertEquals(AntiStuckController.AntiStuckLevel.LEVEL_2_RECALCULATE, stage2.get().level());

        // Next 60 ticks still stuck: Stage 3 trigger (CLEAR_OBSTRUCTION)
        for (int i = 121; i < 180; i++) {
            assertTrue(controller.tick(mobLoc, targetLoc, i).isEmpty());
        }
        Optional<AntiStuckController.AntiStuckAction> stage3 = controller.tick(mobLoc, targetLoc, 180);
        assertTrue(stage3.isPresent());
        assertEquals(AntiStuckController.AntiStuckLevel.LEVEL_3_CLEAR_OBSTRUCTION, stage3.get().level());

        // Next 60 ticks still stuck: Stage 4 trigger (TELEPORT)
        for (int i = 181; i < 240; i++) {
            assertTrue(controller.tick(mobLoc, targetLoc, i).isEmpty());
        }
        Optional<AntiStuckController.AntiStuckAction> stage4 = controller.tick(mobLoc, targetLoc, 240);
        assertTrue(stage4.isPresent());
        assertEquals(AntiStuckController.AntiStuckLevel.LEVEL_4_TELEPORT, stage4.get().level());
        // Teleport should suggest moving 3 blocks towards (10, 64, 0) from (0, 64, 0) -> (3, 64, 0)
        assertEquals(3.0, stage4.get().suggestedTeleportLocation().getX(), 0.01);
        assertEquals(64.0, stage4.get().suggestedTeleportLocation().getY(), 0.01);
    }

    @Test
    @DisplayName("AntiStuckController resets when mob moves beyond threshold")
    void testResetOnMovement() {
        World world = createMockWorld();
        AntiStuckController controller = new AntiStuckController(60, 0.5);

        Location loc1 = new Location(world, 0, 64, 0);
        Location targetLoc = new Location(world, 20, 64, 0);

        // Start tracking
        controller.tick(loc1, targetLoc, 0);

        // Stuck for 40 ticks
        for (int i = 1; i <= 40; i++) {
            controller.tick(loc1, targetLoc, i);
        }
        assertEquals(40, controller.getStuckTicks());

        // Mob moves 1.5 blocks (> 0.5)
        Location loc2 = new Location(world, 1.5, 64, 0);
        Optional<AntiStuckController.AntiStuckAction> action = controller.tick(loc2, targetLoc, 41);

        assertTrue(action.isEmpty());
        assertEquals(0, controller.getStuckTicks(), "Stuck ticks should be reset to 0 after movement");
        assertEquals(1, controller.getCurrentStage(), "Stage should reset to 1 after movement");
    }
}
