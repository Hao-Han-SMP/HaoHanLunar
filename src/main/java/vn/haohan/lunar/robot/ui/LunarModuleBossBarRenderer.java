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
    /**
     * Đo bề rộng chính xác của chuỗi theo font DejaVu Sans Bold kích thước 9.0 (haohan_hud.ttf).
     * Bỏ qua các mã màu định dạng (§a, §l...) và tính thêm 1px nếu đang in đậm (§l).
     */
    public static int calculateTextWidth(String text) {
        if (text == null || text.isEmpty()) return 0;

        double width = 0.0;
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

            double charWidth = getCharWidth(c);
            if (bold && c != ' ') {
                charWidth += 1.0;
            }
            width += charWidth;
        }

        return (int) Math.round(width);
    }

    private static double getCharWidth(char c) {
        if (" ijlìíĩỉị'".indexOf(c) != -1) return 3.0;
        if (",-./:;IJÌÍĨỈỊ!\"".indexOf(c) != -1) return 3.5;
        if ("()[]f".indexOf(c) != -1) return 4.0;
        if ("*_rt".indexOf(c) != -1) return 4.5;
        if ("?z".indexOf(c) != -1) return 5.0;
        if ("Lcs".indexOf(c) != -1) return 5.5;
        if ("EFTaekovxy«»•ÈÉÊàáâãèéêòóôõýăơạảấầẩẫậắằẳẵặẸẹẺẻẼẽẾếỀềỂểỄễỆệọỏốồổỗộớờởỡợỳỵỷỹ".indexOf(c) != -1) return 6.0;
        if ("$0123456789CPSYZbdghnpqu{}ÝùúđũưụủứừửữựỲỴỶỸ⚡".indexOf(c) != -1) return 6.5;
        if ("ABKRVXÀÁÂÃĂẠẢẤẦẨẪẬẮẰẲẴẶ▲▼".indexOf(c) != -1) return 7.0;
        if ("#+=DGHNOQUÒÓÔÕÙÚĐŨƯỌỎỐỒỔỖỘỤỦỨỪỬỮỰ✔❤".indexOf(c) != -1) return 7.5;
        if ("&ƠỚỜỞỠỢ♥♨".indexOf(c) != -1) return 8.0;
        if (c == 'w') return 8.5;
        if ("%@M".indexOf(c) != -1) return 9.0;
        if (c == 'm') return 9.5;
        if (c == 'W') return 10.0;
        return 7.0;
    }

    /**
     * Chuyển toàn bộ ký tự nội dung sang chữ in hoa, nhưng giữ nguyên các mã màu Minecraft
     * (§a, §c...) ở dạng chữ thường để không phá vỡ bộ giải mã màu hoặc rơi vào ký tự lạ.
     */
    public static String toUpperCasePreservingColors(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < len) {
                sb.append(c);
                sb.append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * Bóc tách một chuỗi (có thể chứa mã màu Minecraft legacy §...) thành Adventure Component
     * sạch (không còn ký tự § thô) và áp dụng font chỉ định cho toàn bộ cây Component.
     */
    public static Component buildLayerComponent(
            String text,
            net.kyori.adventure.text.format.TextColor defaultColor,
            net.kyori.adventure.key.Key font
    ) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        Component parsed = LegacyComponentSerializer.legacySection().deserialize(text);
        if (parsed.color() == null && defaultColor != null) {
            parsed = parsed.color(defaultColor);
        }
        return parsed.font(font);
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

    public static final net.kyori.adventure.key.Key FONT_CARD = net.kyori.adventure.key.Key.key("haohan", "module_hud");
    public static final net.kyori.adventure.key.Key FONT_TOP  = net.kyori.adventure.key.Key.key("haohan", "hud_top");
    public static final net.kyori.adventure.key.Key FONT_MID  = net.kyori.adventure.key.Key.key("haohan", "hud_mid");
    public static final net.kyori.adventure.key.Key FONT_BOT  = net.kyori.adventure.key.Key.key("haohan", "hud_bot");

    /**
     * Render thẻ HUD chuẩn phong cách MCC Island gồm 3 tầng văn bản xếp dọc:
     * Dòng 1 (ascent cao): Category / Module ID (In hoa)
     * Dòng 2 (ascent giữa): Action / Trạng thái chính (In hoa in đậm)
     * Dòng 3 (ascent thấp): Indicator / Thanh tiến trình hoặc pin
     */
    public static Component render3LineCard(
            String category,
            String title,
            String indicatorText,
            net.kyori.adventure.text.format.TextColor categoryColor,
            net.kyori.adventure.text.format.TextColor titleColor,
            net.kyori.adventure.text.format.TextColor indicatorColor
    ) {
        int maxInnerWidth = CARD_WIDTH; // Tận dụng tối đa bề rộng khung thẻ 216px
        String safeCategory = truncate(category != null ? toUpperCasePreservingColors(category) : "", maxInnerWidth);
        String safeTitle = truncate(title != null ? toUpperCasePreservingColors(title) : "", maxInnerWidth);
        String safeIndicator = truncate(indicatorText != null ? indicatorText : "", maxInnerWidth);

        int w1 = calculateTextWidth(safeCategory);
        int left1 = Math.max(0, (CARD_WIDTH - w1) / 2);

        int w2 = calculateTextWidth(safeTitle);
        int left2 = Math.max(0, (CARD_WIDTH - w2) / 2);

        int w3 = calculateTextWidth(safeIndicator);
        int left3 = Math.max(0, (CARD_WIDTH - w3) / 2);
        int right3 = Math.max(0, CARD_WIDTH - left3 - w3);

        Component comp1 = buildLayerComponent(safeCategory, categoryColor != null ? categoryColor : net.kyori.adventure.text.format.NamedTextColor.AQUA, FONT_TOP);
        Component comp2 = buildLayerComponent(safeTitle, titleColor != null ? titleColor : net.kyori.adventure.text.format.NamedTextColor.WHITE, FONT_MID);
        Component comp3 = buildLayerComponent(safeIndicator, indicatorColor != null ? indicatorColor : net.kyori.adventure.text.format.NamedTextColor.YELLOW, FONT_BOT);

        return Component.text()
                // 1. Nền thẻ và lùi con trỏ về x = 0
                .append(Component.text(CARD_BG_CHAR, net.kyori.adventure.text.format.NamedTextColor.WHITE).font(FONT_CARD))
                .append(Component.text(SPACE_NEG_216))

                // 2. Dòng 1: Căn giữa theo X, font hud_top nâng lên theo Y, lùi về 0
                .append(Component.text(buildSpaceSequence(left1)))
                .append(comp1)
                .append(Component.text(buildSpaceSequence(-left1 - w1)))

                // 3. Dòng 2: Căn giữa theo X, font hud_mid ở giữa theo Y, lùi về 0
                .append(Component.text(buildSpaceSequence(left2)))
                .append(comp2)
                .append(Component.text(buildSpaceSequence(-left2 - w2)))

                // 4. Dòng 3: Căn giữa theo X, font hud_bot hạ xuống theo Y, tiến bù đủ đúng bề rộng thẻ (216px)
                .append(Component.text(buildSpaceSequence(left3)))
                .append(comp3)
                .append(Component.text(buildSpaceSequence(right3)))
                .build();
    }

    public static Component render3LineCard(
            String category,
            String title,
            String indicatorText,
            String categoryColorCode,
            String titleColorCode,
            String indicatorColorCode
    ) {
        return render3LineCard(
                category,
                title,
                indicatorText,
                parseLegacyColor(categoryColorCode, net.kyori.adventure.text.format.NamedTextColor.AQUA),
                parseLegacyColor(titleColorCode, net.kyori.adventure.text.format.NamedTextColor.WHITE),
                parseLegacyColor(indicatorColorCode, net.kyori.adventure.text.format.NamedTextColor.YELLOW)
        );
    }

    public static net.kyori.adventure.text.format.TextColor parseLegacyColor(String code, net.kyori.adventure.text.format.TextColor fallback) {
        if (code == null || code.isEmpty()) return fallback;
        if (code.contains("§a")) return net.kyori.adventure.text.format.NamedTextColor.GREEN;
        if (code.contains("§b")) return net.kyori.adventure.text.format.NamedTextColor.AQUA;
        if (code.contains("§c")) return net.kyori.adventure.text.format.NamedTextColor.RED;
        if (code.contains("§d")) return net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
        if (code.contains("§e")) return net.kyori.adventure.text.format.NamedTextColor.YELLOW;
        if (code.contains("§f")) return net.kyori.adventure.text.format.NamedTextColor.WHITE;
        if (code.contains("§6")) return net.kyori.adventure.text.format.NamedTextColor.GOLD;
        if (code.contains("§7")) return net.kyori.adventure.text.format.NamedTextColor.GRAY;
        if (code.contains("§8")) return net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
        if (code.contains("§9")) return net.kyori.adventure.text.format.NamedTextColor.BLUE;
        if (code.contains("§2")) return net.kyori.adventure.text.format.NamedTextColor.DARK_GREEN;
        if (code.contains("§3")) return net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA;
        if (code.contains("§4")) return net.kyori.adventure.text.format.NamedTextColor.DARK_RED;
        if (code.contains("§5")) return net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE;
        return fallback;
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
        int maxSafeWidth = 450;
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
