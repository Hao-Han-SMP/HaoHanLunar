package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.entity.Entity;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

import java.util.Objects;

/**
 * Triggers a reactive action whenever the aura host takes damage from an attacker.
 */
public final class OnDamagedAuraComponent implements AuraComponent {

    @FunctionalInterface
    public interface DamagedHandler {
        void onDamaged(ActiveAura aura, Entity attacker, double damage);
    }

    private final DamagedHandler handler;

    public OnDamagedAuraComponent(DamagedHandler handler) {
        this.handler = Objects.requireNonNull(handler, "Damaged handler must not be null");
    }

    @Override
    public void onDamaged(ActiveAura aura, Entity attacker, double damage) {
        handler.onDamaged(aura, attacker, damage);
    }
}
