package vn.haohan.lunar.api.system.combat;

/**
 * Classification of damage dealt by custom skills, mobs, and combat mechanics.
 */
public enum DamageType {
    /** Standard physical combat damage, mitigated by armor and resistance. */
    PHYSICAL,
    /** Magical damage from elemental spells and celestial abilities. */
    MAGICAL,
    /** Pure unmitigated damage that ignores armor, absorption, and resistances. */
    TRUE
}
