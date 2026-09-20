package vn.haohan.lunar.api.system.combat.skill.projectile;

/**
 * Surface and entity collision handling behavior.
 */
public enum SurfaceMode {
    /** Explodes or terminates on first collision. */
    DETONATE,
    /** Reflects velocity off the surface normal. */
    BOUNCE,
    /** Passes through up to N entities or blocks before expiring. */
    PIERCE,
    /** Deflects along the tangent plane of the contacted surface. */
    SLIDE
}
