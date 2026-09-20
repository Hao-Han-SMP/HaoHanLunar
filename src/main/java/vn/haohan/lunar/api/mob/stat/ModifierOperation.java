package vn.haohan.lunar.api.mob.stat;

/**
 * Arithmetic operations supported by stat modifiers.
 */
public enum ModifierOperation {
    /**
     * Adds a fixed flat amount to the base stat.
     */
    FLAT,

    /**
     * Adds a base percentage sum (e.g. +0.20 for +20%).
     */
    PERCENT_ADD,

    /**
     * Multiplies the cumulative value by (1 + value) (e.g. *1.15).
     */
    PERCENT_MULT
}
