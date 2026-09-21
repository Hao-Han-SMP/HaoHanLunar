package vn.haohan.lunar.api.manager;

import vn.haohan.lunar.api.system.combat.skill.condition.Condition;
import vn.haohan.lunar.api.system.combat.skill.mechanic.Mechanic;
import vn.haohan.lunar.api.system.combat.skill.target.Targeter;

/**
 * Registry for skills, mechanics, conditions, and targeters.
 */
public interface SkillManager {

    /**
     * Registers a custom mechanic handler.
     *
     * @param name     mechanic identifier (case-insensitive)
     * @param mechanic mechanic execution callback
     */
    void registerMechanic(String name, Mechanic mechanic);

    /**
     * Registers a custom condition evaluator.
     *
     * @param name      condition identifier (case-insensitive)
     * @param condition condition evaluation callback
     */
    void registerCondition(String name, Condition condition);

    /**
     * Registers a custom targeter resolver.
     *
     * @param name     targeter identifier (case-insensitive)
     * @param targeter targeter resolver callback
     */
    void registerTargeter(String name, Targeter targeter);

    /**
     * Checks if a skill is registered under the given identifier.
     *
     * @param skillId the skill identifier
     * @return true if the skill exists in the registry
     */
    boolean hasSkill(String skillId);
}
