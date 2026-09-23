package vn.haohan.lunar.api.system.mob.equipment;

import java.util.Locale;

/**
 * Equipment slot enumeration matching Bukkit LivingEntity equipment slots.
 */
public enum EquipmentSlot {
    MAIN_HAND,
    OFF_HAND,
    HEAD,
    CHEST,
    LEGS,
    FEET;

    public static EquipmentSlot fromString(String raw) {
        if (raw == null || raw.isBlank()) return MAIN_HAND;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace("-", "_").replace(" ", "_");
        return switch (normalized) {
            case "HAND", "MAINHAND", "MAIN_HAND" -> MAIN_HAND;
            case "OFFHAND", "OFF_HAND" -> OFF_HAND;
            case "HELMET", "HEAD" -> HEAD;
            case "CHESTPLATE", "CHEST" -> CHEST;
            case "LEGGINGS", "LEGS" -> LEGS;
            case "BOOTS", "FEET" -> FEET;
            default -> throw new IllegalArgumentException("Unknown equipment slot: " + raw);
        };
    }
}
