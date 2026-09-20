package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired after a mob finishes casting a skill, indicating whether execution succeeded.
 */
public final class LunarSkillPostCastEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob caster;
    private final SkillDefinition skill;
    private final LivingEntity target;
    private final boolean success;

    public LunarSkillPostCastEvent(Mob caster, SkillDefinition skill, LivingEntity target, boolean success) {
        this.caster = Objects.requireNonNull(caster, "Caster must not be null");
        this.skill = Objects.requireNonNull(skill, "Skill must not be null");
        this.target = target;
        this.success = success;
    }

    /**
     * Returns the mob that executed the skill.
     *
     * @return the casting mob
     */
    public Mob caster() {
        return caster;
    }

    /**
     * Returns the skill definition that was cast.
     *
     * @return the skill definition
     */
    public SkillDefinition skill() {
        return skill;
    }

    /**
     * Returns the primary target entity, if one was targeted.
     *
     * @return optional containing the target entity
     */
    public Optional<LivingEntity> target() {
        return Optional.ofNullable(target);
    }

    /**
     * Returns whether the skill executed successfully without condition failure or cancellation.
     *
     * @return true if execution completed successfully
     */
    public boolean success() {
        return success;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
