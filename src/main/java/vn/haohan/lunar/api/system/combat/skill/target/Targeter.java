package vn.haohan.lunar.api.system.combat.skill.target;

import java.util.List;

/**
 * Functional contract for resolving target entities or locations for mob skills.
 */
@FunctionalInterface
public interface Targeter {

    /**
     * Resolves a list of targets based on the provided context.
     *
     * @param context The context containing caster, origin, radius, etc.
     * @return List of resolved TargetRef entries.
     */
    List<TargetRef> resolve(TargeterContext context);
}
