package vn.haohan.lunar.api.system.mob.ai;

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
    RESTRICT_SUN,
    TARGET_DAMAGERS,
    TARGET_PLAYERS,
    TARGET_THREAT,
    CIRCLE,
    FLEE_PLAYERS,
    FLEE_FACTION,
    FLOAT,
    BOW_ATTACK,
    CROSSBOW_ATTACK,
    RANGED_ATTACK,
    OPEN_DOOR,
    BREAK_DOOR,
    PANIC,
    LEAP_AT_TARGET,
    TARGET_MONSTERS,
    TARGET_VILLAGERS,
    TARGET_OTHER_FACTION,
    TARGET_SPECIFIC_FACTION,
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
            case "restrictsun" -> RESTRICT_SUN;
            case "targetdamagers", "hurtby" -> TARGET_DAMAGERS;
            case "targetplayers", "nearestplayer", "nearestattackable" -> TARGET_PLAYERS;
            case "targetthreat", "threat", "threattarget" -> TARGET_THREAT;
            case "circle" -> CIRCLE;
            case "fleeplayers", "fleeplayer" -> FLEE_PLAYERS;
            case "fleefaction", "fleefactions" -> FLEE_FACTION;
            case "float", "swim" -> FLOAT;
            case "bowattack", "rangedbowattack" -> BOW_ATTACK;
            case "crossbowattack", "rangedcrossbowattack" -> CROSSBOW_ATTACK;
            case "rangedattack", "ranged" -> RANGED_ATTACK;
            case "opendoor" -> OPEN_DOOR;
            case "breakdoor" -> BREAK_DOOR;
            case "panic" -> PANIC;
            case "leapat", "leapattarget", "leap" -> LEAP_AT_TARGET;
            case "targetmonsters", "nearestmonster" -> TARGET_MONSTERS;
            case "targetvillagers", "nearestvillager" -> TARGET_VILLAGERS;
            case "targetotherfaction", "otherfaction" -> TARGET_OTHER_FACTION;
            case "targetspecificfaction", "specificfaction" -> TARGET_SPECIFIC_FACTION;
            default -> CUSTOM;
        };
    }
}
