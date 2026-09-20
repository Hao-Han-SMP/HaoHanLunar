package vn.haohan.lunar.api.system.combat.threat;

/** Reason why a mob's threat table switched or modified target. */
public enum TargetChangeReason {
    DAMAGE_THREAT,
    HEAL_THREAT,
    PROXIMITY_THREAT,
    TAUNT,
    TARGET_LOST,
    TARGET_DEATH,
    CUSTOM
}
