package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import vn.haohan.lunar.api.mob.Mob;
import vn.haohan.lunar.api.combat.skill.SkillDefinition;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired after an Mob finishes casting a skill.
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

    public Mob caster() {
        return caster;
    }

    public SkillDefinition skill() {
        return skill;
    }

    public Optional<LivingEntity> target() {
        return Optional.ofNullable(target);
    }

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
