package vn.haohan.lunar.api.system.combat.skill.aura;

/**
 * Merge strategies when applying an aura that is already active on the same target.
 */
public enum StackMode {
    /** Refreshes duration to full without increasing stack count. */
    REFRESH,
    /** Increments stack count (up to maxStacks) and refreshes duration. */
    ADD_STACK,
    /** Ignores the new aura application if one is already running. */
    IGNORE
}
