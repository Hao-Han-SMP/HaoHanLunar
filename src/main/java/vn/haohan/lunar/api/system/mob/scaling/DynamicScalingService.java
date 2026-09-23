package vn.haohan.lunar.api.system.mob.scaling;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Objects;

/**
 * Service managing dynamic boss difficulty scaling based on nearby player count and proximity.
 */
public final class DynamicScalingService {

    public int countValidPlayers(ActiveMob mob, double radius) {
        if (mob == null || mob.entity() == null || mob.entity().isDead()) {
            return 0;
        }
        Location loc = mob.entity().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return 0;
        }

        double rSq = radius * radius;
        int count = 0;
        for (Player player : loc.getWorld().getPlayers()) {
            if (!isValidCombatant(player)) {
                continue;
            }
            if (player.getWorld().equals(loc.getWorld()) && player.getLocation().distanceSquared(loc) <= rSq) {
                count++;
            }
        }
        return count;
    }

    public boolean isValidCombatant(Player player) {
        if (player == null || !player.isValid() || player.isDead()) {
            return false;
        }
        GameMode gm = player.getGameMode();
        return gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR;
    }

    public DynamicScalingResult computeScaling(ActiveMob mob, int rawPlayerCount) {
        if (mob == null) {
            return DynamicScalingResult.UNCHANGED;
        }

        var optDef = mob.definition().dynamicScaling();
        if (optDef.isEmpty() || !optDef.get().enabled()) {
            return DynamicScalingResult.UNCHANGED;
        }

        DynamicScalingDefinition def = optDef.get();
        int effectivePlayers = Math.min(rawPlayerCount, def.maxPlayers());
        int extraPlayers = Math.max(0, effectivePlayers - def.baselinePlayers());

        double healthMultiplier = 1.0 + (extraPlayers * def.healthPerPlayer());
        double damageMultiplier = 1.0 + (extraPlayers * def.damagePerPlayer());
        double cooldownReduction = Math.min(0.60, extraPlayers * def.cooldownReductionPerPlayer());

        return new DynamicScalingResult(rawPlayerCount, effectivePlayers, healthMultiplier, damageMultiplier, cooldownReduction);
    }

    public DynamicScalingResult applyScaling(ActiveMob mob, int playerCount) {
        Objects.requireNonNull(mob, "Mob must not be null");
        LivingEntity entity = mob.entity();
        if (entity == null || entity.isDead()) {
            return DynamicScalingResult.UNCHANGED;
        }

        DynamicScalingResult result = computeScaling(mob, playerCount);

        // Cache baseline stats if first time
        if (mob.baseMaxHealth() <= 0) {
            double initialMax = entity.getMaxHealth();
            mob.setBaseMaxHealth(initialMax > 0 ? initialMax : 100.0);
        }
        if (mob.baseDamage() <= 0) {
            double baseDmg = 5.0;
            try {
                AttributeInstance dmgAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmgAttr != null) {
                    baseDmg = dmgAttr.getBaseValue();
                }
            } catch (Throwable ignored) {
            }
            mob.setBaseDamage(baseDmg);
        }

        double curMaxHealth = entity.getMaxHealth();
        double curHealth = entity.getHealth();
        double healthPct = curMaxHealth > 0 ? (curHealth / curMaxHealth) : 1.0;
        healthPct = Math.max(0.0, Math.min(1.0, healthPct));

        double newMaxHealth = Math.max(1.0, mob.baseMaxHealth() * result.healthMultiplier());

        try {
            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(newMaxHealth);
            }
        } catch (Throwable ignored) {
        }
        try {
            entity.setMaxHealth(newMaxHealth);
        } catch (Throwable ignored) {
        }

        try {
            AttributeInstance dmgAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(mob.baseDamage() * result.damageMultiplier());
            }
        } catch (Throwable ignored) {
        }

        try {
            entity.setHealth(Math.max(1.0, Math.min(newMaxHealth, newMaxHealth * healthPct)));
        } catch (Throwable ignored) {
        }

        mob.setCurrentHealthMultiplier(result.healthMultiplier());
        mob.setCurrentDamageMultiplier(result.damageMultiplier());
        mob.setDynamicCooldownReduction(result.cooldownReduction());
        mob.setLastTrackedPlayerCount(playerCount);

        return result;
    }

    public DynamicScalingResult updateMobScaling(ActiveMob mob) {
        if (mob == null || mob.definition().dynamicScaling().isEmpty() || !mob.definition().dynamicScaling().get().enabled()) {
            return DynamicScalingResult.UNCHANGED;
        }
        double radius = mob.definition().dynamicScaling().get().radius();
        int count = countValidPlayers(mob, radius);
        return applyScaling(mob, count);
    }
}
