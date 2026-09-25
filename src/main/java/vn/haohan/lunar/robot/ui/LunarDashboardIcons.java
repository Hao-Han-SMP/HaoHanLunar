package vn.haohan.lunar.robot.ui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import vn.haohan.displayui.api.node.AlignedTextNode;
import vn.haohan.displayui.api.text.UiTextAlignment;

/**
 * Declares and manages font glyph icons for the Lunar Robot Dashboard.
 * Maps custom textures declared in HaoHanSMP-resourcepack to Unicode code points.
 */
public final class LunarDashboardIcons {

    public static final Key FONT_KEY = Key.key("haohan:lunar_dashboard");

    public static final char CHAR_INFO       = '\ue401';
    public static final char CHAR_CALENDAR   = '\ue402';
    public static final char CHAR_BATTERY    = '\ue403';
    public static final char CHAR_MODULE     = '\ue404';
    public static final char CHAR_MODE       = '\ue405';
    public static final char CHAR_SETTINGS   = '\ue406';
    public static final char CHAR_EXIT       = '\ue407';

    public static final char CHAR_PROGRESS_CYAN_BASE  = '\ue410'; // 0% to 100% (\ue410 - \ue41a)
    public static final char CHAR_PROGRESS_GREEN_BASE = '\ue420'; // 0% to 100% (\ue420 - \ue42a)

    private LunarDashboardIcons() {}

    public static Component icon(char character) {
        return Component.text(String.valueOf(character)).font(FONT_KEY);
    }

    public static Component icon(char character, TextColor color) {
        return Component.text(String.valueOf(character)).font(FONT_KEY).color(color);
    }

    public static Component infoIcon() {
        return icon(CHAR_INFO);
    }

    public static Component calendarIcon() {
        return icon(CHAR_CALENDAR);
    }

    public static Component batteryIcon() {
        return icon(CHAR_BATTERY);
    }

    public static Component moduleIcon() {
        return icon(CHAR_MODULE);
    }

    public static Component modeIcon() {
        return icon(CHAR_MODE);
    }

    public static Component settingsIcon() {
        return icon(CHAR_SETTINGS);
    }

    public static Component exitIcon() {
        return icon(CHAR_EXIT);
    }

    public static Component cyanProgressBar(double fraction) {
        int index = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 10);
        char c = (char) (CHAR_PROGRESS_CYAN_BASE + index);
        return icon(c);
    }

    public static Component greenProgressBar(double fraction) {
        int index = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 10);
        char c = (char) (CHAR_PROGRESS_GREEN_BASE + index);
        return icon(c);
    }

    /**
     * Creates an AlignedTextNode displaying the icon glyph at the specified position and dimensions,
     * centered optically without baseline drift.
     */
    public static AlignedTextNode createIconNode(Component iconComp, float x, float y, float width, float height, float fontSize, float depth) {
        return new AlignedTextNode(iconComp, x, y, width, height, depth,
                UiTextAlignment.CENTER, 0.0f, 0.0f, fontSize, width,
                vn.haohan.displayui.api.text.UiVerticalAlignment.CENTER, 0.0f, false, false);
    }

    public static AlignedTextNode createIconNode(Component iconComp, float x, float y, float width, float height, float fontSize, float depth, UiTextAlignment alignment) {
        return new AlignedTextNode(iconComp, x, y, width, height, depth,
                alignment, 0.0f, 0.0f, fontSize, width,
                vn.haohan.displayui.api.text.UiVerticalAlignment.CENTER, 0.0f, false, false);
    }

    public static final float PROGRESS_BAR_WIDTH = 34.0f;
    public static final float PROGRESS_BAR_HEIGHT = 7.5f;
    public static final float PROGRESS_BAR_OPTICAL_OFFSET_X = -2.0f;
    public static final float BATTERY_ICON_OPTICAL_OFFSET_Y = 0.95f;
    public static final float INFO_ICON_OPTICAL_OFFSET_Y = 1.35f;

    public static AlignedTextNode createProgressBarNode(Component barComp, float x, float y, float depth) {
        return createProgressBarNode(barComp, x, y, depth, PROGRESS_BAR_OPTICAL_OFFSET_X);
    }

    public static AlignedTextNode createProgressBarNode(Component barComp, float x, float y, float depth, float opticalOffsetX) {
        return new AlignedTextNode(barComp, x + opticalOffsetX, y, PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT, depth,
                UiTextAlignment.LEFT, 0.0f, 0.0f, PROGRESS_BAR_HEIGHT, PROGRESS_BAR_WIDTH,
                vn.haohan.displayui.api.text.UiVerticalAlignment.CENTER, 0.0f, false, false);
    }

    public static AlignedTextNode createLeftIconNode(Component iconComp, float x, float y, float width, float height, float fontSize, float depth) {
        return createIconNode(iconComp, x, y, width, height, fontSize, depth, UiTextAlignment.LEFT);
    }

    public static AlignedTextNode createIconNode(Component iconComp, float x, float y, float width, float height, float fontSize, float depth, float vOffset) {
        return new AlignedTextNode(iconComp, x, y, width, height, depth,
                UiTextAlignment.CENTER, 0.0f, 0.0f, fontSize, width,
                vn.haohan.displayui.api.text.UiVerticalAlignment.CENTER, vOffset, false, false);
    }
}
