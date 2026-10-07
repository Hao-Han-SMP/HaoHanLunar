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
                "«««« 4,500 EU • ♥ 100% »»»»",
                "§c",
                "§c§l",
                "§e"
        );

        assertTrue(combatCard.contains("CHIẾN ĐẤU"));
        assertTrue(combatCard.contains("Zombie"));

        String idleCard = LunarModuleBossBarRenderer.renderCardLegacyString(
                "ROBOT 4 CHÂN",
                "THEO DÕI / NGHỈ",
                "«««« 5,000 EU • ♥ 100% »»»»",
                "§7",
                "§f§l",
                "§a"
        );

        assertTrue(idleCard.contains("ROBOT 4 CHÂN"));
        assertTrue(idleCard.contains("THEO DÕI / NGHỈ"));
    }

    @Test
    @DisplayName("Test 3LineCard renders new Speed telemetry with temp icon and m/s within limits")
    void testSpeedModule3LineCardTelemetry() {
        net.kyori.adventure.text.Component card = LunarModuleBossBarRenderer.render3LineCard(
                "MODULE TỐC HÀNH",
                "BỨT TỐC TỐI ĐA",
                "«««« ♨ 75% • 14.5 m/s • 8,500 EU »»»»",
                "§b",
                "§e§l",
                "§e"
        );

        assertNotNull(card);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(card);
        assertTrue(plain.contains("♨ 75%"));
        assertTrue(plain.contains("14.5 m/s"));
        assertTrue(plain.contains("8,500 EU"));
        assertFalse(plain.contains("..."), "Speed indicator text must fit comfortably without truncation");
    }

    @Test
    @DisplayName("Test 3LineCard renders new Thrust telemetry with altitude ▲ within limits")
    void testThrustModule3LineCardAltitudeTelemetry() {
        net.kyori.adventure.text.Component card = LunarModuleBossBarRenderer.render3LineCard(
                "MODULE ĐẨY PHẢN LỰC",
                "BAY PHẢN LỰC",
                "«« NL: 85% • ▲ 128m • 18,500 EU »»",
                "§e",
                "§a§l",
                "§a"
        );

        assertNotNull(card);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(card);
        assertTrue(plain.contains("NL: 85%"));
        assertTrue(plain.contains("▲ 128m"));
        assertTrue(plain.contains("18,500 EU"));
        assertFalse(plain.contains("..."), "Thrust indicator text must fit comfortably without truncation");
    }

    @Test
    @DisplayName("Test 3LineCard renders Notification without raw section codes or missing glyphs")
    void testNotification3LineCardCleanFormatting() {
        net.kyori.adventure.text.Component card = LunarModuleBossBarRenderer.render3LineCard(
                "THÔNG BÁO ROBOT",
                "§a§l[CHIẾN ĐẤU] §aĐã tiêu diệt: §eNhện hang§a!",
                "«««« §a♥ 100% • 10,000 EU »»»»",
                "§c",
                "§e§l",
                "§c"
        );

        assertNotNull(card);
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(card);
        assertFalse(plain.contains("§"), "Raw § characters must be completely eliminated");
        assertTrue(plain.contains("THÔNG BÁO ROBOT"));
        assertTrue(plain.contains("ĐÃ TIÊU DIỆT"));
        assertTrue(plain.contains("NHỆN HANG"));
        assertTrue(plain.contains("♥ 100%"));
    }
}

