package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.IAuraComponent;

import java.util.Objects;

/**
 * Triggers a reactive action whenever the aura host attacks a target entity.
 */
public final class OnAttackAuraComponent implements IAuraComponent {

    @FunctionalInterface
    public interface IAttackHandler {
        void onAttack(ActiveAura aura, LivingEntity target, double damage);
    }

    private final IAttackHandler handler;

    public OnAttackAuraComponent(IAttackHandler handler) {
        this.handler = Objects.requireNonNull(handler, "Attack handler must not be null");
    }

    @Override
    public void onHit(ActiveAura aura, LivingEntity target, double damage) {
        handler.onAttack(aura, target, damage);
    }
}
