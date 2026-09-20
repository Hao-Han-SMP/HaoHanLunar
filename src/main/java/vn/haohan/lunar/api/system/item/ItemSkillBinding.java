package vn.haohan.lunar.core.system.item;

import java.util.Objects;

/**
 * Declares an active or passive skill bound to an item.
 */
public record ItemSkillBinding(
        String skillId,
        ItemSkillTrigger trigger,
        long cooldownTicks,
        String condition
) {
    public ItemSkillBinding {
        Objects.requireNonNull(skillId, "Skill ID must not be null");
        Objects.requireNonNull(trigger, "Trigger must not be null");
        cooldownTicks = Math.max(0, cooldownTicks);
    }

    public ItemSkillBinding(String skillId, ItemSkillTrigger trigger, long cooldownTicks) {
        this(skillId, trigger, cooldownTicks, null);
    }
}
