package vn.haohan.lunar.robot.ui;

import org.bukkit.boss.BarColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.robot.RobotTask;

import static org.junit.jupiter.api.Assertions.*;

class LunarRobotBossBarIntegrationTest {

    @Test
    @DisplayName("Test building status card for SPEED module boost and overheat")
    void testSpeedModuleCardFormatting() {
        String boostCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "MODULE TỐC HÀNH",
                "BỨT TỐC TỐI ĐA",
                "«««« NHIỆT ĐỘ: 65% • 2,450 EU »»»»",
                "§b",
                "§e§l",
                "§e"
        );

        assertNotNull(boostCard);
        assertTrue(boostCard.contains(LunarModuleBossBarRenderer.CARD_BG_CHAR));
        assertTrue(boostCard.contains("TỐC HÀNH"));
        assertTrue(boostCard.contains("BỨT TỐC"));
        assertTrue(boostCard.contains("65%"));

        String overheatCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "CẢNH BÁO ĐỘNG CƠ",
                "QUÁ NHIỆT (100%)",
                "«««« HẠ NHIỆT: 85% • THẢ SPACE »»»»",
                "§c",
                "§c§l",
                "§c"
        );

        assertTrue(overheatCard.contains("QUÁ NHIỆT"));
        assertTrue(overheatCard.contains("HẠ NHIỆT"));
    }

    @Test
    @DisplayName("Test building status card for THRUST module flight and refueling")
    void testThrustModuleCardFormatting() {
        String thrustCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "MODULE ĐẨY PHẢN LỰC",
                "BAY PHẢN LỰC",
                "«««« NHIÊN LIỆU: 80% • 3,200 EU »»»»",
                "§e",
                "§a§l",
                "§a"
        );

        assertTrue(thrustCard.contains("ĐẨY PHẢN LỰC"));
        assertTrue(thrustCard.contains("BAY PHẢN LỰC"));
        assertTrue(thrustCard.contains("80%"));
    }

    @Test
    @DisplayName("Test building status card for COMBAT and IDLE tasks")
    void testCombatAndIdleCardFormatting() {
        String combatCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "MODULE CHIẾN ĐẤU",
                "TẤN CÔNG: Zombie",
                "«««« 4,500 EU • ❤ 100% »»»»",
                "§c",
                "§c§l",
                "§e"
        );

        assertTrue(combatCard.contains("CHIẾN ĐẤU"));
        assertTrue(combatCard.contains("Zombie"));

        String idleCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "ROBOT 4 CHÂN",
                "THEO DÕI / NGHỈ",
                "«««« 5,000 EU • ❤ 100% »»»»",
                "§7",
                "§f§l",
                "§a"
        );

        assertTrue(idleCard.contains("ROBOT 4 CHÂN"));
        assertTrue(idleCard.contains("THEO DÕI / NGHỈ"));
    }
}
