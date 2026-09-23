package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.system.mob.Mob;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired before a mob executes a skill.
 * Cancelling this event prevents the skill from casting.
 * Handlers may inspect and modify skill power or reassign the target entity.
 */
public final class SkillPreCastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob caster;
    private final SkillDefinition skill;
    private LivingEntity target;
    private double power;
    private boolean cancelled;

    public SkillPreCastEvent(Mob caster, SkillDefinition skill, LivingEntity target, double power) {
        this.caster = Objects.requireNonNull(caster, "Caster must not be null");
        this.skill = Objects.requireNonNull(skill, "Skill must not be null");
        this.target = target;
        this.power = Math.max(0.0, power);
    }

    /**
     * Returns the mob casting the skill.
     *
     * @return the casting mob
     */
    public Mob caster() {
        return caster;
    }

    /**
     * Returns the skill definition being executed.
     *
     * @return the skill definition
     */
    public SkillDefinition skill() {
        return skill;
    }

    /**
     * Returns the intended target entity, or empty if self/area cast.
     *
     * @return optional containing the target entity
     */
    public Optional<LivingEntity> target() {
        return Optional.ofNullable(target);
    }

    /**
     * Reassigns the target entity for this skill execution.
     *
     * @param target the new target entity
     */
    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    /**
     * Returns the power multiplier applied to the skill.
     *
     * @return skill power multiplier
     */
    public double power() {
        return power;
    }

    /**
     * Sets the power multiplier for this skill execution.
     *
     * @param power new power multiplier (clamped to non-negative)
     */
    public void setPower(double power) {
        this.power = Math.max(0.0, power);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
