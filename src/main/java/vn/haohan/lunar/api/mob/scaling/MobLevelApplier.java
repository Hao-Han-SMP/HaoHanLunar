package vn.haohan.lunar.api.mob.scaling;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles reading/writing level from PDC and applying scaled attributes to LivingEntities.
 */
public final class MobLevelApplier {

    public static final NamespacedKey KEY_LEVEL = new NamespacedKey("haohan", "mob_level");

    private MobLevelApplier() {
    }

    /**
     * Reads level from entity PDC, or defaults to 1.
     */
    public static int getLevel(LivingEntity entity) {
        if (entity == null) return 1;
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Integer lvl = pdc.get(KEY_LEVEL, PersistentDataType.INTEGER);
        return lvl != null && lvl >= 1 ? lvl : 1;
    }

    /**
     * Stores level into entity PDC.
     */
    public static void setLevel(LivingEntity entity, int level) {
        if (entity == null) return;
        int clamped = Math.max(1, level);
        entity.getPersistentDataContainer().set(KEY_LEVEL, PersistentDataType.INTEGER, clamped);
    }

    /**
     * Applies level scaling attributes to the entity safely.
     */
    public static void applyScaling(LivingEntity entity, LevelScalingDefinition scaling, int level) {
        if (entity == null || scaling == null) return;
        setLevel(entity, level);

        try {
            AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double base = maxHealthAttr.getBaseValue();
                double scaled = scaling.calculateHealth(base, level);
                maxHealthAttr.setBaseValue(scaled);
                entity.setHealth(scaled);
            }
        } catch (Throwable ignored) {}

        try {
            AttributeInstance attackDmgAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attackDmgAttr != null) {
                double base = attackDmgAttr.getBaseValue();
                double scaled = scaling.calculateDamage(base, level);
                attackDmgAttr.setBaseValue(scaled);
            }
        } catch (Throwable ignored) {}

        try {
            AttributeInstance armorAttr = entity.getAttribute(Attribute.ARMOR);
            if (armorAttr != null) {
                double base = armorAttr.getBaseValue();
                double scaled = scaling.calculateArmor(base, level);
                armorAttr.setBaseValue(scaled);
            }
        } catch (Throwable ignored) {}
    }
}
