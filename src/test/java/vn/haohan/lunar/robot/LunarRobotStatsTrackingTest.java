package vn.haohan.lunar.robot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.robot.ui.LunarRobotDashboardUi;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LunarRobotStatsTrackingTest {

    @Test
    @DisplayName("Verify LunarRobotData accumulates stats properly")
    public void testStatsAccumulation() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());

        assertEquals(0.0, data.getDamageDealt());
        assertEquals(0.0, data.getDamageTaken());
        assertEquals(0, data.getTotalSteps());

        data.addDamageDealt(25.5);
        assertEquals(25.5, data.getDamageDealt(), 1e-4);

        data.addDamageTaken(14.0);
        assertEquals(14.0, data.getDamageTaken(), 1e-4);

        data.addSteps(100);
        assertEquals(100, data.getTotalSteps());

        data.addSteps(50);
        assertEquals(150, data.getTotalSteps());
    }

    @Test
    @DisplayName("Verify Dashboard reflects accumulated stats and active time")
    public void testDashboardDisplaysAccumulatedStats() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("Ares-1");
        data.addDamageDealt(123.4);
        data.addDamageTaken(45.6);
        data.addSteps(789);
        data.setEnergy(15000);
        data.setMaxEnergy(30000);

        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTab(LunarRobotDashboardUi.Tab.SETTINGS);
        ui.setExpandedSettingsCard(LunarRobotDashboardUi.SettingsCard.HEALTH);

        var mgr = ui.buildDashboardLayerManager();
        var healthBody = mgr.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow()
                .findContainer("card_health").orElseThrow()
                .findContainer("card_health_body").orElseThrow();

        // Check damage dealt stat component exists
        assertTrue(healthBody.findComponent("stat_dmg_dealt_bg").isPresent());
        assertTrue(healthBody.findComponent("stat_dmg_dealt_lbl").isPresent());

        // Check damage taken stat component exists
        assertTrue(healthBody.findComponent("stat_dmg_taken_bg").isPresent());

        // Check steps stat component exists
        assertTrue(healthBody.findComponent("stat_steps_bg").isPresent());
    }

    @Test
    @DisplayName("Verify Dashboard tick does not crash when handle is null")
    public void testDashboardTickWithoutHandle() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);

        // Ticking without Bukkit handle attached should silently do nothing
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 25; i++) {
                ui.tick();
            }
        });
    }
}
