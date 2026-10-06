package vn.haohan.lunar.robot.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Renderer chuyển đổi trạng thái robot module thành giao diện thẻ BossBar chuẩn MCC Island.
 * Sử dụng kỹ thuật Custom Font Glyph và Negative Spaces để hiển thị khung nền bo góc sẫm màu
 * và tự động căn giữa chữ chuẩn xác theo từng pixel.
 */
public final class LunarModuleBossBarRenderer {

    public static final String CARD_BG_CHAR = "\ue700";
    public static final int CARD_WIDTH = 216;

    // Negative spaces advances
    public static final String SPACE_NEG_216 = "\ue3ea";
    public static final String SPACE_NEG_210 = "\ue3f0";
    public static final String SPACE_NEG_105 = "\ue3f1";
    public static final String SPACE_NEG_64  = "\ue3f2";
    public static final String SPACE_NEG_32  = "\ue3f3";
    public static final String SPACE_NEG_16  = "\ue3f4";
    public static final String SPACE_NEG_8   = "\ue3f5";
    public static final String SPACE_NEG_4   = "\ue3f6";
    public static final String SPACE_NEG_2   = "\ue3f7";
    public static final String SPACE_NEG_1   = "\ue3f8";

    // Positive spaces advances
    public static final String SPACE_POS_1   = "\ue3f9";
    public static final String SPACE_POS_2   = "\ue3fa";
    public static final String SPACE_POS_4   = "\ue3fb";
    public static final String SPACE_POS_8   = "\ue3fc";
    public static final String SPACE_POS_16  = "\ue3fd";
    public static final String SPACE_POS_32  = "\ue3fe";
    public static final String SPACE_POS_64  = "\ue3ff";
    public static final String SPACE_POS_105 = "\ue3ee";
    public static final String SPACE_POS_210 = "\ue3ef";

    private LunarModuleBossBarRenderer() {}

    /**
     * Sinh chuỗi ký tự khoảng cách âm hoặc dương để dịch chuyển con trỏ vẽ (cursor).
     */
    public static String buildSpaceSequence(int pixels) {
        if (pixels == 0) return "";
        StringBuilder sb = new StringBuilder();

        if (pixels < 0) {
            int remaining = -pixels;
            if (remaining == 216) {
                return SPACE_NEG_216;
            }
            while (remaining > 0) {
                if (remaining >= 216) {
                    sb.append(SPACE_NEG_216);
                    remaining -= 216;
                } else if (remaining >= 210) {
                    sb.append(SPACE_NEG_210);
                    remaining -= 210;
                } else if (remaining >= 105) {
                    sb.append(SPACE_NEG_105);
                    remaining -= 105;
                } else if (remaining >= 64) {
                    sb.append(SPACE_NEG_64);
                    remaining -= 64;
                } else if (remaining >= 32) {
                    sb.append(SPACE_NEG_32);
                    remaining -= 32;
                } else if (remaining >= 16) {
                    sb.append(SPACE_NEG_16);
                    remaining -= 16;
                } else if (remaining >= 8) {
                    sb.append(SPACE_NEG_8);
                    remaining -= 8;
                } else if (remaining >= 4) {
                    sb.append(SPACE_NEG_4);
                    remaining -= 4;
                } else if (remaining >= 2) {
                    sb.append(SPACE_NEG_2);
                    remaining -= 2;
                } else {
                    sb.append(SPACE_NEG_1);
                    remaining -= 1;
                }
            }
        } else {
            int remaining = pixels;
            while (remaining > 0) {
                if (remaining >= 210) {
                    sb.append(SPACE_POS_210);
                    remaining -= 210;
                } else if (remaining >= 105) {
                    sb.append(SPACE_POS_105);
                    remaining -= 105;
                } else if (remaining >= 64) {
                    sb.append(SPACE_POS_64);
                    remaining -= 64;
                } else if (remaining >= 32) {
                    sb.append(SPACE_POS_32);
                    remaining -= 32;
                } else if (remaining >= 16) {
                    sb.append(SPACE_POS_16);
                    remaining -= 16;
                } else if (remaining >= 8) {
                    sb.append(SPACE_POS_8);
                    remaining -= 8;
                } else if (remaining >= 4) {
                    sb.append(SPACE_POS_4);
                    remaining -= 4;
                } else if (remaining >= 2) {
                    sb.append(SPACE_POS_2);
                    remaining -= 2;
                } else {
                    sb.append(SPACE_POS_1);
                    remaining -= 1;
                }
            }
        }
        return sb.toString();
    }

    /**
     * Đo bề rộng xấp xỉ của một chuỗi văn bản theo Minecraft Default Font glyphs (pixel).
     * Bỏ qua các mã màu định dạng (§a, §l...) và tính thêm 1px nếu đang in đậm (§l).
     */
    public static int calculateTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0;

        int width = 0;
        boolean bold = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            // Bỏ qua mã định dạng Minecraft (§ hoặc &)
            if ((c == '§' || c == '&') && i + 1 < len) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                if (next == 'l') {
                    bold = true;
                } else if (next == 'r' || (next >= '0' && next <= '9') || (next >= 'a' && next <= 'f')) {
                    bold = false;
                }
                i++; // Nhảy qua ký tự format
                continue;
            }

            int charWidth = getCharWidth(c);
            if (bold && c != ' ') {
                charWidth += 1;
            }
            width += charWidth;
        }

        return width;
    }

    private static int getCharWidth(char c) {
        if (c == ' ') return 4;
        if (c == '!' || c == '|' || c == ':' || c == '.' || c == ',' || c == '\'') return 2;
        if (c == 'l' || c == ';' || c == '`' || c == 'i') return 3;
        if (c == 'I' || c == '[' || c == ']' || c == 't') return 4;
        if (c == 'k' || c == 'f' || c == '"' || c == '(' || c == ')' || c == '{' || c == '}') return 5;
        if (c == '@' || c == '~') return 7;
        if (c == '«' || c == '»') return 6;
        if (c == '⚡' || c == '❤') return 8;

        // Ký tự unicode tiếng Việt hoặc ký tự thường mặc định
        return 6;
    }

    /**
     * Cắt ngắn chuỗi nếu bề rộng vượt quá giới hạn tối đa cho phép.
     */
    public static String truncate(String text, int maxWidthPixels) {
        if (text == null || calculateTextWidth(text) <= maxWidthPixels) {
            return text;
        }

        int ellipsisWidth = calculateTextWidth("...");
        int allowed = maxWidthPixels - ellipsisWidth;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                sb.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }
            if (calculateTextWidth(sb.toString() + c) > allowed) {
                break;
            }
            sb.append(c);
        }

        return sb.append("...").toString();
    }

    /**
     * Render thẻ HUD hoàn chỉnh dưới dạng chuỗi định dạng Legacy Minecraft.
     */
    public static String renderCardLegacyString(
            String category,
            String title,
            String indicatorText,
            String categoryColor,
            String titleColor,
            String indicatorColor
    ) {
        StringBuilder contentBuilder = new StringBuilder();
        if (category != null && !category.isEmpty()) {
            contentBuilder.append(categoryColor).append(category);
        }
        if (title != null && !title.isEmpty()) {
            if (contentBuilder.length() > 0) {
                contentBuilder.append(" §8| ");
            }
            contentBuilder.append(titleColor).append(title);
        }
        if (indicatorText != null && !indicatorText.isEmpty()) {
            if (contentBuilder.length() > 0) {
                contentBuilder.append(" ");
            }
            contentBuilder.append(indicatorColor).append(indicatorText);
        }

        String content = contentBuilder.toString();
        // Giới hạn an toàn để văn bản không tràn quá mép màn hình
        int maxSafeWidth = 320;
        if (calculateTextWidth(content) > maxSafeWidth) {
            content = truncate(content, maxSafeWidth);
        }

        int contentWidth = calculateTextWidth(content);
        int leftPadding = Math.max(0, (CARD_WIDTH - contentWidth) / 2);
        int rightPadding = Math.max(0, CARD_WIDTH - leftPadding - contentWidth);

        return CARD_BG_CHAR
                + SPACE_NEG_216
                + buildSpaceSequence(leftPadding)
                + content
                + buildSpaceSequence(rightPadding);
    }

    /**
     * Render thẻ HUD hoàn chỉnh dưới dạng Kyori Adventure Component.
     */
    public static Component renderCard(
            String category,
            String title,
            String indicatorText,
            String categoryColor,
            String titleColor,
            String indicatorColor
    ) {
        String legacy = renderCardLegacyString(category, title, indicatorText, categoryColor, titleColor, indicatorColor);
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
