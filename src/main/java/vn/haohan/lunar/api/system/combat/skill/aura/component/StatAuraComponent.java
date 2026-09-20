package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically applies and refreshes potion buffs/debuffs to living entities standing within the aura radius.
 */
public final class StatAuraComponent implements AuraComponent {

    private final double radius;
    private final PotionEffectType effectType;
    private final int amplifier;
    private final boolean affectOwner;
    private final Set<LivingEntity> affected = new HashSet<>();

    public StatAuraComponent(double radius, PotionEffectType effectType, int amplifier, boolean affectOwner) {
        this.radius = Math.max(0.5, radius);
        this.effectType = Objects.requireNonNull(effectType, "EffectType must not be null");
        this.amplifier = Math.max(0, Math.min(amplifier, 255));
        this.affectOwner = affectOwner;
    }

    public double radius() {
        return radius;
    }

    public PotionEffectType effectType() {
        return effectType;
    }

    public int amplifier() {
        return amplifier;
    }

    @Override
    public void onTick(ActiveAura aura, long currentTick) {
        Location center = aura.attachment().location().orElse(null);
        if (center == null || center.getWorld() == null) {
            clearEffects();
            return;
        }

        Set<LivingEntity> currentInRange = new HashSet<>();
        UUID ownerId = aura.ownerId();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                if (!affectOwner && ownerId != null && living.getUniqueId().equals(ownerId)) {
                    continue;
                }
                currentInRange.add(living);
                try {
                    living.addPotionEffect(new PotionEffect(effectType, 40, amplifier, false, false));
                } catch (Exception ignored) {}
            }
        }

        Iterator<LivingEntity> it = affected.iterator();
        while (it.hasNext()) {
            LivingEntity prev = it.next();
            if (!currentInRange.contains(prev)) {
                try {
                    if (prev.isValid()) prev.removePotionEffect(effectType);
                } catch (Exception ignored) {}
                it.remove();
            }
        }
        affected.addAll(currentInRange);
    }

    @Override
    public void onExpire(ActiveAura aura) {
        clearEffects();
    }

    private void clearEffects() {
        for (LivingEntity living : affected) {
            try {
                if (living.isValid()) living.removePotionEffect(effectType);
            } catch (Exception ignored) {}
        }
        affected.clear();
    }
}
