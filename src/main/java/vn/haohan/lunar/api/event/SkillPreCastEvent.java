package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;
import vn.haohan.lunar.api.combat.skill.SkillDefinition;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired before an Mob executes a skill.
 * Cancellable: cancelling stops the skill execution.
 * Allows modifying skill power and changing or overriding the skill target.
 */
public final class LunarSkillPreCastEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob caster;
    private final SkillDefinition skill;
    private LivingEntity target;
    private double power;
    private boolean cancelled;

    public LunarSkillPreCastEvent(Mob caster, SkillDefinition skill, LivingEntity target, double power) {
        this.caster = Objects.requireNonNull(caster, "Caster must not be null");
        this.skill = Objects.requireNonNull(skill, "Skill must not be null");
        this.target = target;
        this.power = Math.max(0.0, power);
    }

    public Mob caster() {
        return caster;
    }

    public SkillDefinition skill() {
        return skill;
    }

    public Optional<LivingEntity> target() {
        return Optional.ofNullable(target);
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public double power() {
        return power;
    }

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
