package vn.haohan.lunar.api.mob;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.ThreatTable;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.util.UUID;

/**
 * Public interface representing an actively managed custom Lunar mob in the world.
 */
public interface Mob {

    /**
     * @return The Minecraft Entity UUID.
     */
    UUID entityId();

    /**
     * Standard Bukkit UniqueId alias.
     */
    default UUID getUniqueId() {
        return entityId();
    }

    /**
     * @return The underlying Bukkit LivingEntity instance.
     */
    LivingEntity entity();

    /**
     * Standard Bukkit LivingEntity alias.
     */
    default LivingEntity getEntity() {
        return entity();
    }

    /**
     * @return The configured mob type identifier (e.g. "void_crawler").
     */
    String mobId();

    /**
     * @return The mob's current level.
     */
    int level();

    /**
     * Sets the mob's level and reapplies dynamic attribute scaling.
     */
    void setLevel(int level);

    /**
     * @return Current behavioral stance (e.g. "default", "enraged").
     */
    String stance();

    /**
     * Updates the behavioral stance.
     */
    void setStance(String stance);

    /**
     * @return The threat table tracking player hostility.
     */
    ThreatTable threatTable();

    /**
     * @return True if the entity is valid, alive, and in a loaded world chunk.
     */
    boolean isValid();

    /**
     * Triggers skills configured for the given trigger event.
     */
    void trigger(SkillTrigger trigger);

    /**
     * Manually triggers an execution of the given skill chain by ID.
     */
    void castSkill(String skillName);

    /**
     * Safely despawns and cleans up this mob instance.
     */
    void despawn();
}
