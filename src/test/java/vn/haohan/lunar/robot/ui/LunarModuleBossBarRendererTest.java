package vn.haohan.lunar.robot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LunarModuleBossBarRendererTest {

    @Test
    @DisplayName("Test negative space sequence generator correctly sums to target pixels")
    void testNegativeSpaceSequenceSum() {
        int target = -216;
        String seq = LunarModuleBossBarRenderer.buildSpaceSequence(target);
        assertNotNull(seq);
        assertEquals(LunarModuleBossBarRenderer.SPACE_NEG_216, seq);

        // Test combined negative space calculation
        int customNeg = -55; // -32, -16, -4, -2, -1
        String customSeq = LunarModuleBossBarRenderer.buildSpaceSequence(customNeg);
        assertTrue(customSeq.contains(LunarModuleBossBarRenderer.SPACE_NEG_32));
        assertTrue(customSeq.contains(LunarModuleBossBarRenderer.SPACE_NEG_16));
    }

    @Test
    @DisplayName("Test positive space sequence generator")
    void testPositiveSpaceSequence() {
        int target = 45; // 32, 8, 4, 1
        String seq = LunarModuleBossBarRenderer.buildSpaceSequence(target);
        assertNotNull(seq);
        assertFalse(seq.isEmpty());
    }

    @Test
    @DisplayName("Test Minecraft text width calculation stripping color codes")
    void testTextWidthCalculation() {
        // Plain string "ROBOT" -> 5 chars * 6px approx = ~30px
        int widthPlain = LunarModuleBossBarRenderer.calculateTextWidth("ROBOT");
        assertTrue(widthPlain > 20 && widthPlain < 40, "Width should be around 30px, was " + widthPlain);

        // Color coded string "§b§lROBOT" should have color codes stripped, plus bold bonus
        int widthColored = LunarModuleBossBarRenderer.calculateTextWidth("§b§lROBOT");
        assertTrue(widthColored >= widthPlain, "Bold colored text should be >= plain text");
    }

    @Test
    @DisplayName("Test text truncation within max width")
    void testTruncate() {
        String longText = "Mục tiêu siêu dài vượt quá kích thước hiển thị của bossbar card 216px";
        String truncated = LunarModuleBossBarRenderer.truncate(longText, 100);
        assertTrue(truncated.endsWith("..."));
        assertTrue(LunarModuleBossBarRenderer.calculateTextWidth(truncated) <= 100);
    }

    @Test
    @DisplayName("Test renderCard produces valid Adventure Component with background and text")
    void testRenderCardComponent() {
        Component card = LunarModuleBossBarRenderer.renderCard(
                "MODULE TỐC HÀNH",
                "BỨT TỐC TỐI ĐA (BOOST)",
                "«««« NHIỆT ĐỘ: 68% • PIN: 2,450 EU »»»»",
                "§b",
                "§f§l",
                "§e"
        );

        assertNotNull(card);
        String plain = PlainTextComponentSerializer.plainText().serialize(card);
        assertTrue(plain.contains("MODULE TỐC HÀNH") || plain.contains("BỨT TỐC TỐI ĐA"));
        assertTrue(plain.contains(LunarModuleBossBarRenderer.CARD_BG_CHAR));
    }

    @Test
    @DisplayName("Test renderCardLegacyString for Bukkit setTitle support")
    void testRenderCardLegacyString() {
        String legacy = LunarModuleBossBarRenderer.renderCardLegacyString(
                "TỐC HÀNH",
                "BỨT TỐC",
                "«««« 68% »»»»",
                "§b",
                "§f§l",
                "§e"
        );

        assertNotNull(legacy);
        assertTrue(legacy.contains(LunarModuleBossBarRenderer.CARD_BG_CHAR));
        assertTrue(legacy.contains("TỐC HÀNH"));
        assertTrue(legacy.contains("BỨT TỐC"));
    }

    @Test
    @DisplayName("Test render3LineCard produces Component with 3 font layers and cursor rewinds")
    void testRender3LineCard() {
        Component card = LunarModuleBossBarRenderer.render3LineCard(
                "MODULE TỐC HÀNH",
                "SẴN SÀNG [SPACE]",
                "«««« ỔN ĐỊNH (0%) • 2,450 EU »»»»",
                "§b",
                "§a§l",
                "§e"
        );

        assertNotNull(card);
        String plain = PlainTextComponentSerializer.plainText().serialize(card);
        assertTrue(plain.contains("MODULE TỐC HÀNH"));
        assertTrue(plain.contains("SẴN SÀNG [SPACE]"));
        assertTrue(plain.contains("ỔN ĐỊNH"));
        assertTrue(plain.contains(LunarModuleBossBarRenderer.CARD_BG_CHAR));
    }

    @Test
    @DisplayName("Test render3LineCard does not truncate long indicator fuel text")
    void testRender3LineCardFuelNotTruncated() {
        Component card = LunarModuleBossBarRenderer.render3LineCard(
                "MODULE ĐẨY PHẢN LỰC",
                "SẴN SÀNG BAY [SPACE]",
                "«««« NHIÊN LIỆU: 100% • 20,286 EU »»»»",
                "§b",
                "§f",
                "§e"
        );

        assertNotNull(card);
        String plain = PlainTextComponentSerializer.plainText().serialize(card);
        assertTrue(plain.contains("«««« NHIÊN LIỆU: 100% • 20,286 EU »»»»"), "Full indicator text must be preserved without ellipsis");
        assertFalse(plain.contains("..."), "No ellipsis should be present when text fits");
    }

    @Test
    @DisplayName("Test toUpperCasePreservingColors converts text while keeping section codes lowercase")
    void testToUpperCasePreservingColors() {
        String input = "§a§l[Chiến đấu] §eĐã tiêu diệt: §cNhện hang§a!";
        String result = LunarModuleBossBarRenderer.toUpperCasePreservingColors(input);
        assertEquals("§a§l[CHIẾN ĐẤU] §eĐÃ TIÊU DIỆT: §cNHỆN HANG§a!", result);
    }

    @Test
    @DisplayName("Test render3LineCard with legacy colors and special icons strips raw section characters from text nodes")
    void testRender3LineCardWithLegacyColorsAndIcons() {
        Component card = LunarModuleBossBarRenderer.render3LineCard(
                "THÔNG BÁO ROBOT",
                "§a§l[CHIẾN ĐẤU] §aĐã tiêu diệt: §eNhện hang§a!",
                "«««« §a♥ 100% • 10,000 EU »»»»",
                "§c",
                "§e§l",
                "§c"
        );

        assertNotNull(card);
        String plain = PlainTextComponentSerializer.plainText().serialize(card);
        // Plain text serialized from Component should NOT contain raw '§' characters
        assertFalse(plain.contains("§"), "Adventure Component text nodes must not contain raw § characters");
        assertTrue(plain.contains("THÔNG BÁO ROBOT"));
        assertTrue(plain.contains("ĐÃ TIÊU DIỆT"));
        assertTrue(plain.contains("♥ 100%"));
        assertTrue(plain.contains("10,000 EU"));
    }
}

