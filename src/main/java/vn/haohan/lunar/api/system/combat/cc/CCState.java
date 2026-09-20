package vn.haohan.lunar.api.system.combat.cc;

/**
 * Standard Crowd-Control (CC) states for Lunar mobs.
 */
public enum CCState {
    /**
     * Complete loss of control: cannot move and cannot attack or cast skills.
     */
    STUN,

    /**
     * Immobilized: cannot move, but can still attack and cast skills.
     */
    ROOT,

    /**
     * Silenced: cannot cast active skills.
     */
    SILENCE,

    /**
     * Disarmed: cannot perform basic attacks.
     */
    DISARM,

    /**
     * Invulnerable: cannot take any damage.
     */
    INVULNERABLE,

    /**
     * Movement speed reduced by an intensity ratio.
     */
    SLOW,

    /**
     * Feared: panicked state, fleeing from the source entity.
     */
    FEAR
}
