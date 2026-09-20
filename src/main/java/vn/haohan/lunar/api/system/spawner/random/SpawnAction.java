package vn.haohan.lunar.api.system.spawner.random;

/**
 * Strategy for random mob spawning and replacement.
 */
public enum SpawnAction {
    /**
     * Intercepts natural vanilla spawns and replaces them with a custom Lunar mob.
     */
    REPLACE,

    /**
     * Adds and spawns additional mobs in a ring around active players.
     */
    ADD,

    /**
     * Denies/cancels vanilla spawns entirely within configured conditions/regions.
     */
    DENY
}
