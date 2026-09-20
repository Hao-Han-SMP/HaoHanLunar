package vn.haohan.lunar.api.manager;

import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamageResult;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Execution pipeline for damage processing and combat interactions.
 */
public interface CombatManager {

    /**
     * Executes the damage pipeline using the provided combat context.
     *
     * @param context combat execution parameters
     * @return execution result including final damage, mitigation, and cancellation status
     */
    DamageResult execute(DamageContext context);

    /**
     * Registers a pre-check predicate to validate combat conditions before processing modifiers.
     *
     * @param check function returning a rejection reason, or null if validation passes
     * @return this manager instance for chaining
     */
    CombatManager registerPreCheck(Function<DamageContext, String> check);

    /**
     * Registers a damage modifier callback to alter damage values or flags.
     *
     * @param modifier consumer modifying the combat context
     * @return this manager instance for chaining
     */
    CombatManager registerModifier(Consumer<DamageContext> modifier);

    /**
     * Registers an immunity check returning an immunity description if damage should be blocked.
     *
     * @param checker function returning an immunity reason, or null if vulnerable
     * @return this manager instance for chaining
     */
    CombatManager registerImmunityChecker(Function<DamageContext, String> checker);
}
