package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

import java.util.Objects;

/**
 * Triggers a reactive action whenever the aura host attacks a target entity.
 */
public final class OnAttackAuraComponent implements AuraComponent {

    @FunctionalInterface
    public interface AttackHandler {
        void onAttack(ActiveAura aura, LivingEntity target, double damage);
    }

    private final AttackHandler handler;

    public OnAttackAuraComponent(AttackHandler handler) {
        this.handler = Objects.requireNonNull(handler, "Attack handler must not be null");
    }

    @Override
    public void onHit(ActiveAura aura, LivingEntity target, double damage) {
        handler.onAttack(aura, target, damage);
    }
}
