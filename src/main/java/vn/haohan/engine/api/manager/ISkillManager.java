package vn.haohan.engine.api.manager;

import vn.haohan.engine.api.system.combat.skill.condition.ICondition;
import vn.haohan.engine.api.system.combat.skill.mechanic.IMechanic;
import vn.haohan.engine.api.system.combat.skill.target.ITargeter;

/**
 * Registry for skills, mechanics, conditions, and targeters.
 */
public interface ISkillManager {

    /**
     * Registers a custom mechanic handler.
     *
     * @param name     mechanic identifier (case-insensitive)
     * @param mechanic mechanic execution callback
     */
    void registerMechanic(String name, IMechanic mechanic);

    /**
     * Registers a custom condition evaluator.
     *
     * @param name      condition identifier (case-insensitive)
     * @param condition condition evaluation callback
     */
    void registerCondition(String name, ICondition condition);

    /**
     * Registers a custom targeter resolver.
     *
     * @param name     targeter identifier (case-insensitive)
     * @param targeter targeter resolver callback
     */
    void registerTargeter(String name, ITargeter targeter);

    /**
     * Checks if a skill is registered under the given identifier.
     *
     * @param skillId the skill identifier
     * @return true if the skill exists in the registry
     */
    boolean hasSkill(String skillId);
}
