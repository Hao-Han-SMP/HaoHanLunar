package vn.haohan.lunar.api.system.combat;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import vn.haohan.lunar.api.manager.CombatManager;
import vn.haohan.lunar.api.system.mob.Mob;
import vn.haohan.lunar.api.spawner.cluster.PackAggroCoordinator;
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
    private static final int MAX_CALL_DEPTH = 6;
    private static final ThreadLocal<Integer> CALL_DEPTH = ThreadLocal.withInitial(() -> 0);

    private PackAggroCoordinator packAggroCoordinator = new PackAggroCoordinator();
    private vn.haohan.lunar.core.subsystem.mob.LunarMobManager mobManager;

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

        // Built-in modifier: victim temporary or native invulnerability
        registerModifier(ctx -> {
            asActive(ctx.victimMob()).ifPresent(victim -> {
                long currentTick;
                try {
                    currentTick = org.bukkit.Bukkit.getCurrentTick();
                } catch (Throwable t) {
                    currentTick = System.currentTimeMillis() / 50L;
                }
                if (victim.isInvulnerable(currentTick)) {
                    ctx.setFinalDamage(0.0);
                    ctx.setCancelled(true);
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

        // Built-in modifier: boss maxDamagePerHit and rolling damageCap capping
        registerModifier(ctx -> {
            asActive(ctx.victimMob()).ifPresent(victim -> {
                double cap = victim.options().maxDamagePerHit();
                if (cap > 0.0 && ctx.finalDamage() > cap) {
                    ctx.setFinalDamage(cap);
                }
                double dpsCap = victim.options().damageCap();
                if (dpsCap > 0.0) {
                    long currentTick;
                    try {
                        currentTick = org.bukkit.Bukkit.getCurrentTick();
                    } catch (Throwable t) {
                        currentTick = System.currentTimeMillis() / 50L;
                    }
                    double allowed = victim.applyDamageCap(ctx.finalDamage(), dpsCap, currentTick);
                    ctx.setFinalDamage(allowed);
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
                            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
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

        // Built-in post handler: pack aggro coordination
        registerPostHandler((ctx, appliedDamage) -> {
            if (appliedDamage <= 0.0) return;
            asActive(ctx.victimMob()).ifPresent(victim -> {
                if (ctx.attacker() instanceof LivingEntity attacker && mobManager != null && packAggroCoordinator != null) {
                    packAggroCoordinator.broadcastAggro(victim, attacker, mobManager.snapshot());
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

    public void setMobManager(vn.haohan.lunar.core.subsystem.mob.LunarMobManager mobManager) {
        this.mobManager = mobManager;
    }

    private static Optional<ActiveMob> asActive(Optional<? extends Mob> mobOpt) {
        return mobOpt.filter(ActiveMob.class::isInstance).map(ActiveMob.class::cast);
    }

    public void setPackAggroCoordinator(PackAggroCoordinator coordinator) {
        this.packAggroCoordinator = coordinator != null ? coordinator : new PackAggroCoordinator();
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

        int depth = CALL_DEPTH.get();
        if (depth >= MAX_CALL_DEPTH) {
            String rejectReason = "Recursion call depth limit exceeded (" + MAX_CALL_DEPTH + ")";
            context.cancel(rejectReason);
            return DamageResult.cancelled(context, rejectReason);
        }
        CALL_DEPTH.set(depth + 1);
        try {
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
        } finally {
            CALL_DEPTH.set(depth);
        }
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
