package vn.haohan.lunar.api.system.combat;

import vn.haohan.lunar.api.manager.CombatManager;
import vn.haohan.lunar.api.mob.Mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Centralized combat execution pipeline for custom mob abilities and combat mechanics.
 * Standardizes pre-checks, damage modifiers, immunity verification, armor mitigation,
 * safe execution with recursion guards, and post-damage event dispatching.
 */
public final class DamagePipeline implements CombatManager {

    private final List<Function<DamageContext, String>> preChecks = new CopyOnWriteArrayList<>();
    private final List<Consumer<DamageContext>> modifiers = new CopyOnWriteArrayList<>();
    private final List<Function<DamageContext, String>> immunityCheckers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<DamageContext, Double>> postHandlers = new CopyOnWriteArrayList<>();

    private static Optional<ActiveMob> asActive(Optional<? extends Mob> mobOpt) {
        return mobOpt.filter(ActiveMob.class::isInstance).map(ActiveMob.class::cast);
    }

    public DamagePipeline() {
        // Built-in pre-checks
        registerPreCheck(ctx -> {
            if (ctx.isCancelled()) {
                return ctx.cancelReason().orElse("Damage context explicitly cancelled");
            }
            LivingEntity victim = ctx.victim();
            if (victim == null || !victim.isValid() || victim.isDead()) {
                return "Dead or invalid victim";
            }
            if (victim.isInvulnerable()) {
                return "Victim is invulnerable";
            }
            // Victim Crowd-Control check: Invulnerable state
            if (asActive(ctx.victimMob()).map(mob -> mob.crowdControl().isInvulnerable()).orElse(false)) {
                return "Victim is invulnerable (CCState.INVULNERABLE)";
            }
            // Attacker Crowd-Control check: Stunned or Disarmed
            ActiveMob attackerActive = asActive(ctx.attackerMob()).orElse(null);
            if (attackerActive != null) {
                if (attackerActive.crowdControl().isStunned()) {
                    return "Attacker is stunned (CCState.STUN)";
                }
                if (attackerActive.crowdControl().isDisarmed() && isBasicAttack(ctx.cause())) {
                    return "Attacker is disarmed (CCState.DISARM)";
                }
                if (attackerActive.isUsingDamageSkill()) {
                    return "Recursion guard: attacker is already executing a damage skill";
                }
            }
            return null;
        });

        // Built-in modifiers: mob victim damage modifiers
        registerModifier(ctx -> {
            asActive(ctx.victimMob()).ifPresent(mob -> mob.damageModifiers().apply(ctx));
        });

        // Built-in modifiers: attacker critical strike calculation
        registerModifier(ctx -> {
            asActive(ctx.attackerMob()).ifPresent(attacker -> {
                var stats = attacker.stats().snapshot(0);
                double critChance = stats.critChance();
                if (critChance > 0.0 && Math.random() < critChance) {
                    ctx.setCritical(true);
                    double critMult = stats.critDamage();
                    if (critMult > 0.0) {
                        ctx.multiplyDamage(critMult);
                    }
                }
            });
        });

        // Built-in modifiers: victim armor mitigation
        registerModifier(ctx -> {
            if (ctx.isIgnoreArmor()) return;
            asActive(ctx.victimMob()).ifPresent(victim -> {
                var stats = victim.stats().snapshot(0);
                double armor = stats.armor();
                if (armor > 0.0) {
                    double reduction = armor / (armor + 100.0);
                    ctx.multiplyDamage(1.0 - reduction);
                }
            });
        });

        // Built-in modifier: soft leash resistance (90% damage reduction)
        registerModifier(ctx -> {
            asActive(ctx.victimMob()).ifPresent(victim -> {
                if (victim.isSoftLeashed()) {
                    ctx.multiplyDamage(0.10);
                }
            });
        });

        // Built-in post handler: attacker lifesteal
        registerPostHandler((ctx, appliedDamage) -> {
            if (appliedDamage <= 0.0) return;
            asActive(ctx.attackerMob()).ifPresent(attacker -> {
                var stats = attacker.stats().snapshot(0);
                double lifesteal = stats.lifesteal();
                if (lifesteal > 0.0) {
                    double heal = appliedDamage * lifesteal;
                    LivingEntity entity = attacker.entity();
                    if (entity != null && entity.isValid() && !entity.isDead()) {
                        double current = entity.getHealth();
                        double max = Double.MAX_VALUE;
                        try {
                            var attr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                            if (attr != null) {
                                max = attr.getValue();
                            }
                        } catch (Throwable ignored) {}
                        try {
                            entity.setHealth(Math.min(max, current + heal));
                        } catch (Throwable ignored) {}
                    }
                }
            });
        });

        // Built-in immunity checker: mob victim immunity table
        registerImmunityChecker(ctx -> {
            return asActive(ctx.victimMob())
                    .flatMap(mob -> mob.immunityTable().checkImmunity(ctx))
                    .orElse(null);
        });
    }

    private static boolean isBasicAttack(DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
    }

    /**
     * Executes the damage pipeline on the given context.
     *
     * @param context the damage execution parameters
     * @return result of execution including final damage or cancellation reason
     */
    @Override
    public DamageResult execute(DamageContext context) {
        Objects.requireNonNull(context, "Damage context must not be null");

        // 1. Pre-execution checks
        for (Function<DamageContext, String> check : preChecks) {
            String rejectReason = check.apply(context);
            if (rejectReason != null) {
                context.cancel(rejectReason);
                return DamageResult.cancelled(context, rejectReason);
            }
        }

        // 2. Modifiers
        for (Consumer<DamageContext> modifier : modifiers) {
            modifier.accept(context);
            if (context.isCancelled()) {
                return DamageResult.cancelled(context, context.cancelReason().orElse("Cancelled by modifier"));
            }
        }

        if (context.finalDamage() <= 0.0) {
            context.cancel("Damage reduced to zero or below");
            return DamageResult.cancelled(context, "Damage reduced to zero or below");
        }

        // 3. Immunity check
        for (Function<DamageContext, String> checker : immunityCheckers) {
            String immuneReason = checker.apply(context);
            if (immuneReason != null) {
                context.cancel(immuneReason);
                return DamageResult.cancelled(context, immuneReason);
            }
        }

        // 4. Execution with recursion lock
        double appliedDamage = context.finalDamage();
        LivingEntity victim = context.victim();
        ActiveMob attackerMob = asActive(context.attackerMob()).orElse(null);

        if (attackerMob != null) {
            attackerMob.setUsingDamageSkill(true);
        }

        try {
            if (context.attacker() != null) {
                victim.damage(appliedDamage, context.attacker());
            } else {
                victim.damage(appliedDamage);
            }
        } finally {
            if (attackerMob != null) {
                attackerMob.setUsingDamageSkill(false);
            }
        }

        // 5. Post-damage notification
        for (BiConsumer<DamageContext, Double> handler : postHandlers) {
            try {
                handler.accept(context, appliedDamage);
            } catch (Throwable ignored) {
                // Safeguard against buggy third-party post handlers crashing the pipeline
            }
        }

        return DamageResult.success(context, appliedDamage);
    }

    @Override
    public DamagePipeline registerPreCheck(Function<DamageContext, String> check) {
        if (check != null) {
            preChecks.add(check);
        }
        return this;
    }

    @Override
    public DamagePipeline registerModifier(Consumer<DamageContext> modifier) {
        if (modifier != null) {
            modifiers.add(modifier);
        }
        return this;
    }

    @Override
    public DamagePipeline registerImmunityChecker(Function<DamageContext, String> immunityChecker) {
        if (immunityChecker != null) {
            immunityCheckers.add(immunityChecker);
        }
        return this;
    }

    public DamagePipeline registerPostHandler(BiConsumer<DamageContext, Double> handler) {
        if (handler != null) {
            postHandlers.add(handler);
        }
        return this;
    }
}
