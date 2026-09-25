package vn.haohan.lunar.robot.ui;

import org.bukkit.Color;

/**
 * Color themes and palette definitions for the Lunar Robot Dashboard UI.
 * Matches specifications from lunar_design.pdf.
 */
public enum LunarDashboardTheme {
    CYAN_WHITE("Trắng xanh",
            Color.fromRGB(0xbf, 0xd7, 0xee),
            Color.fromRGB(0xe3, 0xf4, 0xf8),
            Color.fromRGB(0x53, 0x86, 0xcb),
            Color.fromRGB(0x8d, 0xab, 0xcb),
            Color.fromRGB(0xca, 0xe6, 0xff)),

    RED("Đỏ",
            Color.fromRGB(0xf7, 0xc2, 0xc2),
            Color.fromRGB(0xfa, 0xe8, 0xe8),
            Color.fromRGB(0xd4, 0x50, 0x50),
            Color.fromRGB(0xc8, 0x86, 0x86),
            Color.fromRGB(0xff, 0xd5, 0xd5)),

    BLUE("Xanh dương",
            Color.fromRGB(0xb3, 0xd2, 0xf2),
            Color.fromRGB(0xdb, 0xee, 0xfd),
            Color.fromRGB(0x3a, 0x75, 0xc4),
            Color.fromRGB(0x7f, 0xa0, 0xc7),
            Color.fromRGB(0xbe, 0xe0, 0xff)),

    YELLOW("Vàng",
            Color.fromRGB(0xf5, 0xe6, 0xb8),
            Color.fromRGB(0xfc, 0xf6, 0xde),
            Color.fromRGB(0xcf, 0xa2, 0x34),
            Color.fromRGB(0xc4, 0xb3, 0x86),
            Color.fromRGB(0xff, 0xf1, 0xc7));

    private final String displayName;
    private final Color gradStart;
    private final Color gradEnd;
    private final Color accentColor;
    private final Color cardBg;
    private final Color buttonBg;

    // Fixed colors according to lunar_design.pdf
    public static final Color DANGER_BORDER = Color.fromRGB(0xd4, 0x50, 0x50);
    public static final Color DANGER_BG = Color.fromRGB(0xff, 0x6a, 0x6a);
    public static final Color DANGER_BUTTON = Color.fromRGB(0xd4, 0x50, 0x50);
    public static final Color NEUTRAL_BUTTON = Color.fromRGB(0x8d, 0xab, 0xcb);

    LunarDashboardTheme(String displayName, Color gradStart, Color gradEnd, Color accentColor, Color cardBg, Color buttonBg) {
        this.displayName = displayName;
        this.gradStart = gradStart;
        this.gradEnd = gradEnd;
        this.accentColor = accentColor;
        this.cardBg = cardBg;
        this.buttonBg = buttonBg;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getGradStart() {
        return gradStart;
    }

    public Color getGradEnd() {
        return gradEnd;
    }

    public Color getAccentColor() {
        return accentColor;
    }

    public Color getCardBg() {
        return cardBg;
    }

    public Color getButtonBg() {
        return buttonBg;
    }

    public LunarDashboardTheme next() {
        LunarDashboardTheme[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
