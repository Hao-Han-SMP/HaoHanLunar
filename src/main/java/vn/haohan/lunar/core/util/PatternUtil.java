package vn.haohan.lunar.core.util;

import java.util.regex.Pattern;

/**
 * Centralized registry and utility for compiled regular expression patterns across HaoHanLunar.
 * <p>
 * Consolidating patterns into precompiled immutable instances prevents repeated regex compilation
 * overhead during configuration loading, content linting, command execution, and combat tick cycles.
 */
public final class PatternUtil {

    private PatternUtil() {}


    /**
     * Standard namespaced identifier pattern (e.g. {@code lunar:my_mob}, {@code my_skill_1}).
     * Requires starting and ending with lowercase alphanumeric characters, allowing {@code _}, {@code .}, {@code :}, and {@code -} in between.
     */
    public static final Pattern VALID_NAMESPACED_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.:-]*[a-z0-9])?");

    /**
     * Configuration identifier pattern (e.g. {@code my_config_file-1}).
     * Requires starting and ending with lowercase alphanumeric characters, allowing {@code _}, {@code .}, and {@code -} in between.
     */
    public static final Pattern VALID_CONFIG_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.-]*[a-z0-9])?");

    /**
     * Simple identifier pattern for content linting (allowing lowercase alphanumeric, {@code _}, {@code .}, and {@code -}).
     */
    public static final Pattern VALID_LINT_ID = Pattern.compile("^[a-z0-9_.-]+$");

    /**
     * Inline targeter pattern supporting Mythic-style syntax like {@code @PlayersInRadius{r=20;limit=3}}.
     */
    public static final Pattern INLINE_TARGETER = Pattern.compile("^@?([a-zA-Z0-9_-]+)(?:\\{(.*)\\})?$");

    /**
     * Skill ID attribute pattern for item skill lines (e.g. {@code s=ground_slam}).
     */
    public static final Pattern ITEM_SKILL_ID = Pattern.compile("s=([a-zA-Z0-9_-]+)");

    /**
     * Skill cooldown attribute pattern for item skill lines (e.g. {@code cd=200}).
     */
    public static final Pattern ITEM_SKILL_COOLDOWN = Pattern.compile("cd=([0-9]+)");

    /**
     * Trigger pattern for item skill lines (e.g. {@code ~ON_ATTACK}).
     */
    public static final Pattern ITEM_SKILL_TRIGGER = Pattern.compile("~([a-zA-Z0-9]+)");

    /**
     * One or more whitespace characters.
     */
    public static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Semicolon or comma delimiter ({@code [;,]}).
     */
    public static final Pattern COMMA_OR_SEMICOLON = Pattern.compile("[;,]");

    /**
     * Comma, semicolon, or newline delimiter ({@code [,;\n]}).
     */
    public static final Pattern COMMA_SEMICOLON_OR_NEWLINE = Pattern.compile("[,;\\n]");

    /**
     * Colon or whitespace delimiter ({@code [:\s]+}).
     */
    public static final Pattern COLON_OR_WHITESPACE = Pattern.compile("[:\\s]+");

    /**
     * Comma, semicolon, or whitespace delimiter ({@code [,;\s]+}).
     */
    public static final Pattern COMMA_SEMICOLON_OR_WHITESPACE = Pattern.compile("[,;\\s]+");

    /**
     * Pipe delimiter ({@code |}).
     */
    public static final Pattern PIPE = Pattern.compile("\\|");

    /**
     * Validates whether a string is a valid namespaced identifier (e.g. mob/skill IDs).
     *
     * @param id the identifier to test
     * @return {@code true} if non-null and valid, {@code false} otherwise
     */
    public static boolean isValidNamespacedId(String id) {
        return id != null && VALID_NAMESPACED_ID.matcher(id).matches();
    }

    /**
     * Validates whether a string is a valid configuration identifier.
     *
     * @param id the identifier to test
     * @return {@code true} if non-null and valid, {@code false} otherwise
     */
    public static boolean isValidConfigId(String id) {
        return id != null && VALID_CONFIG_ID.matcher(id).matches();
    }

    /**
     * Validates whether a string is a valid lint identifier.
     *
     * @param id the identifier to test
     * @return {@code true} if non-null and valid, {@code false} otherwise
     */
    public static boolean isValidLintId(String id) {
        return id != null && VALID_LINT_ID.matcher(id).matches();
    }

    /**
     * Splits input string by whitespace. Returns empty array if input is null or blank.
     *
     * @param input the input string
     * @return array of split tokens
     */
    public static String[] splitWhitespace(String input) {
        if (input == null || input.isBlank()) return new String[0];
        return WHITESPACE.split(input.trim());
    }

    /**
     * Splits input string by comma or semicolon. Returns empty array if input is null or blank.
     *
     * @param input the input string
     * @return array of split tokens
     */
    public static String[] splitCommaOrSemicolon(String input) {
        if (input == null || input.isBlank()) return new String[0];
        return COMMA_OR_SEMICOLON.split(input);
    }
}
