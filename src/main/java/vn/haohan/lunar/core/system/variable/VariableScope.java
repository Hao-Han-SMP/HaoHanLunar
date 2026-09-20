package vn.haohan.lunar.core.system.variable;

/**
 * Scopes for Lunar skill and mob variables.
 */
public enum VariableScope {
    /**
     * Persists globally across the server session.
     */
    GLOBAL,

    /**
     * Scoped to the active casting mob.
     */
    CASTER,

    /**
     * Scoped to the target entity or player.
     */
    TARGET,

    /**
     * Transiently scoped to the single active skill cast execution.
     */
    CAST
}
