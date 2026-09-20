package vn.haohan.lunar.api.world.environment;

import java.util.Locale;

/**
 * The eight lunar phases corresponding to Minecraft's celestial day progression.
 */
public enum LunarPhase {
    FULL_MOON(0, "Full Moon"),
    WANING_GIBBOUS(1, "Waning Gibbous"),
    LAST_QUARTER(2, "Last Quarter"),
    WANING_CRESCENT(3, "Waning Crescent"),
    NEW_MOON(4, "New Moon"),
    WAXING_CRESCENT(5, "Waxing Crescent"),
    FIRST_QUARTER(6, "First Quarter"),
    WAXING_GIBBOUS(7, "Waxing Gibbous");

    private final int phaseIndex;
    private final String displayName;

    LunarPhase(int phaseIndex, String displayName) {
        this.phaseIndex = phaseIndex;
        this.displayName = displayName;
    }

    public int phaseIndex() {
        return phaseIndex;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Resolves the Minecraft world's current lunar phase from its full world time.
     * In Minecraft vanilla, a lunar cycle lasts 8 in-game days (192,000 ticks).
     */
    public static LunarPhase fromFullTime(long fullTime) {
        int index = (int) ((fullTime / 24000L) % 8L);
        if (index < 0) index += 8;
        return values()[index];
    }

    /**
     * Resolves phase by name or alias (e.g. THIRD_QUARTER -> LAST_QUARTER).
     */
    public static LunarPhase parse(String name) {
        if (name == null || name.isBlank()) return null;
        String norm = name.trim().toUpperCase(Locale.ROOT);
        if ("THIRD_QUARTER".equals(norm)) return LAST_QUARTER;
        for (LunarPhase phase : values()) {
            if (phase.name().equals(norm) || String.valueOf(phase.phaseIndex).equals(norm)) {
                return phase;
            }
        }
        return null;
    }
}
