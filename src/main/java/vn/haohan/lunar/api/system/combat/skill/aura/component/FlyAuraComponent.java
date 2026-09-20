package vn.haohan.lunar.api.system.combat.skill.aura.component;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.system.combat.skill.aura.ActiveAura;
import vn.haohan.lunar.api.system.combat.skill.aura.AuraComponent;

/**
 * Grants temporary levitation or flight suspension to the aura host entity.
 */
public final class FlyAuraComponent implements AuraComponent {

    private final double upwardForce;
    private final boolean applyLevitation;

    public FlyAuraComponent(double upwardForce, boolean applyLevitation) {
        this.upwardForce = Math.max(-1.0, Math.min(upwardForce, 2.0));
        this.applyLevitation = applyLevitation;
    }

    public double upwardForce() {
        return upwardForce;
    }

    public boolean applyLevitation() {
        return applyLevitation;
    }

    @Override
    public void onStart(ActiveAura aura) {
        aura.attachment().entity().ifPresent(this::enableFlight);
    }

    @Override
    public void onTick(ActiveAura aura, long currentTick) {
        aura.attachment().entity().ifPresent(entity -> {
            if (entity.isValid() && !entity.isDead()) {
                if (upwardForce != 0.0) {
                    Vector current = entity.getVelocity();
                    entity.setVelocity(new Vector(current.getX(), upwardForce, current.getZ()));
                }
            }
        });
    }

    @Override
    public void onExpire(ActiveAura aura) {
        aura.attachment().entity().ifPresent(this::disableFlight);
    }

    private void enableFlight(LivingEntity entity) {
        if (entity instanceof Player player) {
            try {
                player.setAllowFlight(true);
                player.setFlying(true);
            } catch (Exception ignored) {}
        }
        if (applyLevitation) {
            try {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1, false, false));
            } catch (Exception ignored) {}
        }
    }

    private void disableFlight(LivingEntity entity) {
        if (entity instanceof Player player) {
            try {
                if (player.getGameMode().name().equals("SURVIVAL") || player.getGameMode().name().equals("ADVENTURE")) {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            } catch (Exception ignored) {}
        }
        if (applyLevitation) {
            try {
                entity.removePotionEffect(PotionEffectType.LEVITATION);
            } catch (Exception ignored) {}
        }
    }
}
