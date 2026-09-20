package vn.haohan.lunar.api.mob.persistence;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionRegistry;

import java.util.Optional;

/**
 * Persists runtime combat and state variables into the entity's PersistentDataContainer (PDC).
 * Enables preserving mob health, stance, and phase across server restarts and chunk unloads.
 */
public final class MobPersistenceManager {

    private static final String NAMESPACE = "haohanlunar";
    public static final NamespacedKey KEY_SAVED_HEALTH = new NamespacedKey(NAMESPACE, "saved_health");
    public static final NamespacedKey KEY_SAVED_STANCE = new NamespacedKey(NAMESPACE, "saved_stance");
    public static final NamespacedKey KEY_PERSISTENT = new NamespacedKey(NAMESPACE, "is_persistent");

    private MobPersistenceManager() {
    }

    /**
     * Saves runtime state of ActiveLunarMob to PDC before chunk unload or server shutdown.
     */
    public static void saveState(ActiveLunarMob mob) {
        if (mob == null || mob.entity() == null || !mob.entity().isValid()) {
            return;
        }

        LivingEntity entity = mob.entity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        pdc.set(KEY_SAVED_HEALTH, PersistentDataType.DOUBLE, entity.getHealth());
        pdc.set(KEY_SAVED_STANCE, PersistentDataType.STRING, mob.stance());
        pdc.set(KEY_PERSISTENT, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Restores saved runtime state (health, stance) onto an ActiveLunarMob after chunk load.
     */
    public static void restoreState(ActiveLunarMob mob) {
        if (mob == null || mob.entity() == null) {
            return;
        }

        LivingEntity entity = mob.entity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        Double savedHealth = pdc.get(KEY_SAVED_HEALTH, PersistentDataType.DOUBLE);
        if (savedHealth != null && savedHealth > 0.0) {
            double targetHealth = savedHealth;
            try {
                var attr = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attr != null) {
                    targetHealth = Math.min(savedHealth, attr.getValue());
                }
            } catch (Throwable ignored) {}
            try {
                entity.setHealth(targetHealth);
            } catch (Throwable ignored) {}
        }

        String savedStance = pdc.get(KEY_SAVED_STANCE, PersistentDataType.STRING);
        if (savedStance != null && !savedStance.isBlank()) {
            mob.setStance(savedStance);
        }
    }

    /**
     * Reconstructs an ActiveLunarMob from an existing living entity using its PDC identity.
     */
    public static ActiveLunarMob restoreState(LivingEntity entity, MobDefinitionRegistry registry) {
        if (entity == null || registry == null) return null;
        Optional<LunarMobIdentity> idOpt = LunarMobIdentity.read(entity);
        if (idOpt.isEmpty()) return null;
        LunarMobIdentity identity = idOpt.get();
        Optional<MobDefinition> defOpt = registry.get(identity.mobId());
        if (defOpt.isEmpty()) return null;
        ActiveLunarMob mob = new ActiveLunarMob(entity, defOpt.get(), identity);
        restoreState(mob);
        return mob;
    }

    /**
     * Checks if this entity was marked persistent in PDC.
     */
    public static boolean isMarkedPersistent(LivingEntity entity) {
        if (entity == null) return false;
        Byte b = entity.getPersistentDataContainer().get(KEY_PERSISTENT, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }
}
