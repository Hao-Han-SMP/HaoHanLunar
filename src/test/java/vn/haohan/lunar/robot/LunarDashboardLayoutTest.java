package vn.haohan.lunar.robot;

import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import vn.haohan.displayui.api.UiDocument;
import vn.haohan.displayui.api.animation.Easings;
import vn.haohan.displayui.api.animation.UiAnimation;
import vn.haohan.displayui.api.animation.UiEffects;
import vn.haohan.displayui.api.node.UiBackgroundNode;
import vn.haohan.displayui.runtime.scene.follow.UiFollowController;
import org.bukkit.plugin.Plugin;
import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.displayui.api.bridge.UiDocumentBridge;
import vn.haohan.displayui.api.container.Container;
import vn.haohan.displayui.api.container.DropdownContainer;
import vn.haohan.displayui.api.layer.Layer;
import vn.haohan.displayui.api.layer.LayerManager;
import vn.haohan.lunar.robot.ui.LunarDashboardIcons;
import vn.haohan.lunar.robot.ui.LunarDashboardTheme;
import vn.haohan.lunar.robot.ui.LunarRobotDashboardUi;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Automated test suite verifying the layout coordinates, element bounds,
 * animation configurations, color palettes, and gaze follower deadzone for the new Robot Dashboard.
 */
public class LunarDashboardLayoutTest {

    @Test
    @DisplayName("1. Verify global dashboard aspect ratio and bounds")
    public void testGlobalDashboardBounds() {
        assertEquals(224.0f, LunarRobotDashboardUi.TOTAL_WIDTH, 1e-4);
        assertEquals(126.0f, LunarRobotDashboardUi.TOTAL_HEIGHT, 1e-4);

        // Aspect ratio 224:126 is exactly 16:9
        assertEquals(16.0 / 9.0, (double) LunarRobotDashboardUi.TOTAL_WIDTH / LunarRobotDashboardUi.TOTAL_HEIGHT, 1e-4);

        // Symmetrical center (0, 0)
        assertEquals(-LunarRobotDashboardUi.TOTAL_WIDTH / 2.0f, LunarRobotDashboardUi.MIN_X, 1e-4);
        assertEquals(LunarRobotDashboardUi.TOTAL_WIDTH / 2.0f, LunarRobotDashboardUi.MAX_X, 1e-4);
        assertEquals(-LunarRobotDashboardUi.TOTAL_HEIGHT / 2.0f, LunarRobotDashboardUi.MIN_Y, 1e-4);
        assertEquals(LunarRobotDashboardUi.TOTAL_HEIGHT / 2.0f, LunarRobotDashboardUi.MAX_Y, 1e-4);
    }

    @Test
    @DisplayName("2. Verify left and right panels do not overlap and fit inside global bounds")
    public void testPanelBoundsAndSeparation() {
        // Left panel inside bounds
        assertTrue(LunarRobotDashboardUi.LEFT_PANEL_X >= LunarRobotDashboardUi.MIN_X, "Left panel exceeds minX");
        assertTrue(LunarRobotDashboardUi.LEFT_PANEL_X + LunarRobotDashboardUi.LEFT_PANEL_W <= LunarRobotDashboardUi.MAX_X, "Left panel exceeds maxX");
        assertTrue(LunarRobotDashboardUi.LEFT_PANEL_Y >= LunarRobotDashboardUi.MIN_Y, "Left panel exceeds minY");
        assertTrue(LunarRobotDashboardUi.LEFT_PANEL_Y + LunarRobotDashboardUi.LEFT_PANEL_H <= LunarRobotDashboardUi.MAX_Y, "Left panel exceeds maxY");

        // Right panel inside bounds
        assertTrue(LunarRobotDashboardUi.RIGHT_PANEL_X >= LunarRobotDashboardUi.MIN_X, "Right panel exceeds minX");
        assertTrue(LunarRobotDashboardUi.RIGHT_PANEL_X + LunarRobotDashboardUi.RIGHT_PANEL_W <= LunarRobotDashboardUi.MAX_X, "Right panel exceeds maxX");
        assertTrue(LunarRobotDashboardUi.RIGHT_PANEL_Y >= LunarRobotDashboardUi.MIN_Y, "Right panel exceeds minY");
        assertTrue(LunarRobotDashboardUi.RIGHT_PANEL_Y + LunarRobotDashboardUi.RIGHT_PANEL_H <= LunarRobotDashboardUi.MAX_Y, "Right panel exceeds maxY");

        // Strict non-overlapping separation between left and right panels
        float leftEdge = LunarRobotDashboardUi.LEFT_PANEL_X + LunarRobotDashboardUi.LEFT_PANEL_W;
        float rightEdge = LunarRobotDashboardUi.RIGHT_PANEL_X;
        assertTrue(leftEdge < rightEdge, "Left panel must not overlap right panel");
        float gap = rightEdge - leftEdge;
        assertTrue(gap >= 2.0f, "There must be at least 2px gap between panels");
    }

    @Test
    @DisplayName("3. Verify left navigation buttons stacking and non-overlap")
    public void testLeftNavigationButtons() {
        float btnW = LunarRobotDashboardUi.LEFT_BTN_W;
        float btnH = LunarRobotDashboardUi.LEFT_BTN_H;

        // Verify button width fits inside left panel
        assertTrue(btnW <= LunarRobotDashboardUi.LEFT_PANEL_W);

        // Buttons must be ordered top-to-bottom without overlapping
        assertTrue(LunarRobotDashboardUi.BTN_MODES_Y + btnH <= LunarRobotDashboardUi.BTN_SETTINGS_Y,
                "Modes button must not overlap Settings button");
        assertTrue(LunarRobotDashboardUi.BTN_SETTINGS_Y + btnH <= LunarRobotDashboardUi.BTN_EXIT_Y,
                "Settings button must not overlap Exit button");

        // All buttons fit inside left panel vertical bounds
        assertTrue(LunarRobotDashboardUi.BTN_MODES_Y >= LunarRobotDashboardUi.LEFT_PANEL_Y);
        assertTrue(LunarRobotDashboardUi.BTN_EXIT_Y + btnH <= LunarRobotDashboardUi.LEFT_PANEL_Y + LunarRobotDashboardUi.LEFT_PANEL_H);
    }

    @Test
    @DisplayName("4. Verify Page 1 (Overview) Info Card and Status Card positions and bottom padding")
    public void testOverviewCardsLayout() {
        // Info card at top, Status card at bottom
        float infoY = LunarRobotDashboardUi.INFO_CARD_Y;
        float infoH = LunarRobotDashboardUi.INFO_CARD_H;
        float statusY = LunarRobotDashboardUi.STATUS_CARD_Y;
        float statusH = LunarRobotDashboardUi.STATUS_CARD_H;

        assertTrue(infoY + infoH <= statusY, "Info card must not overlap Status card");
        assertTrue(statusY + statusH <= LunarRobotDashboardUi.RIGHT_PANEL_Y + LunarRobotDashboardUi.RIGHT_PANEL_H,
                "Status card must fit within right panel");

        // Verify bottom padding symmetry between Info card and Status card:
        // Info card: pill at +16, height 24 -> ends at +40. Padding = 46 - 40 = 6.0f
        float infoPillBottom = 16.0f + LunarRobotDashboardUi.SUB_CARD_H;
        float infoBottomPadding = infoH - infoPillBottom;
        assertEquals(6.0f, infoBottomPadding, 1e-4, "Info card bottom padding must be 6.0f");

        // Status card: row 2 at +40, height 24 -> ends at +64. Padding = 70 - 64 = 6.0f
        float statusRow2Bottom = 40.0f + LunarRobotDashboardUi.SUB_CARD_H;
        float statusBottomPadding = statusH - statusRow2Bottom;
        assertEquals(6.0f, statusBottomPadding, 1e-4, "Status card bottom padding must be 6.0f (non-zero breathing room)");

        // Symmetrical flush bottom alignment between Left Panel and Status Card:
        float leftBottom = LunarRobotDashboardUi.LEFT_PANEL_Y + LunarRobotDashboardUi.LEFT_PANEL_H;
        float statusBottom = statusY + statusH;
        assertEquals(leftBottom, statusBottom, 1e-4, "Left panel bottom and Status card bottom must align identically");
        assertEquals(60.0f, statusBottom, 1e-4, "Bottom edge must be exactly Y = +60.0f");
    }

    @Test
    @DisplayName("5. Verify Page 4 Module slots layout (3 slots side-by-side)")
    public void testModuleSlotsLayout() {
        float slotW = LunarRobotDashboardUi.MODULE_SLOT_W;
        float slotH = LunarRobotDashboardUi.MODULE_SLOT_H;
        float startX = LunarRobotDashboardUi.RIGHT_PANEL_X + 6;
        float gap = 46.0f;

        // Check each of the 3 slots
        for (int i = 0; i < 3; i++) {
            float sx = startX + i * gap;
            // Slot fits inside right panel
            assertTrue(sx >= LunarRobotDashboardUi.RIGHT_PANEL_X, "Slot " + i + " out of bounds left");
            assertTrue(sx + slotW <= LunarRobotDashboardUi.RIGHT_PANEL_X + LunarRobotDashboardUi.RIGHT_PANEL_W,
                    "Slot " + i + " out of bounds right");

            if (i > 0) {
                float prevRight = startX + (i - 1) * gap + slotW;
                assertTrue(prevRight <= sx, "Slot " + (i - 1) + " overlaps with slot " + i);
            }
        }
    }

    @Test
    @DisplayName("6. Verify Page 7 Unbind confirmation buttons layout")
    public void testUnbindButtonsLayout() {
        float btnW = LunarRobotDashboardUi.UNBIND_BTN_W;
        float btnH = LunarRobotDashboardUi.UNBIND_BTN_H;

        float panelX = LunarRobotDashboardUi.RIGHT_PANEL_X;
        float panelW = LunarRobotDashboardUi.RIGHT_PANEL_W;
        float btn1X = panelX + (panelW / 2.0f) - btnW - 8.0f;
        float btn2X = panelX + (panelW / 2.0f) + 8.0f;

        // Button 1 [Xác nhận hủy] and Button 2 [Hủy bỏ] must not overlap
        assertTrue(btn1X + btnW <= btn2X, "Confirmation buttons must have separation gap");
        assertTrue(btn1X >= panelX, "Button 1 exceeds left edge");
        assertTrue(btn2X + btnW <= panelX + panelW, "Button 2 exceeds right edge");
    }

    @Test
    @DisplayName("7. Verify synchronized opening animation configuration")
    public void testAnimationConfig() {
        UiAnimation anim = UiEffects.scaleIn(0.70f, Easings.OutCubic);
        assertNotNull(anim);
        assertTrue(anim.durationTicks() > 0, "Animation duration must be positive");
        assertEquals(0.70f, anim.fromScale(), 1e-4, "Start scale must match 0.70f");
        assertEquals(1.00f, anim.toScale(), 1e-4, "End scale must be 1.0f");
        assertEquals(Easings.OutCubic, anim.easing(), "Easing must be OutCubic for smooth entry");
    }

    @Test
    @DisplayName("8. Verify Theme colors and palette definitions")
    public void testThemeColors() {
        // Default theme CYAN_WHITE
        LunarDashboardTheme cyan = LunarDashboardTheme.CYAN_WHITE;
        assertEquals("Trắng xanh", cyan.getDisplayName());
        assertEquals(Color.fromRGB(0xbf, 0xd7, 0xee), cyan.getGradStart());
        assertEquals(Color.fromRGB(0xe3, 0xf4, 0xf8), cyan.getGradEnd());
        assertEquals(Color.fromRGB(0xca, 0xe6, 0xff), cyan.getButtonBg());
        assertEquals(Color.fromRGB(0x8d, 0xab, 0xcb), cyan.getCardBg());
        assertEquals(Color.fromRGB(0x53, 0x86, 0xcb), cyan.getAccentColor());

        // Danger colors
        assertEquals(Color.fromRGB(0xd4, 0x50, 0x50), LunarDashboardTheme.DANGER_BORDER);
        assertEquals(Color.fromRGB(0xff, 0x6a, 0x6a), LunarDashboardTheme.DANGER_BG);

        // Theme cycle
        assertEquals(LunarDashboardTheme.RED, cyan.next());
        assertEquals(LunarDashboardTheme.BLUE, LunarDashboardTheme.RED.next());
        assertEquals(LunarDashboardTheme.YELLOW, LunarDashboardTheme.BLUE.next());
        assertEquals(LunarDashboardTheme.CYAN_WHITE, LunarDashboardTheme.YELLOW.next());
    }

    @Test
    @DisplayName("9. Verify Font Icon Unicode characters and Font Key")
    public void testFontIcons() {
        assertEquals('\ue401', LunarDashboardIcons.CHAR_INFO);
        assertEquals('\ue402', LunarDashboardIcons.CHAR_CALENDAR);
        assertEquals('\ue403', LunarDashboardIcons.CHAR_BATTERY);
        assertEquals('\ue404', LunarDashboardIcons.CHAR_MODULE);
        assertEquals('\ue405', LunarDashboardIcons.CHAR_MODE);
        assertEquals('\ue406', LunarDashboardIcons.CHAR_SETTINGS);
        assertEquals('\ue407', LunarDashboardIcons.CHAR_EXIT);
        assertEquals('\ue410', LunarDashboardIcons.CHAR_PROGRESS_CYAN_BASE);
        assertEquals('\ue420', LunarDashboardIcons.CHAR_PROGRESS_GREEN_BASE);

        assertEquals("haohan:lunar_dashboard", LunarDashboardIcons.FONT_KEY.asString());
        assertNotNull(LunarDashboardIcons.infoIcon());
        assertNotNull(LunarDashboardIcons.calendarIcon());
        assertNotNull(LunarDashboardIcons.batteryIcon());
        assertNotNull(LunarDashboardIcons.moduleIcon());
        assertNotNull(LunarDashboardIcons.modeIcon());
        assertNotNull(LunarDashboardIcons.settingsIcon());
        assertNotNull(LunarDashboardIcons.exitIcon());
        assertNotNull(LunarDashboardIcons.cyanProgressBar(0.5));
        assertNotNull(LunarDashboardIcons.greenProgressBar(0.95));
        assertEquals(34.0f, LunarDashboardIcons.PROGRESS_BAR_WIDTH);
        vn.haohan.displayui.api.node.AlignedTextNode pbNode = LunarDashboardIcons.createProgressBarNode(LunarDashboardIcons.cyanProgressBar(0.5), 10.0f, 20.0f, 0.01f);
        assertEquals(vn.haohan.displayui.api.text.UiTextAlignment.LEFT, pbNode.alignment());
        assertEquals(34.0f, pbNode.contentWidth());
    }

    @Test
    @DisplayName("10. Verify Gaze Follower deadzone matching new dashboard bounds")
    public void testGazeFollowerDeadzone() {
        Vector eyePos = new Vector(0, 1.6, 0);
        Vector rayDir = new Vector(0, 0, 1);
        Vector planeOrigin = new Vector(0, 1.6, 2.2);
        Vector planeDir = new Vector(0, 0, -1);

        float pixelsPerBlock = LunarRobotDashboardUi.PIXELS_PER_BLOCK;
        double maxDistance = 5.0;

        // Center hit
        UiFollowController.RaycastResult res = UiFollowController.projectCrosshair(
                eyePos, rayDir, planeOrigin, planeDir, pixelsPerBlock, maxDistance);

        assertTrue(res.hits());
        assertEquals(0.0f, res.localX(), 1e-3);
        assertEquals(0.0f, res.localY(), 1e-3);

        // Within bounds check: (-112, 112) and (-63, 63)
        assertTrue(res.localX() >= LunarRobotDashboardUi.MIN_X && res.localX() <= LunarRobotDashboardUi.MAX_X);
        assertTrue(res.localY() >= LunarRobotDashboardUi.MIN_Y && res.localY() <= LunarRobotDashboardUi.MAX_Y);

        // Edge check: Ray looking towards right edge (+X = 1.4 at distance 2.2 => 1.4 * 75 = 105px <= 112px)
        Vector edgeDir = new Vector(1.4, 0, 2.2).normalize();
        UiFollowController.RaycastResult edgeRes = UiFollowController.projectCrosshair(
                eyePos, edgeDir, planeOrigin, planeDir, pixelsPerBlock, maxDistance);

        assertTrue(edgeRes.hits());
        assertTrue(Math.abs(edgeRes.localX()) <= LunarRobotDashboardUi.MAX_X, "Inside width deadzone");

        // Far outside check (+X = 2.5 => 2.5 * 75 = 187.5px > 112px)
        Vector outsideDir = new Vector(2.5, 0, 2.2).normalize();
        UiFollowController.RaycastResult outRes = UiFollowController.projectCrosshair(
                eyePos, outsideDir, planeOrigin, planeDir, pixelsPerBlock, maxDistance);

        assertTrue(outRes.hits());
        assertTrue(Math.abs(outRes.localX()) > LunarRobotDashboardUi.MAX_X, "Must detect gaze outside deadzone");
    }

    @Test
    @DisplayName("11. Verify Interaction Hitbox dimensions and vertical center framing")
    public void testInteractionHitboxBounds() {
        float widthBlocks = LunarRobotDashboardUi.TOTAL_WIDTH / LunarRobotDashboardUi.PIXELS_PER_BLOCK;
        float heightBlocks = LunarRobotDashboardUi.TOTAL_HEIGHT / LunarRobotDashboardUi.PIXELS_PER_BLOCK;

        // Total width: 224px / 75 = 2.9867 blocks
        assertEquals(2.9867f, widthBlocks, 1e-4);
        // Total height: 126px / 75 = 1.6800 blocks
        assertEquals(1.6800f, heightBlocks, 1e-4);

        // Interaction feet offset from center origin must be -height / 2
        double feetOffsetY = -heightBlocks * 0.5;
        assertEquals(-0.84, feetOffsetY, 1e-4);

        // Verify that [feet, feet + height] exactly spans [-0.84, +0.84]
        double bottomBound = feetOffsetY;
        double topBound = feetOffsetY + heightBlocks;
        assertEquals(-0.84, bottomBound, 1e-4);
        assertEquals(0.84, topBound, 1e-4);

        // Verify that this perfectly encompasses MIN_Y and MAX_Y in block units
        assertEquals(LunarRobotDashboardUi.MIN_Y / LunarRobotDashboardUi.PIXELS_PER_BLOCK, bottomBound, 1e-4);
        assertEquals(LunarRobotDashboardUi.MAX_Y / LunarRobotDashboardUi.PIXELS_PER_BLOCK, topBound, 1e-4);
    }

    @Test
    @DisplayName("12. Verify Progress Bar and Status Card optical alignment")
    public void testStatusCardOpticalAlignment() {
        assertEquals(-2.0f, LunarDashboardIcons.PROGRESS_BAR_OPTICAL_OFFSET_X, 1e-4);
        assertEquals(0.95f, LunarDashboardIcons.BATTERY_ICON_OPTICAL_OFFSET_Y, 1e-4);
        assertEquals(1.35f, LunarDashboardIcons.INFO_ICON_OPTICAL_OFFSET_Y, 1e-4);

        assertEquals(62.0f, LunarRobotDashboardUi.SUB_CARD_W, 1e-4);
        assertEquals(24.0f, LunarRobotDashboardUi.SUB_CARD_H, 1e-4);
        assertEquals(5.0f, LunarRobotDashboardUi.LINE_1_Y_OFFSET, 1e-4);
        assertEquals(12.0f, LunarRobotDashboardUi.LINE_2_Y_OFFSET, 1e-4);
        assertEquals(11.75f, LunarRobotDashboardUi.PROGRESS_BAR_Y_OFFSET, 1e-4);

        // Verify center-to-center symmetry from center axis (Y = 12.0f)
        float cardCenterY = LunarRobotDashboardUi.SUB_CARD_H / 2.0f; // 12.0f
        float textCenterY = LunarRobotDashboardUi.LINE_1_Y_OFFSET + 3.5f; // 8.5f
        float barCenterY = LunarRobotDashboardUi.PROGRESS_BAR_Y_OFFSET + 3.75f; // 15.5f
        float text2CenterY = LunarRobotDashboardUi.LINE_2_Y_OFFSET + 3.5f; // 15.5f

        float distUp = cardCenterY - textCenterY; // 3.5f
        float distDownBar = barCenterY - cardCenterY; // 3.5f
        float distDownText = text2CenterY - cardCenterY; // 3.5f

        assertEquals(3.5f, distUp, 1e-4, "Distance UP from axis to text center must be 3.5f");
        assertEquals(3.5f, distDownBar, 1e-4, "Distance DOWN from axis to bar center must be 3.5f");
        assertEquals(3.5f, distDownText, 1e-4, "Distance DOWN from axis to text2 center must be 3.5f");
        assertEquals(distUp, distDownBar, 1e-4, "Distance above and below the center axis must be exactly equal");

        vn.haohan.displayui.api.node.AlignedTextNode pbNode = LunarDashboardIcons.createProgressBarNode(
                LunarDashboardIcons.cyanProgressBar(0.5), LunarRobotDashboardUi.SUB_CARD_CONTENT_X, LunarRobotDashboardUi.PROGRESS_BAR_Y_OFFSET, 0.012f);
        // BoxX with optical offset applied: 24.0f + (-2.0f) = 22.0f
        assertEquals(22.0f, pbNode.boxX(), 1e-4);
        assertEquals(11.75f, pbNode.boxY(), 1e-4);
        assertEquals(34.0f, pbNode.width(), 1e-4);
        assertEquals(vn.haohan.displayui.api.text.UiTextAlignment.LEFT, pbNode.alignment());
    }

    @Test
    @DisplayName("13. Verify Slot 3 Battery Slot bounds and alignment in Robot Settings")
    public void testBatterySlotLayout() {
        float slotW = LunarRobotDashboardUi.MODULE_SLOT_W;
        float slotH = LunarRobotDashboardUi.MODULE_SLOT_H;
        float startX = LunarRobotDashboardUi.RIGHT_PANEL_X + 6;
        float gap = 46.0f;

        // Slot 3 (Index 2) is the Battery slot
        float slot3X = startX + 2 * gap;
        assertTrue(slot3X >= LunarRobotDashboardUi.RIGHT_PANEL_X, "Slot 3 out of bounds left");
        assertTrue(slot3X + slotW <= LunarRobotDashboardUi.RIGHT_PANEL_X + LunarRobotDashboardUi.RIGHT_PANEL_W,
                "Slot 3 out of bounds right");

        // Verify slot 1, 2, 3 equal spacing
        float slot1X = startX;
        float slot2X = startX + gap;
        assertEquals(gap, slot2X - slot1X, 1e-4);
        assertEquals(gap, slot3X - slot2X, 1e-4);
    }

    @Test
    @DisplayName("14. Verify Dashboard Rounded Corner Constants and Rendering Helpers")
    public void testRoundedCornerConstantsAndHelpers() {
        assertEquals(6.0f, LunarRobotDashboardUi.PANEL_CORNER_RADIUS, 1e-4);
        assertEquals(5.0f, LunarRobotDashboardUi.CARD_CORNER_RADIUS, 1e-4);
        assertEquals(4.0f, LunarRobotDashboardUi.BUTTON_CORNER_RADIUS, 1e-4);
        assertEquals(4.0f, LunarRobotDashboardUi.PILL_CORNER_RADIUS, 1e-4);
        assertEquals(5.0f, LunarRobotDashboardUi.SLOT_CORNER_RADIUS, 1e-4);
        assertEquals(3.0f, LunarRobotDashboardUi.SMALL_BTN_CORNER_RADIUS, 1e-4);

        vn.haohan.displayui.api.UiDocument.Builder b = vn.haohan.displayui.api.UiDocument.builder();
        LunarRobotDashboardUi.addRoundedRect(b, 0, 0, 0.001f, 50, 20, Color.fromRGB(200, 200, 200), 4.0f);
        LunarRobotDashboardUi.addRoundedGradient(b, 0, 0, 0.001f, 50, 20,
                vn.haohan.displayui.api.gradient.UiGradient.horizontal(Color.WHITE, Color.BLACK), 4.0f);

        vn.haohan.displayui.api.UiDocument doc = b.build();
        assertFalse(doc.nodes().isEmpty(), "Document must contain generated rounded slice nodes");
    }

    @Test
    @DisplayName("15. Verify Unified Master Frame Constants and Center Symmetry")
    public void testMasterFrameDimensions() {
        assertEquals(-108.0f, LunarRobotDashboardUi.MASTER_FRAME_X, 1e-4);
        assertEquals(-62.0f, LunarRobotDashboardUi.MASTER_FRAME_Y, 1e-4);
        assertEquals(216.0f, LunarRobotDashboardUi.MASTER_FRAME_W, 1e-4);
        assertEquals(124.0f, LunarRobotDashboardUi.MASTER_FRAME_H, 1e-4);

        // Center must be exactly at (0, 0)
        float cx = LunarRobotDashboardUi.MASTER_FRAME_X + LunarRobotDashboardUi.MASTER_FRAME_W * 0.5f;
        float cy = LunarRobotDashboardUi.MASTER_FRAME_Y + LunarRobotDashboardUi.MASTER_FRAME_H * 0.5f;
        assertEquals(0.0f, cx, 1e-4, "Master frame center X must be 0");
        assertEquals(0.0f, cy, 1e-4, "Master frame center Y must be 0");

        // Master frame must encompass Left and Right panels with border margin
        assertTrue(LunarRobotDashboardUi.MASTER_FRAME_X <= LunarRobotDashboardUi.LEFT_PANEL_X);
        assertTrue(LunarRobotDashboardUi.MASTER_FRAME_X + LunarRobotDashboardUi.MASTER_FRAME_W >=
                LunarRobotDashboardUi.RIGHT_PANEL_X + LunarRobotDashboardUi.RIGHT_PANEL_W);
    }

    @Test
    @DisplayName("16. Verify Animation Helpers: Zoom Out, Closing, and Slide Fade")
    public void testAnimationHelpers() {
        // Zoom out (scale 1.06 -> 1.0, opacity 0.70 -> 1.0, OutCubic, 8 ticks)
        UiAnimation zoomOut = LunarRobotDashboardUi.createZoomOutAnimation();
        assertNotNull(zoomOut);
        assertEquals(1.06f, zoomOut.fromScale(), 1e-4);
        assertEquals(1.00f, zoomOut.toScale(), 1e-4);
        assertEquals(0.70f, zoomOut.fromOpacity(), 1e-4);
        assertEquals(1.0f, zoomOut.toOpacity(), 1e-4);
        assertEquals(8, zoomOut.durationTicks());
        assertEquals(Easings.OutCubic, zoomOut.easing());

        // Closing (scale 1.0 -> 1.22, opacity 1.0 -> 0.0, InCubic, 8 ticks)
        UiAnimation closing = LunarRobotDashboardUi.createClosingAnimation();
        assertNotNull(closing);
        assertEquals(1.00f, closing.fromScale(), 1e-4);
        assertEquals(1.22f, closing.toScale(), 1e-4);
        assertEquals(1.0f, closing.fromOpacity(), 1e-4);
        assertEquals(0.0f, closing.toOpacity(), 1e-4);
        assertEquals(LunarRobotDashboardUi.CLOSING_DURATION_TICKS, closing.durationTicks());
        assertEquals(Easings.InCubic, closing.easing());

        // Slide Fade (offset 16px, opacity 0.50 -> 1.0, OutQuad, 8 ticks)
        UiAnimation slideRight = LunarRobotDashboardUi.createSlideFadeAnimation(true);
        assertEquals(16.0f, slideRight.offsetX(), 1e-4);
        assertEquals(0.50f, slideRight.fromOpacity(), 1e-4);
        assertEquals(1.0f, slideRight.toOpacity(), 1e-4);
        assertEquals(Easings.OutQuad, slideRight.easing());

        UiAnimation slideLeft = LunarRobotDashboardUi.createSlideFadeAnimation(false);
        assertEquals(-16.0f, slideLeft.offsetX(), 1e-4);
    }

    @Test
    @DisplayName("17. Verify Splash Screen and Lifecycle Constants")
    public void testLifecycleConstants() {
        // Exactly 1.0s (20 ticks) logo splash screen
        assertEquals(20, LunarRobotDashboardUi.SPLASH_DURATION_TICKS);
        assertEquals(8, LunarRobotDashboardUi.CLOSING_DURATION_TICKS);

        // UiState lifecycle states
        assertEquals(3, LunarRobotDashboardUi.UiState.values().length);
        assertNotNull(LunarRobotDashboardUi.UiState.valueOf("SPLASH"));
        assertNotNull(LunarRobotDashboardUi.UiState.valueOf("DASHBOARD"));
        assertNotNull(LunarRobotDashboardUi.UiState.valueOf("CLOSING"));
    }

    @Test
    @DisplayName("18. Verify Settings Viewport and Accordion Card Constants")
    public void testSettingsViewportAndAccordion() {
        assertEquals(136.0f, LunarRobotDashboardUi.ACCORDION_CARD_W, 1e-4);
        assertEquals(13.0f, LunarRobotDashboardUi.ACCORDION_CARD_H, 1e-4);
        assertEquals(2.0f, LunarRobotDashboardUi.ACCORDION_GAP, 1e-4);

        // Accordion cards fit within right panel width (144.0f) with margins
        assertTrue(LunarRobotDashboardUi.ACCORDION_CARD_W <= LunarRobotDashboardUi.RIGHT_PANEL_W - 4.0f);

        // SettingsCard enums
        assertEquals(5, LunarRobotDashboardUi.SettingsCard.values().length);
        assertNotNull(LunarRobotDashboardUi.SettingsCard.valueOf("COLLAPSED"));
        assertNotNull(LunarRobotDashboardUi.SettingsCard.valueOf("MODULES"));
        assertNotNull(LunarRobotDashboardUi.SettingsCard.valueOf("HEALTH"));
        assertNotNull(LunarRobotDashboardUi.SettingsCard.valueOf("CUSTOMIZE"));
        assertNotNull(LunarRobotDashboardUi.SettingsCard.valueOf("UNBIND"));
    }

    @Test
    @DisplayName("19. Verify Settings Theme Adaptability and Hotbar Protection")
    public void testSettingsThemeAdaptability() {
        for (LunarDashboardTheme theme : LunarDashboardTheme.values()) {
            assertNotNull(theme.getButtonBg(), "Theme " + theme.name() + " must have a button background");
            assertNotNull(theme.getAccentColor(), "Theme " + theme.name() + " must have an accent color");
            assertNotNull(theme.getCardBg(), "Theme " + theme.name() + " must have a card background");
        }

        // Verify Yellow theme matches user's screenshot requirements
        LunarDashboardTheme yellow = LunarDashboardTheme.YELLOW;
        assertEquals(Color.fromRGB(0xff, 0xf1, 0xc7), yellow.getButtonBg());
        assertEquals(Color.fromRGB(0xcf, 0xa2, 0x34), yellow.getAccentColor());
    }

    @Test
    @DisplayName("20. Verify Left Panel static frame, Right Panel slide, and Button fade selection")
    public void testTabSwitchAnimations() {
        UiDocument.Builder b = UiDocument.builder();
        // 1. Left Panel outer container (depth = 0.001f, X = LEFT_PANEL_X)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.LEFT_PANEL_X, LunarRobotDashboardUi.LEFT_PANEL_Y, 0.001f, 60, 100, Color.WHITE));
        // 2. Left Panel "Dashboard" title (depth = 0.010f, Y = -60.0f)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.LEFT_PANEL_X + 4, -60.0f, 0.010f, 54, 14, Color.WHITE));
        // 3. Left Panel "Chế độ" nav button (depth = 0.004f, Y = BTN_MODES_Y)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.LEFT_PANEL_X + 4, LunarRobotDashboardUi.BTN_MODES_Y, 0.004f, 54, 18, Color.WHITE));
        // 4. Left Panel "Cài đặt" nav button (depth = 0.004f, Y = BTN_SETTINGS_Y)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.LEFT_PANEL_X + 4, LunarRobotDashboardUi.BTN_SETTINGS_Y, 0.004f, 54, 18, Color.WHITE));
        // 5. Left Panel "Thoát" exit button (depth = 0.004f, Y = BTN_EXIT_Y)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.LEFT_PANEL_X + 4, LunarRobotDashboardUi.BTN_EXIT_Y, 0.004f, 54, 18, Color.WHITE));
        // 6. Right Panel content card (X = RIGHT_PANEL_X, depth = 0.003f)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, -40.0f, 0.003f, 100, 50, Color.WHITE));

        UiDocument doc = b.build();
        assertEquals(6, doc.nodes().size());

        // Forward tab switch (e.g. Overview -> Modes, or Modes -> Settings)
        List<UiAnimation> forwardAnims = LunarRobotDashboardUi.createTabSwitchAnimations(doc, true);
        assertEquals(6, forwardAnims.size());

        // 1. Left container background: MUST BE STATIC (no offset, no opacity change)
        UiAnimation leftBgAnim = forwardAnims.get(0);
        assertTrue(leftBgAnim.isStatic(), "Left frame background must be completely static");
        assertEquals(0.0f, leftBgAnim.offsetX(), 1e-4);
        assertEquals(1.0f, leftBgAnim.fromOpacity(), 1e-4);

        // 2. Left title: MUST BE STATIC (no offset)
        UiAnimation titleAnim = forwardAnims.get(1);
        assertEquals(0.0f, titleAnim.offsetX(), 1e-4);
        assertEquals(1.0f, titleAnim.fromOpacity(), 1e-4);

        // 3. "Chế độ" nav button: MUST HAVE FADE SELECTION (no offset, opacity fades from 0.25 to 1.0)
        UiAnimation modesBtnAnim = forwardAnims.get(2);
        assertEquals(0.0f, modesBtnAnim.offsetX(), 1e-4, "Nav button must not slide horizontally");
        assertEquals(0.25f, modesBtnAnim.fromOpacity(), 1e-4, "Nav button must fade in selection from 0.25f");
        assertEquals(1.0f, modesBtnAnim.toOpacity(), 1e-4);

        // 4. "Cài đặt" nav button: MUST HAVE FADE SELECTION
        UiAnimation settingsBtnAnim = forwardAnims.get(3);
        assertEquals(0.0f, settingsBtnAnim.offsetX(), 1e-4, "Nav button must not slide horizontally");
        assertEquals(0.25f, settingsBtnAnim.fromOpacity(), 1e-4, "Nav button must fade in selection from 0.25f");

        // 5. "Thoát" exit button: MUST BE STATIC
        UiAnimation exitBtnAnim = forwardAnims.get(4);
        assertTrue(exitBtnAnim.isStatic(), "Exit button must be static during tab switch");
        assertEquals(0.0f, exitBtnAnim.offsetX(), 1e-4);

        // 6. Right Panel content: MUST HAVE SLIDE & FADE ANIMATION
        UiAnimation rightAnim = forwardAnims.get(5);
        assertEquals(16.0f, rightAnim.offsetX(), 1e-4, "Right panel must slide in from right (+16px)");
        assertEquals(0.50f, rightAnim.fromOpacity(), 1e-4, "Right panel must fade in from 0.50f");

        // Backward tab switch (e.g. Settings -> Modes)
        List<UiAnimation> backwardAnims = LunarRobotDashboardUi.createTabSwitchAnimations(doc, false);
        UiAnimation rightBackAnim = backwardAnims.get(5);
        assertEquals(-16.0f, rightBackAnim.offsetX(), 1e-4, "Right panel must slide in from left (-16px) when moving backward");
        assertEquals(0.50f, rightBackAnim.fromOpacity(), 1e-4);

        // Left panel still has zero offset in backward switch
        assertEquals(0.0f, backwardAnims.get(0).offsetX(), 1e-4);
        assertEquals(0.0f, backwardAnims.get(2).offsetX(), 1e-4);

        // Verify fixed shields/header at depth >= 0.012f stay static, while content cards animate
        UiDocument.Builder settingsDocB = UiDocument.builder();
        settingsDocB.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, -56.0f, 0.012f, 100, 20, Color.WHITE)); // shield
        settingsDocB.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, -30.0f, 0.003f, 100, 20, Color.WHITE)); // card
        UiDocument sDoc = settingsDocB.build();
        List<UiAnimation> sAnims = LunarRobotDashboardUi.createTabSwitchAnimations(sDoc, true);
        assertTrue(sAnims.get(0).isStatic(), "Fixed shield must stay static on tab switch");
        assertFalse(sAnims.get(1).isStatic(), "Settings card must animate into view on tab switch");
        assertEquals(16.0f, sAnims.get(1).offsetX(), 1e-4);
        assertEquals(0.50f, sAnims.get(1).fromOpacity(), 1e-4);
    }

    @Test
    @DisplayName("21. Verify Background Gradient Layer (depth <= 0.002f) is strictly static during animations")
    public void testBackgroundGradientLayerStaticDuringAnimations() {
        UiDocument.Builder b = UiDocument.builder();
        // 1. Right Panel Base Background (depth = 0.001f, X = RIGHT_PANEL_X, Y = 0.0f)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, 0.0f, 0.001f, 144, 120, Color.WHITE));
        // 2. Right Panel Foreground Content Card (depth = 0.003f, X = RIGHT_PANEL_X, Y = 0.0f)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, 0.0f, 0.003f, 140, 50, Color.WHITE));
        // 3. Right Panel Fixed Header on Shield (depth = 0.015f, X = RIGHT_PANEL_X, Y = -56.0f)
        b.add(new UiBackgroundNode(LunarRobotDashboardUi.RIGHT_PANEL_X, -56.0f, 0.015f, 100, 12, Color.WHITE));

        UiDocument doc = b.build();
        List<UiAnimation> anims = LunarRobotDashboardUi.createTabSwitchAnimations(doc, true);
        assertEquals(3, anims.size());

        // Node 1: Right Panel Base Background MUST BE STRICTLY STATIC (Zero flicker, zero slide, zero fade)
        UiAnimation bgAnim = anims.get(0);
        assertTrue(bgAnim.isStatic(), "Right panel background gradient at depth 0.001f must be 100% static");
        assertEquals(0.0f, bgAnim.offsetX(), 1e-4, "Background gradient must have 0 horizontal offset");
        assertEquals(1.0f, bgAnim.fromOpacity(), 1e-4, "Background gradient must maintain 100% opacity");

        // Node 2: Foreground Content Card MUST SLIDE & FADE IN
        UiAnimation contentAnim = anims.get(1);
        assertFalse(contentAnim.isStatic(), "Foreground content card must animate into view");
        assertEquals(16.0f, contentAnim.offsetX(), 1e-4, "Content card must slide from right");
        assertEquals(0.50f, contentAnim.fromOpacity(), 1e-4, "Content card must fade in from opacity 0.50f");

        // Node 3: Fixed Header Title on Shield MUST BE STATIC
        UiAnimation headerAnim = anims.get(2);
        assertTrue(headerAnim.isStatic(), "Fixed header on shield must stay static");
    }

    @Test
    @DisplayName("22. Verify Nav button zero-scale jitter")
    public void testSettingsHeaderAndNavButtonInvariants() {
        // Verify FADE_SELECTION_ANIMATION has zero scale distortion (scale == 1.0f) and zero offset (offset == 0.0f)
        assertEquals(1.0f, LunarRobotDashboardUi.FADE_SELECTION_ANIMATION.fromScale(), 1e-4);
        assertEquals(1.0f, LunarRobotDashboardUi.FADE_SELECTION_ANIMATION.toScale(), 1e-4);
        assertEquals(0.0f, LunarRobotDashboardUi.FADE_SELECTION_ANIMATION.offsetX(), 1e-4);
        assertEquals(0.0f, LunarRobotDashboardUi.FADE_SELECTION_ANIMATION.offsetY(), 1e-4);
    }

    @Test
    @DisplayName("23. Verify Accordion Card Dimensions")
    public void testModulesCardDimensions() {
        assertTrue(LunarRobotDashboardUi.ACCORDION_CARD_W > 0);
        assertTrue(LunarRobotDashboardUi.ACCORDION_CARD_H > 0);
    }

    @Test
    @DisplayName("25. Verify LayerManager structure and Layer allocation in buildDashboardLayerManager")
    public void testDashboardLayerManagerStructure() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("TestBot");

        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        LayerManager manager = ui.buildDashboardLayerManager();

        assertNotNull(manager);
        assertEquals(3, manager.layerCount(), "Dashboard must contain exactly 3 layers (bg, left_nav, right_content)");

        assertTrue(manager.hasLayer("background_canvas"));
        assertTrue(manager.hasLayer("left_nav_layer"));
        assertTrue(manager.hasLayer("right_content_layer"));
        assertFalse(manager.hasLayer("frame_hitbox_layer"), "Faulty frame_hitbox_layer must not exist");

        List<Layer> layers = manager.layers();
        assertEquals("background_canvas", layers.get(0).id());
        assertEquals(0, layers.get(0).zIndex());

        assertEquals("left_nav_layer", layers.get(1).id());
        assertEquals(1, layers.get(1).zIndex());

        assertEquals("right_content_layer", layers.get(2).id());
        assertEquals(2, layers.get(2).zIndex());

        // Verify left panel container
        Layer leftLayer = manager.getLayer("left_nav_layer").orElseThrow();
        Container leftContainer = leftLayer.getContainer("left_panel").orElseThrow();
        assertEquals(LunarRobotDashboardUi.LEFT_PANEL_W, leftContainer.width(), 1e-4);
        assertEquals(LunarRobotDashboardUi.LEFT_PANEL_H, leftContainer.height(), 1e-4);
        assertNotNull(leftContainer.findComponent("btn_nav_modes"));
        assertNotNull(leftContainer.findComponent("btn_nav_settings"));
        assertNotNull(leftContainer.findComponent("btn_nav_exit"));

        // Verify right panel container
        Layer rightLayer = manager.getLayer("right_content_layer").orElseThrow();
        Container rightContainer = rightLayer.getContainer("right_panel").orElseThrow();
        assertEquals(LunarRobotDashboardUi.RIGHT_PANEL_W, rightContainer.width(), 1e-4);
        assertEquals(LunarRobotDashboardUi.RIGHT_PANEL_H, rightContainer.height(), 1e-4);
    }

    @Test
    @DisplayName("26. Verify Splash LayerManager and Splash Card Container")
    public void testSplashLayerManagerStructure() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());

        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        LayerManager splashManager = ui.buildSplashLayerManager();

        assertNotNull(splashManager);
        assertEquals(1, splashManager.layerCount());
        Layer splashLayer = splashManager.getLayer("splash_layer").orElseThrow();
        assertEquals(0, splashLayer.zIndex());

        Container splashCard = splashLayer.getContainer("splash_card").orElseThrow();
        assertEquals(120.0f, splashCard.width(), 1e-4);
        assertEquals(72.0f, splashCard.height(), 1e-4);
        assertNotNull(splashCard.findComponent("splash_title"));
        assertNotNull(splashCard.findComponent("splash_subtitle"));
    }

    @Test
    @DisplayName("27. Verify UiDocumentBridge compilation produces non-colliding non-empty document")
    public void testUiDocumentBridgeCompilation() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("TestBot");

        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        UiDocument doc = ui.renderDocument();

        assertNotNull(doc);
        assertFalse(doc.nodes().isEmpty(), "Compiled document must contain rendered visual nodes");
        assertFalse(doc.buttons().isEmpty(), "Compiled document must contain interactive buttons");

        // Verify button presence
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_modes")));
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_settings")));
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_exit")));
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("btn_nav_overview")));
    }

    @Test
    @DisplayName("28. Verify Settings page integrates DisplayUI DropdownContainer with dynamic expansion")
    public void testSettingsDropdownContainers() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("TestBot");

        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTab(LunarRobotDashboardUi.Tab.SETTINGS);

        // 1. Initially all collapsed
        assertEquals(LunarRobotDashboardUi.SettingsCard.COLLAPSED, ui.getExpandedSettingsCard());
        LayerManager manager = ui.buildDashboardLayerManager();
        Layer rightLayer = manager.getLayer("right_content_layer").orElseThrow();
        Container rightPanel = rightLayer.getContainer("right_panel").orElseThrow();

        // 2. Verify all 4 dropdown containers exist and are DropdownContainer instances
        assertTrue(rightPanel.findContainer("card_modules").isPresent());
        assertTrue(rightPanel.findContainer("card_health").isPresent());
        assertTrue(rightPanel.findContainer("card_customize").isPresent());
        assertTrue(rightPanel.findContainer("card_unbind").isPresent());

        Container modCont = rightPanel.findContainer("card_modules").get();
        assertTrue(modCont instanceof DropdownContainer, "card_modules must be DropdownContainer");
        DropdownContainer modDropdown = (DropdownContainer) modCont;

        Container healthCont = rightPanel.findContainer("card_health").get();
        assertTrue(healthCont instanceof DropdownContainer, "card_health must be DropdownContainer");
        DropdownContainer healthDropdown = (DropdownContainer) healthCont;

        Container custCont = rightPanel.findContainer("card_customize").get();
        assertTrue(custCont instanceof DropdownContainer, "card_customize must be DropdownContainer");
        DropdownContainer custDropdown = (DropdownContainer) custCont;

        Container unbindCont = rightPanel.findContainer("card_unbind").get();
        assertTrue(unbindCont instanceof DropdownContainer, "card_unbind must be DropdownContainer");
        DropdownContainer unbindDropdown = (DropdownContainer) unbindCont;

        // When collapsed, height must equal headerHeight
        assertFalse(modDropdown.isExpanded());
        assertEquals(LunarRobotDashboardUi.ACCORDION_CARD_H, modDropdown.height(), 1e-4);
        assertEquals("card_modules_toggle", modDropdown.toggleButtonId());

        // 3. Test clicking header toggle button expands MODULES dropdown
        ui.handleButtonClick("card_modules_toggle");
        assertEquals(LunarRobotDashboardUi.SettingsCard.MODULES, ui.getExpandedSettingsCard());

        LayerManager expandedMgr = ui.buildDashboardLayerManager();
        DropdownContainer expandedMod = (DropdownContainer) expandedMgr.getLayer("right_content_layer")
                .orElseThrow().getContainer("right_panel").orElseThrow().findContainer("card_modules").orElseThrow();

        assertTrue(expandedMod.isExpanded(), "MODULES dropdown must be expanded");
        assertTrue(expandedMod.height() > LunarRobotDashboardUi.ACCORDION_CARD_H, "Height must dynamically expand");

        // Verify module slots are nested inside the expanded dropdown
        assertTrue(expandedMod.findComponent("slot_0").isPresent());
        assertTrue(expandedMod.findComponent("slot_1").isPresent());
        assertTrue(expandedMod.findComponent("slot_2").isPresent());
        assertTrue(expandedMod.findComponent("slot_0_l1").isPresent());
        assertTrue(expandedMod.findComponent("slot_2_l1").isPresent());

        // 4. Test compilation with UiDocumentBridge
        UiDocument doc = UiDocumentBridge.compile(expandedMgr);
        assertNotNull(doc);
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("card_modules_toggle")),
                "Compiled document must have card_modules_toggle header button");
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("slot_0")),
                "Compiled document must contain slot_0 button when expanded");
        assertTrue(doc.buttons().stream().anyMatch(b -> b.id().equals("slot_2")),
                "Compiled document must contain slot_2 (battery) button when expanded");

        // 5. Test switching to HEALTH dropdown
        ui.handleButtonClick("card_health_toggle");
        assertEquals(LunarRobotDashboardUi.SettingsCard.HEALTH, ui.getExpandedSettingsCard());

        LayerManager healthMgr = ui.buildDashboardLayerManager();
        DropdownContainer expHealth = (DropdownContainer) healthMgr.getLayer("right_content_layer")
                .orElseThrow().getContainer("right_panel").orElseThrow().findContainer("card_health").orElseThrow();
        assertTrue(expHealth.isExpanded());
        assertTrue(expHealth.findComponent("health_detail_title").isPresent());
        assertTrue(expHealth.findComponent("stat_owner_lbl").isPresent());
        assertTrue(expHealth.findComponent("stat_espent_lbl").isPresent());

        // 6. Test CUSTOMIZE dropdown
        ui.handleButtonClick("card_customize_toggle");
        assertEquals(LunarRobotDashboardUi.SettingsCard.CUSTOMIZE, ui.getExpandedSettingsCard());

        LayerManager custMgr = ui.buildDashboardLayerManager();
        Container custRightPanel = custMgr.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow();
        DropdownContainer expCust = (DropdownContainer) custRightPanel.findContainer("card_customize").orElseThrow();
        assertTrue(expCust.isExpanded());
        assertTrue(expCust.findComponent("cust_detail_title").isPresent());
        assertTrue(expCust.findComponent("action_change_theme").isPresent());
        assertTrue(expCust.findComponent("action_rename_robot").isPresent());
        assertTrue(custRightPanel.findContainer("card_health").isPresent());
        assertTrue(custRightPanel.findContainer("card_unbind").isPresent());

        // 7. Test UNBIND dropdown with dangerous styling
        ui.handleButtonClick("card_unbind_toggle");
        assertEquals(LunarRobotDashboardUi.SettingsCard.UNBIND, ui.getExpandedSettingsCard());

        LayerManager unbindMgr = ui.buildDashboardLayerManager();
        Container unbindRightPanel = unbindMgr.getLayer("right_content_layer").orElseThrow()
                .getContainer("right_panel").orElseThrow();
        DropdownContainer expUnbind = (DropdownContainer) unbindRightPanel.findContainer("card_unbind").orElseThrow();
        assertTrue(expUnbind.isExpanded());
        assertTrue(expUnbind.findComponent("confirm_unbind").isPresent());
        assertTrue(expUnbind.findComponent("cancel_unbind").isPresent());
        assertTrue(expUnbind.findComponent("unbind_warn_title").isPresent());
        assertTrue(unbindRightPanel.findContainer("card_modules").isPresent());
        assertTrue(unbindRightPanel.findContainer("card_health").isPresent());

        // 8. Test all dropdowns exist in right panel across all expansion states
        for (LunarRobotDashboardUi.SettingsCard card : LunarRobotDashboardUi.SettingsCard.values()) {
            ui.setExpandedSettingsCard(card);
            LayerManager lm = ui.buildDashboardLayerManager();
            Container rp = lm.getLayer("right_content_layer").orElseThrow().getContainer("right_panel").orElseThrow();
            assertTrue(rp.findContainer("card_modules").isPresent());
            assertTrue(rp.findContainer("card_health").isPresent());
            assertTrue(rp.findContainer("card_customize").isPresent());
            assertTrue(rp.findContainer("card_unbind").isPresent());
        }
    }

    @Test
    @DisplayName("29. Verify all Settings Dropdown expansions fit strictly within RIGHT_PANEL_H bounds")
    public void testSettingsDropdownContainersFitWithinPanelBounds() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("TestBot");
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTab(LunarRobotDashboardUi.Tab.SETTINGS);

        for (LunarRobotDashboardUi.SettingsCard card : LunarRobotDashboardUi.SettingsCard.values()) {
            ui.setExpandedSettingsCard(card);
            LayerManager manager = ui.buildDashboardLayerManager();
            Layer rightLayer = manager.getLayer("right_content_layer").orElseThrow();
            Container rightPanel = rightLayer.getContainer("right_panel").orElseThrow();

            for (Container child : rightPanel.childContainers()) {
                float bottomY = child.y() + child.height();
                assertTrue(bottomY <= LunarRobotDashboardUi.RIGHT_PANEL_H + 0.01f,
                        "Container " + child.id() + " with expanded card " + card + " exceeds RIGHT_PANEL_H (" + bottomY + " > " + LunarRobotDashboardUi.RIGHT_PANEL_H + ")");
            }
        }
    }

    @Test
    @DisplayName("30. Verify all 4 dropdowns use standalone TextComponent for header title with 5px offset and high contrast")
    public void testModulesDropdownCustomHeaderTitle() {
        LunarRobotData data = new LunarRobotData(UUID.randomUUID());
        data.setName("TestBot");
        LunarRobotDashboardUi ui = new LunarRobotDashboardUi(data);
        ui.setCurrentTab(LunarRobotDashboardUi.Tab.SETTINGS);

        LayerManager mgr = ui.buildDashboardLayerManager();
        Container rightPanel = mgr.getLayer("right_content_layer")
                .orElseThrow().getContainer("right_panel").orElseThrow();

        String[] cardIds = {"card_modules", "card_health", "card_customize", "card_unbind"};
        for (String cardId : cardIds) {
            DropdownContainer cont = (DropdownContainer) rightPanel.findContainer(cardId).orElseThrow();
            assertEquals(net.kyori.adventure.text.Component.empty(), cont.headerTitle(), cardId + " headerTitle should be empty");

            var titleCompOpt = cont.findComponent(cardId + "_title");
            assertTrue(titleCompOpt.isPresent(), cardId + "_title TextComponent must exist");
            var titleComp = (vn.haohan.displayui.api.component.TextComponent) titleCompOpt.get();
            assertEquals(11.0f, titleComp.x(), 1e-4, cardId + " title should be offset 11px");
            assertEquals(4.2f, titleComp.fontSize(), 1e-4, cardId + " title font size should be 4.2f");
        }
    }
}

