package vn.haohan.lunar.core.features.boss.warden;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;

import java.util.*;

/** Registry facade for the existing Java-backed Warden skill implementations. */
public final class WardenSkillRegistry {

    public static final String AERIAL_SLASH_COMBO = "aerial_slash_combo";
    public static final String GROUND_SLAM = "ground_slam";
    public static final String CELESTIAL_SUMMON = "celestial_summon";
    public static final String SHIELD_BLOCK = "shield_block";
    public static final String SHIELD_BLOCK_PUSH = "shield_block_push";
    public static final String SHIELD_CHARGE = "shield_charge";
    public static final String SHIELD_SWORD_SLAM = "shield_sword_slam";
    public static final String TARGETED_LIGHT_STRIKE = "targeted_light_strike";
    public static final String THRUST_FLING = "thrust_fling";
    public static final String PURSUIT = "pursuit";

    private final Map<String, WardenSkillInvoker> skills = new LinkedHashMap<>();

    public WardenSkillRegistry(WardenSkillInvoker invoker) {
        Objects.requireNonNull(invoker, "Warden skill invoker must not be null");
        for (String id : skillIds()) skills.put(id, (ignored, context, parameters) -> invoker.invoke(id, context, parameters));
    }

    public boolean contains(String skillId) {
        return skills.containsKey(normalize(skillId));
    }

    public boolean cast(String skillId, SkillCastContext context, Map<String, Object> parameters) {
        WardenSkillInvoker skill = skills.get(normalize(skillId));
        if (skill == null || context == null || context.isCancelled()) return false;
        skill.invoke(normalize(skillId), context, Map.copyOf(Objects.requireNonNull(parameters, "Skill parameters must not be null")));
        return !context.isCancelled();
    }

    public Map<String, WardenSkillInvoker> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(skills));
    }

    public static java.util.List<String> skillIds() {
        return java.util.List.of(AERIAL_SLASH_COMBO, GROUND_SLAM, CELESTIAL_SUMMON, SHIELD_BLOCK,
                SHIELD_BLOCK_PUSH, SHIELD_CHARGE, SHIELD_SWORD_SLAM, TARGETED_LIGHT_STRIKE, THRUST_FLING, PURSUIT);
    }

    private static String normalize(String value) {
        Objects.requireNonNull(value, "Skill ID must not be null");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    public interface WardenSkillInvoker {
        void invoke(String skillId, SkillCastContext context, Map<String, Object> parameters);
    }
}
