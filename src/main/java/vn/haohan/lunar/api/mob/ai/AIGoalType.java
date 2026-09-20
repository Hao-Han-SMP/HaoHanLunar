package vn.haohan.lunar.api.mob.ai;

import java.util.Locale;

/**
 * Enumeration of supported AI behavior and target selector goal types.
 */
public enum AIGoalType {
    CLEAR,
    MOVE_TO_TARGET,
    MELEE_ATTACK,
    LOOK_AT_PLAYERS,
    PATROL,
    FLEE_SUN,
    TARGET_DAMAGERS,
    TARGET_PLAYERS,
    CUSTOM;

    public static AIGoalType fromString(String name) {
        if (name == null || name.isBlank()) return CUSTOM;
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return switch (normalized) {
            case "clear" -> CLEAR;
            case "movetotarget" -> MOVE_TO_TARGET;
            case "meleeattack" -> MELEE_ATTACK;
            case "lookatplayers", "lookatplayer" -> LOOK_AT_PLAYERS;
            case "patrol", "wander", "randomstroll" -> PATROL;
            case "fleesun" -> FLEE_SUN;
            case "targetdamagers", "hurtby" -> TARGET_DAMAGERS;
            case "targetplayers", "nearestplayer", "nearestattackable" -> TARGET_PLAYERS;
            default -> CUSTOM;
        };
    }
}
