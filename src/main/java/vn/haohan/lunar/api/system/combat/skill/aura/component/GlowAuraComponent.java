package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Highlights living entities within the aura radius with the Minecraft glowing outline effect.
 */
public final class GlowAuraComponent implements AuraComponent {

    private final double radius;
    private final Set<LivingEntity> currentlyGlowing = new HashSet<>();

    public GlowAuraComponent(double radius) {
        this.radius = Math.max(0.5, radius);
    }

    public double radius() {
        return radius;
    }

    @Override
    public void onTick(ActiveAura aura, long currentTick) {
        Location center = aura.attachment().location().orElse(null);
        if (center == null || center.getWorld() == null) {
            clearGlow();
            return;
        }

        Set<LivingEntity> inRange = new HashSet<>();
        UUID ownerId = aura.ownerId();

        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (nearby instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                if (ownerId != null && living.getUniqueId().equals(ownerId)) {
                    continue;
                }
                inRange.add(living);
                try {
                    living.setGlowing(true);
                } catch (Exception ignored) {}
            }
        }

        // Entities that exited radius lose glow
        Iterator<LivingEntity> it = currentlyGlowing.iterator();
        while (it.hasNext()) {
            LivingEntity prev = it.next();
            if (!inRange.contains(prev)) {
                try {
                    if (prev.isValid()) prev.setGlowing(false);
                } catch (Exception ignored) {}
                it.remove();
            }
        }
        currentlyGlowing.addAll(inRange);
    }

    @Override
    public void onExpire(ActiveAura aura) {
        clearGlow();
    }

    private void clearGlow() {
        for (LivingEntity entity : currentlyGlowing) {
            try {
                if (entity.isValid()) entity.setGlowing(false);
            } catch (Exception ignored) {}
        }
        currentlyGlowing.clear();
    }
}
