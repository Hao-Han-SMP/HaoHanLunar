package vn.haohan.lunar.api.system.mob.options;

import vn.haohan.lunar.api.system.mob.MobOptionDefinition;

import java.util.Locale;
import java.util.Map;

/**
 * Immutable configuration options for custom mob runtime behavior.
 * Parsed from the mob definition options map.
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
        DespawnMode despawnMode,
        int noDamageTicks,
        double maxDamagePerHit,
        boolean preventSuffocation,
        boolean preventFallDamage,
        boolean preventDrowning,
        boolean voidProtection,
        double damageCap,
        double leashRange,
        double softLeashRadius,
        boolean healOnLeash,
        boolean resetThreatOnLeash,
        int leashInvulnerableTicks,
        String faction,
        boolean preventFriendlyFire
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
            DespawnMode.DESPAWN, 20, 0.0, false, false, false, false,
            0.0, 0.0, 0.0, true, true, 60, null, true
    );

    public MobOptions(boolean preventOtherDrops, boolean preventRandomEquipment, boolean preventSunburn, boolean preventKnockback, boolean preventLeashing, boolean preventRename, boolean preventEndermanTeleport, boolean preventItemPickup, boolean preventSilverfishInfection, boolean preventExploding, boolean preventMobKillDrops, boolean preventTransformation, boolean preventMounts, boolean passthroughDamage, boolean applyInvisibility, boolean preventVanillaDamage, DespawnMode despawnMode, int noDamageTicks, double maxDamagePerHit, boolean preventSuffocation, boolean preventFallDamage, boolean preventDrowning, boolean voidProtection, double damageCap, double leashRange, double softLeashRadius, boolean healOnLeash, boolean resetThreatOnLeash, int leashInvulnerableTicks) {
        this(preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, noDamageTicks, maxDamagePerHit, preventSuffocation, preventFallDamage, preventDrowning, voidProtection, damageCap, leashRange, softLeashRadius, healOnLeash, resetThreatOnLeash, leashInvulnerableTicks, null, true);
    }

    public MobOptions(boolean preventOtherDrops, boolean preventRandomEquipment, boolean preventSunburn, boolean preventKnockback, boolean preventLeashing, boolean preventRename, boolean preventEndermanTeleport, boolean preventItemPickup, boolean preventSilverfishInfection, boolean preventExploding, boolean preventMobKillDrops, boolean preventTransformation, boolean preventMounts, boolean passthroughDamage, boolean applyInvisibility, boolean preventVanillaDamage, DespawnMode despawnMode, int noDamageTicks, double maxDamagePerHit, boolean preventSuffocation, boolean preventFallDamage, boolean preventDrowning, boolean voidProtection, double damageCap, double leashRange, double softLeashRadius, boolean healOnLeash, boolean resetThreatOnLeash) {
        this(preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, noDamageTicks, maxDamagePerHit, preventSuffocation, preventFallDamage, preventDrowning, voidProtection, damageCap, leashRange, softLeashRadius, healOnLeash, resetThreatOnLeash, 60);
    }

    public MobOptions(boolean preventOtherDrops, boolean preventRandomEquipment, boolean preventSunburn, boolean preventKnockback, boolean preventLeashing, boolean preventRename, boolean preventEndermanTeleport, boolean preventItemPickup, boolean preventSilverfishInfection, boolean preventExploding, boolean preventMobKillDrops, boolean preventTransformation, boolean preventMounts, boolean passthroughDamage, boolean applyInvisibility, boolean preventVanillaDamage, DespawnMode despawnMode, int noDamageTicks) {
        this(preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, noDamageTicks, 0.0, false, false, false, false, 0.0, 0.0, 0.0, true, true);
    }

    public MobOptions(boolean preventOtherDrops, boolean preventRandomEquipment, boolean preventSunburn, boolean preventKnockback, boolean preventLeashing, boolean preventRename, boolean preventEndermanTeleport, boolean preventItemPickup, boolean preventSilverfishInfection, boolean preventExploding, boolean preventMobKillDrops, boolean preventTransformation, boolean preventMounts, boolean passthroughDamage, boolean applyInvisibility, boolean preventVanillaDamage, DespawnMode despawnMode, int noDamageTicks, double maxDamagePerHit, boolean preventSuffocation, boolean preventFallDamage, boolean preventDrowning, boolean voidProtection) {
        this(preventOtherDrops, preventRandomEquipment, preventSunburn, preventKnockback, preventLeashing, preventRename, preventEndermanTeleport, preventItemPickup, preventSilverfishInfection, preventExploding, preventMobKillDrops, preventTransformation, preventMounts, passthroughDamage, applyInvisibility, preventVanillaDamage, despawnMode, noDamageTicks, maxDamagePerHit, preventSuffocation, preventFallDamage, preventDrowning, voidProtection, 0.0, 0.0, 0.0, true, true);
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
                despawn(map, "despawnmode", "despawn-mode"),
                intVal(map, "nodamageticks", "no-damage-ticks", 20),
                doubleVal(map, "maxdamageperhit", "max-damage-per-hit", 0.0),
                bool(map, "preventsuffocation", "prevent-suffocation"),
                bool(map, "preventfalldamage", "prevent-fall-damage"),
                bool(map, "preventdrowning", "prevent-drowning"),
                bool(map, "voidprotection", "void-protection"),
                doubleVal(map, "damagecap", "damage-cap", doubleVal(map, "dpscap", "dps-cap", 0.0)),
                doubleVal(map, "leashrange", "leash-range", 0.0),
                doubleVal(map, "softleashradius", "soft-leash-radius", 0.0),
                boolWithDefault(map, "healonleash", "heal-on-leash", true),
                boolWithDefault(map, "resetthreatonleash", "reset-threat-on-leash", true),
                intVal(map, "leashinvulnerableticks", "leash-invulnerable-ticks",
                        boolWithDefault(map, "invulnerableonleash", "invulnerable-on-leash", true) ? 60 : 0),
                stringVal(map, "faction", "Faction"),
                boolWithDefault(map, "preventfriendlyfire", "prevent-friendly-fire", true)
        );
    }

    private static Object unwrap(Object val) {
        if (val instanceof MobOptionDefinition mod) {
            return mod.value();
        }
        return val;
    }

    private static String stringVal(Map<String, ?> map, String... keys) {
        for (String k : keys) {
            Object val = map.get(k);
            if (val != null) {
                String s = String.valueOf(unwrap(val)).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return null;
    }

    private static boolean bool(Map<String, ?> map, String key1, String key2) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        val = unwrap(val);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val).trim());
    }

    private static boolean boolWithDefault(Map<String, ?> map, String key1, String key2, boolean def) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        val = unwrap(val);
        if (val == null) return def;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val).trim());
    }

    private static DespawnMode despawn(Map<String, ?> map, String key1, String key2) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        val = unwrap(val);
        if (val == null) return DespawnMode.DESPAWN;
        String s = String.valueOf(val).trim().toUpperCase(Locale.ROOT);
        return "PERSISTENT".equals(s) || "TRUE".equals(s) ? DespawnMode.PERSISTENT : DespawnMode.DESPAWN;
    }

    private static int intVal(Map<String, ?> map, String key1, String key2, int def) {
        Object val = map.get(key1);
        if (val == null) val = map.get(key2);
        val = unwrap(val);
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
        val = unwrap(val);
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
