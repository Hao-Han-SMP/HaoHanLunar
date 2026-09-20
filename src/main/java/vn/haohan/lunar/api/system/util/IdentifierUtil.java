package vn.haohan.lunar.api.system.util;

import java.util.Locale;

/**
 * Standard utility for normalizing identifiers, config keys, and registry IDs.
 * Ensures consistent trimmed lowercase representation with {@link Locale#ROOT}
 * to prevent locale-specific collation bugs across platforms.
 */
public final class IdentifierUtil {

    private IdentifierUtil() {}

    /**
     * Normalizes a string identifier by trimming whitespace and converting to lowercase
     * using {@link Locale#ROOT}. Returns an empty string if {@code id} is null.
     *
     * @param id the raw identifier string
     * @return trimmed lowercase identifier or empty string if null
     */
    public static String normalizeKey(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks whether two identifier strings are equal after normalization.
     *
     * @param id1 first identifier
     * @param id2 second identifier
     * @return true if both normalized identifiers match
     */
    public static boolean equalsNormalized(String id1, String id2) {
        return normalizeKey(id1).equals(normalizeKey(id2));
    }
}
