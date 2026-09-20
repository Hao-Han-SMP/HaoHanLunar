package vn.haohan.lunar.api.manager;

import vn.haohan.lunar.api.system.combat.skill.target.Targeter;
import vn.haohan.lunar.api.system.combat.skill.condition.Condition;
import vn.haohan.lunar.api.system.combat.skill.mechanic.Mechanic;

/**
 * Public interface for skill registry and custom mechanic/condition/targeter registration.
 */
public interface SkillManager {

    /**
     * Registers a custom mechanic handler.
     *
     * @param name     Mechanic identifier (case-insensitive).
     * @param mechanic The mechanic execution callback.
     */
    void registerMechanic(String name, Mechanic mechanic);

    /**
     * Registers a custom condition handler.
     *
     * @param name      Condition identifier (case-insensitive).
     * @param condition The condition evaluation callback.
     */
    void registerCondition(String name, Condition condition);

    /**
     * Registers a custom targeter resolver.
     *
     * @param name     Targeter identifier (case-insensitive).
     * @param targeter The targeter resolver callback.
     */
    void registerTargeter(String name, Targeter targeter);

    /**
     * Checks if a skill is registered by ID.
     */
    boolean hasSkill(String skillId);
}
