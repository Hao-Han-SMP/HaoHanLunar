package vn.haohan.lunar.api.manager;

import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamageResult;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Public interface for custom combat mechanics and damage calculation pipelines.
 */
public interface CombatManager {

    /**
     * Executes the damage pipeline with the given context.
     *
     * @param context Immutable combat parameters.
     * @return Execution result including damage dealt, mitigation, and cancellation status.
     */
    DamageResult execute(DamageContext context);

    /**
     * Registers a pre-check validation rule.
     */
    CombatManager registerPreCheck(Function<DamageContext, String> check);

    /**
     * Registers a damage modifier.
     */
    CombatManager registerModifier(Consumer<DamageContext> modifier);

    /**
     * Registers an immunity checker.
     */
    CombatManager registerImmunityChecker(Function<DamageContext, String> checker);
}
