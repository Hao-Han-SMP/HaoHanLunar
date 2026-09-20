package vn.haohan.lunar.api.mob;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.combat.ThreatTable;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.util.UUID;

/**
 * Represents an active custom mob instance in the world.
 */
public interface Mob {

    /**
     * Returns the Minecraft entity unique identifier.
     *
     * @return the entity UUID
     */
    UUID entityId();

    /**
     * Alias for {@link #entityId()} matching Bukkit conventions.
     *
     * @return the entity UUID
     */
    default UUID getUniqueId() {
        return entityId();
    }

    /**
     * Returns the underlying Bukkit living entity instance.
     *
     * @return the living entity
     */
    LivingEntity entity();

    /**
     * Alias for {@link #entity()} matching Bukkit conventions.
     *
     * @return the living entity
     */
    default LivingEntity getEntity() {
        return entity();
    }

    /**
     * Returns the mob type identifier (e.g. {@code "void_crawler"}).
     *
     * @return the mob type ID
     */
    String mobId();

    /**
     * Returns the mob's current combat level.
     *
     * @return the current level
     */
    int level();

    /**
     * Sets the mob's level and reapplies attribute scaling formulas.
     *
     * @param level the new level to assign
     */
    void setLevel(int level);

    /**
     * Returns the current behavioral stance identifier (e.g. {@code "default"}, {@code "enraged"}).
     *
     * @return the current stance string
     */
    String stance();

    /**
     * Updates the mob's behavioral stance.
     *
     * @param stance the new stance identifier
     */
    void setStance(String stance);

    /**
     * Returns the threat table tracking entity hostility and targeting scores.
     *
     * @return the threat table
     */
    ThreatTable threatTable();

    /**
     * Checks whether the underlying entity is non-null, valid, alive, and within a loaded chunk.
     *
     * @return true if the mob is currently active and alive
     */
    boolean isValid();

    /**
     * Triggers all skills configured for the specified combat or lifecycle event.
     *
     * @param trigger the trigger event
     */
    void trigger(SkillTrigger trigger);

    /**
     * Executes the skill identified by name immediately on this mob.
     *
     * @param skillName the identifier of the skill to cast
     */
    void castSkill(String skillName);

    /**
     * Despawns the mob, removing the entity from the world and clearing its runtime state.
     */
    void despawn();
}
