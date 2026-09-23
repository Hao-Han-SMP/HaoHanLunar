package vn.haohan.lunar.api.system.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.haohan.lunar.api.system.mob.Mob;

import java.util.Objects;
import java.util.Optional;

/**
 * Contextual data container for an in-flight damage execution.
 * Tracks attacker, victim, modifiers, type, and cancellation state through the pipeline.
 */
public final class DamageContext {

    private final Entity attacker;
    private final Mob attackerMob;
    private final LivingEntity victim;
    private final Mob victimMob;
    private final EntityDamageEvent.DamageCause cause;
    private final DamageType damageType;
    private final double baseDamage;
    private double finalDamage;
    private final String skillId;

    private boolean critical;
    private boolean ignoreArmor;
    private boolean ignoreAbsorption;
    private boolean cancelled;
    private String cancelReason;

    public DamageContext(Builder builder) {
        this.victim = Objects.requireNonNull(builder.victim, "Victim must not be null");
        this.attacker = builder.attacker;
        this.attackerMob = builder.attackerMob;
        this.victimMob = builder.victimMob;
        this.cause = builder.cause != null ? builder.cause : EntityDamageEvent.DamageCause.CUSTOM;
        this.damageType = builder.damageType != null ? builder.damageType : DamageType.PHYSICAL;
        if (builder.baseDamage < 0) {
            throw new IllegalArgumentException("Base damage cannot be negative: " + builder.baseDamage);
        }
        this.baseDamage = builder.baseDamage;
        this.finalDamage = builder.finalDamage >= 0 ? builder.finalDamage : builder.baseDamage;
        this.skillId = builder.skillId != null ? builder.skillId.trim() : null;
        this.critical = builder.critical;
        this.ignoreArmor = builder.ignoreArmor || this.damageType == DamageType.TRUE;
        this.ignoreAbsorption = builder.ignoreAbsorption || this.damageType == DamageType.TRUE;
        this.cancelled = builder.cancelled;
        this.cancelReason = builder.cancelReason;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Entity attacker() {
        return attacker;
    }

    public Optional<Mob> attackerMob() {
        return Optional.ofNullable(attackerMob);
    }

    public LivingEntity victim() {
        return victim;
    }

    public Optional<Mob> victimMob() {
        return Optional.ofNullable(victimMob);
    }

    public EntityDamageEvent.DamageCause cause() {
        return cause;
    }

    public DamageType damageType() {
        return damageType;
    }

    public double baseDamage() {
        return baseDamage;
    }

    public double finalDamage() {
        return finalDamage;
    }

    public void setFinalDamage(double finalDamage) {
        this.finalDamage = Math.max(0.0, finalDamage);
    }

    public void multiplyDamage(double multiplier) {
        if (multiplier <= 0.0) {
            this.finalDamage = 0.0;
        } else {
            this.finalDamage *= multiplier;
        }
    }

    public Optional<String> skillId() {
        return Optional.ofNullable(skillId);
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public boolean isIgnoreArmor() {
        return ignoreArmor;
    }

    public void setIgnoreArmor(boolean ignoreArmor) {
        this.ignoreArmor = ignoreArmor;
    }

    public boolean isIgnoreAbsorption() {
        return ignoreAbsorption;
    }

    public void setIgnoreAbsorption(boolean ignoreAbsorption) {
        this.ignoreAbsorption = ignoreAbsorption;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel(String reason) {
        this.cancelled = true;
        this.cancelReason = reason;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
        if (!cancelled) {
            this.cancelReason = null;
        }
    }

    public Optional<String> cancelReason() {
        return Optional.ofNullable(cancelReason);
    }

    public static final class Builder {
        private Entity attacker;
        private Mob attackerMob;
        private LivingEntity victim;
        private Mob victimMob;
        private EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;
        private DamageType damageType = DamageType.PHYSICAL;
        private double baseDamage;
        private double finalDamage = -1.0;
        private String skillId;
        private boolean critical;
        private boolean ignoreArmor;
        private boolean ignoreAbsorption;
        private boolean cancelled;
        private String cancelReason;

        public Builder attacker(Entity attacker) {
            this.attacker = attacker;
            return this;
        }

        public Builder attackerMob(Mob attackerMob) {
            this.attackerMob = attackerMob;
            if (attackerMob != null && this.attacker == null) {
                this.attacker = attackerMob.entity();
            }
            return this;
        }

        public Builder victim(LivingEntity victim) {
            this.victim = victim;
            return this;
        }

        public Builder victimMob(Mob victimMob) {
            this.victimMob = victimMob;
            if (victimMob != null && this.victim == null) {
                this.victim = victimMob.entity();
            }
            return this;
        }

        public Builder cause(EntityDamageEvent.DamageCause cause) {
            this.cause = cause;
            return this;
        }

        public Builder damageType(DamageType damageType) {
            this.damageType = damageType;
            return this;
        }

        public Builder baseDamage(double baseDamage) {
            this.baseDamage = baseDamage;
            return this;
        }

        public Builder finalDamage(double finalDamage) {
            this.finalDamage = finalDamage;
            return this;
        }

        public Builder skillId(String skillId) {
            this.skillId = skillId;
            return this;
        }

        public Builder critical(boolean critical) {
            this.critical = critical;
            return this;
        }

        public Builder ignoreArmor(boolean ignoreArmor) {
            this.ignoreArmor = ignoreArmor;
            return this;
        }

        public Builder ignoreAbsorption(boolean ignoreAbsorption) {
            this.ignoreAbsorption = ignoreAbsorption;
            return this;
        }

        public Builder cancelled(boolean cancelled) {
            this.cancelled = cancelled;
            return this;
        }

        public Builder cancelReason(String cancelReason) {
            this.cancelReason = cancelReason;
            return this;
        }

        public DamageContext build() {
            return new DamageContext(this);
        }
    }
}
