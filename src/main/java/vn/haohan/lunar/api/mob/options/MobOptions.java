package vn.haohan.lunar.api.mob.options;

import java.util.Locale;
import java.util.Map;

/**
 * Immutable strongly-typed container for all 23+ MythicMobs mob options,
 * including boss anti-exploit safeguards and damage capping.
 */
public record MobOptions(
        boolean preventOtherDrops,
        boolean preventRandomEquipment,
        boolean preventSunburn,
        boolean preventKnockback,
        boolean preventLeashing,
        boolean preventRename,
        boolean preventEndermanTeleport,
        boolean preventItemPickup,
        boolean preventSilverfishInfection,
        boolean preventExploding,
        boolean preventMobKillDrops,
        boolean preventTransformation,
        boolean preventMounts,
        boolean passthroughDamage,
        boolean applyInvisibility,
        boolean preventVanillaDamage,
        DespawnMode despawnMode, int noDamageTicks, double maxDamagePerHit, boolean preventSuffocation,
        boolean preventFallDamage, boolean preventDrowning, boolean voidProtection
) {
    public enum DespawnMode {
        DESPAWN,
        PERSISTENT
    }

    public static final MobOptions DEFAULT = new MobOptions(
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            DespawnMode.DESPAWN, 20, 0.0, false, false, false, false
    );

    public MobOptions(boolean preventOtherDrops, boolean preventRandomEquipment, boolean preventSunburn, boolean preventKnockback, boolean preventLeashing, boolean preventRename, boolean preventEndermanTeleport, boolean preventItemPickup, boolean preventSilverfishInfection, boolean preventExploding, boolean preventMobKillDrops, boolean preventTransformation, boolean preventMounts, boolean passthroughDamage, boolean applyInvisibility, boolean preventVanillaDamage, DespawnMode despawnMode, int noDamageTicks) {
        this(preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, noDamageTicks, 0.0, false, false, false, false);
    }

    public static MobOptions fromMap(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return DEFAULT;
        }

        return new MobOptions(
                bool(map, "preventotherdrops", "prevent-other-drops"),
                bool(map, "preventrandomequipment", "prevent-random-equipment"),
                bool(map, "preventsunburn", "prevent-sunburn"),
                bool(map, "preventknockback", "prevent-knockback"),
                bool(map, "preventleashing", "prevent-leashing"),
                bool(map, "preventrename", "prevent-rename"),
                bool(map, "preventendermanteleport", "prevent-enderman-teleport"),
                bool(map, "preventitempickup", "prevent-item-pickup"),
                bool(map, "preventsilverfishinfection", "prevent-silverfish-infection"),
                bool(map, "preventexploding", "prevent-exploding"),
                bool(map, "preventmobkilldrops", "prevent-mob-kill-drops"),
                bool(map, "preventtransformation", "prevent-transformation"),
                bool(map, "preventmounts", "prevent-mounts"),
                bool(map, "passthroughdamage", "passthrough-damage"),
                bool(map, "applyinvisibility", "apply-invisibility"),
                bool(map, "preventvanilladamage", "prevent-vanilla-damage"),
                despawn(map, "despawnmode", "despawn-mode"), intVal(map, "nodamageticks", "no-damage-ticks", 20), doubleVal(map, "maxdamageperhit", "max-damage-per-hit", 0.0), bool(map, "preventsuffocation", "prevent-suffocation"), bool(map, "preventfalldamage", "prevent-fall-damage"), bool(map, "preventdrowning", "prevent-drowning"), bool(map, "voidprotection", "void-protection")
        );
    }

    private static boolean bool(Map<String, ?> map, String key1, String key2) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val).trim());
    }

    private static DespawnMode despawn(Map<String, ?> map, String key1, String key2) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        if (val == null) return DespawnMode.DESPAWN;
        String s = String.valueOf(val).trim().toUpperCase(Locale.ROOT);
        return "PERSISTENT".equals(s) || "TRUE".equals(s) ? DespawnMode.PERSISTENT : DespawnMode.DESPAWN;
    }

    private static int intVal(Map<String, ?> map, String key1, String key2, int def) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        if (val == null) return def;
        if (val instanceof Number n) return Math.max(0, n.intValue());
        try {
            return Math.max(0, Integer.parseInt(String.valueOf(val).trim()));
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static double doubleVal(Map<String, ?> map, String key1, String key2, double def) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        if (val == null) return def;
        if (val instanceof Number n) return Math.max(0.0, n.doubleValue());
        try {
            return Math.max(0.0, Double.parseDouble(String.valueOf(val).trim()));
        }
        catch (NumberFormatException ignored) {
            return def;
        }
    }
}
