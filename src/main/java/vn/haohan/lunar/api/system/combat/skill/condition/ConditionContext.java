package vn.haohan.lunar.api.system.combat.skill.condition;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Read-only inputs available to a condition evaluation. */
public record ConditionContext(LivingEntity caster, Entity target, String phase,
                               CooldownRegistry cooldowns, Map<String, Object> variables,
                               long currentTick) {

    public ConditionContext {
        caster = Objects.requireNonNull(caster, "Caster must not be null");
        cooldowns = Objects.requireNonNull(cooldowns, "Cooldown registry must not be null");
        variables = Map.copyOf(Objects.requireNonNull(variables, "Variables must not be null"));
    }

    public Optional<Entity> targetOptional() { return Optional.ofNullable(target); }
    public UUID casterId() { return caster.getUniqueId(); }
    public LivingEntity subjectLivingEntity() {
        return target instanceof LivingEntity living ? living : caster;
    }
}
