package vn.haohan.lunar.api.system.mob;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.presentation.display.bossbar.LunarBossBarTracker;
import vn.haohan.lunar.api.system.combat.DamageModifierTable;
import vn.haohan.lunar.api.system.combat.ImmunityTable;
import vn.haohan.lunar.api.system.combat.cc.CrowdControlTracker;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.interrupt.InterruptReason;
import vn.haohan.lunar.api.system.combat.threat.IThreatTable;
import vn.haohan.lunar.api.system.mob.ai.antistuck.AntiStuckController;
import vn.haohan.lunar.api.system.mob.disguise.DisguiseData;
import vn.haohan.lunar.api.system.mob.stat.StatHolder;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Public domain contract representing an active custom mob instance in the world.
 * Provides unified access to mob identity, combat systems, stats, navigation,
 * boss presentation, and lifecycle controls.
 */
public interface IMob {

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
    IThreatTable threatTable();

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

    /**
     * Returns the faction identifier of this mob, or empty string if unaligned.
     *
     * @return the faction string
     */
    default String faction() {
        return "";
    }

    /**
     * Returns the spawn location of this mob, or null if unknown.
     *
     * @return the spawn location or null
     */
    default Location spawnLocation() {
        return null;
    }

    /**
     * Returns the stat holder tracking custom stats and attributes.
     */
    default StatHolder stats() {
        return null;
    }

    /**
     * Returns the immunity table protecting this mob from specific damage causes or effects.
     */
    default ImmunityTable immunityTable() {
        return null;
    }

    /**
     * Returns the crowd control tracker managing stuns, silences, and roots.
     */
    default CrowdControlTracker crowdControl() {
        return null;
    }

    /**
     * Returns the anti-stuck navigation controller.
     */
    default AntiStuckController antiStuckController() {
        return null;
    }

    /**
     * Returns the damage modifier table containing multiplicative/additive adjustments.
     */
    default DamageModifierTable damageModifiers() {
        return DamageModifierTable.empty();
    }

    /**
     * Updates the damage modifier table.
     */
    default void setDamageModifiers(DamageModifierTable damageModifiers) {}

    /**
     * Returns the boss bar tracker associated with this mob.
     */
    default LunarBossBarTracker bossBars() {
        return null;
    }

    /**
     * Checks if this mob is currently invulnerable.
     */
    default boolean isInvulnerable(long currentTick) {
        return false;
    }

    /**
     * Sets invulnerability for a duration in ticks.
     */
    default void setInvulnerableTicks(int ticks, long currentTick) {}

    /**
     * Checks if the mob is currently in a berserk state.
     */
    default boolean isBerserk() {
        return false;
    }

    /**
     * Sets the berserk state.
     */
    default void setBerserk(boolean berserk) {}

    /**
     * Checks if the mob is soft-leashed to its spawn point.
     */
    default boolean isSoftLeashed() {
        return false;
    }

    /**
     * Updates the soft leash state.
     */
    default void setSoftLeashed(boolean softLeashed) {}

    /**
     * Returns the disguise definition applied to this mob, or null if none.
     */
    default DisguiseData disguise() {
        return null;
    }

    /**
     * Applies or removes disguise on this mob.
     */
    default void setDisguise(DisguiseData disguise) {}

    /**
     * Checks if this mob has an active disguise.
     */
    default boolean isDisguised() {
        return disguise() != null;
    }

    /**
     * Returns the active ModelEngine model ID if applied.
     */
    default String activeModelId() {
        return null;
    }

    /**
     * Sets the active ModelEngine model ID.
     */
    default void setActiveModelId(String activeModelId) {}

    /**
     * Returns the active ModelEngine state.
     */
    default String activeModelState() {
        return null;
    }

    /**
     * Sets the active ModelEngine state.
     */
    default void setActiveModelState(String activeModelState) {}

    /**
     * Returns the UUID of the parent mob if part of a minion/spawn hierarchy.
     */
    default UUID parentUUID() {
        return null;
    }

    /**
     * Sets the parent entity UUID.
     */
    default void setParentUUID(UUID parentUUID) {}

    /**
     * Returns the vehicle mount UUID if this mob is riding another entity.
     */
    default UUID mountUUID() {
        return null;
    }

    /**
     * Sets the mount vehicle UUID.
     */
    default void setMountUUID(UUID mountUUID) {}

    /**
     * Checks if this mob is currently riding another entity.
     */
    default boolean isMounted() {
        return mountUUID() != null;
    }

    /**
     * Returns the set of entity UUIDs currently riding this mob.
     */
    default Set<UUID> riderUUIDs() {
        return Collections.emptySet();
    }

    /**
     * Checks if this mob has any passengers/riders.
     */
    default boolean hasRiders() {
        return !riderUUIDs().isEmpty();
    }

    /**
     * Resolves the maximum health of this mob.
     */
    default double maxHealth() {
        return entity() != null ? entity().getHealth() : 20.0;
    }

    /**
     * Checks if the mob is currently channeling a skill.
     */
    default boolean isChanneling() {
        return false;
    }

    /**
     * Returns the identifier of the skill currently being channeled, or null.
     */
    default String activeChannelingSkill() {
        return null;
    }

    /**
     * Interrupts all active and channeling skills on this mob.
     *
     * @param reason the reason for the interruption
     * @return the number of skills interrupted
     */
    default int interruptActiveSkills(InterruptReason reason) {
        return 0;
    }

    /**
     * Resets the mob back to its spawn location, clearing threat, soft leash,
     * restoring full health, and cancelling active abilities.
     */
    default void resetToSpawn() {}

    /**
     * Resets the mob back to its spawn location with the given tick.
     */
    default void resetToSpawn(long currentTick) {
        resetToSpawn();
    }
}
