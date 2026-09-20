package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically induces panic/fear in nearby living entities, disorienting their movement.
 */
public final class FearAuraComponent implements AuraComponent {

    private final double radius;
    private final double strength;

    public FearAuraComponent(double radius, double strength) {
        this.radius = Math.max(0.5, radius);
        this.strength = Math.max(0.1, Math.min(strength, 3.0));
    }

    public double radius() {
        return radius;
    }

    public double strength() {
        return strength;
    }

    @Override
    public void onTick(ActiveAura aura, long currentTick) {
        Location center = aura.attachment().location().orElse(null);
        if (center == null || center.getWorld() == null) return;

        UUID ownerId = aura.ownerId();
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || !target.isValid() || target.isDead()) {
                continue;
            }
            if (ownerId != null && target.getUniqueId().equals(ownerId)) {
                continue;
            }
            // Apply randomized panic directional impulse
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            Vector panicVelocity = new Vector(Math.cos(angle) * strength, 0.15, Math.sin(angle) * strength);
            target.setVelocity(panicVelocity);
        }
    }
}
