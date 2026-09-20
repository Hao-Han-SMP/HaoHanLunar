package vn.haohan.lunar.api.system.combat.skill.targeter;

import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;

import java.util.Collection;
import java.util.Map;

/**
 * Base contract for resolving targets from a cast context.
 *
 * @param <T> Target type (LivingEntity or Location)
 */
public interface SkillTargeter<T> {

    /** Identifier name of this targeter (e.g. self, target, players_in_radius). */
    String name();

    /**
     * Resolves targets into an unmodifiable collection snapshot.
     *
     * @param context the active cast context
     * @param parameters configuration parameters passed to the targeter
     * @return unmodifiable snapshot of targets, never null
     */
    Collection<T> resolve(SkillCastContext context, Map<String, Object> parameters);
}
