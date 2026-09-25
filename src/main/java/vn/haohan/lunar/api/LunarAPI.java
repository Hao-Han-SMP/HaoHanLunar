package vn.haohan.lunar.api;

import vn.haohan.lunar.api.manager.*;
import vn.haohan.lunar.api.system.item.IItemProvider;

import java.util.Objects;

/**
 * Service locator for the HaoHanLunar API.
 * Provides access to core managers for mobs, skills, combat, loot, spawners, and items.
 */
public final class LunarAPI {

    private static IMobManager mobManager;
    private static ISkillManager skillManager;
    private static ICombatManager combatManager;
    private static ILootManager lootManager;
    private static ISpawnerManager spawnerManager;
    private static IItemProvider itemProvider;

    private LunarAPI() {}

    /**
     * Returns the active mob manager instance.
     *
     * @return the active mob manager
     * @throws NullPointerException if the mob manager has not been initialized
     */
    public static IMobManager getMobManager() {
        return Objects.requireNonNull(mobManager, "IMobManager has not been initialized yet!");
    }

    /**
     * Registers the active mob manager instance.
     *
     * @param manager the mob manager to register
     */
    public static void setMobManager(IMobManager manager) {
        mobManager = manager;
    }

    /**
     * Returns the active skill manager instance.
     *
     * @return the active skill manager
     * @throws NullPointerException if the skill manager has not been initialized
     */
    public static ISkillManager getSkillManager() {
        return Objects.requireNonNull(skillManager, "ISkillManager has not been initialized yet!");
    }

    /**
     * Registers the active skill manager instance.
     *
     * @param manager the skill manager to register
     */
    public static void setSkillManager(ISkillManager manager) {
        skillManager = manager;
    }

    /**
     * Returns the active combat pipeline manager instance.
     *
     * @return the active combat manager
     * @throws NullPointerException if the combat manager has not been initialized
     */
    public static ICombatManager getCombatManager() {
        return Objects.requireNonNull(combatManager, "ICombatManager has not been initialized yet!");
    }

    /**
     * Registers the active combat pipeline manager instance.
     *
     * @param manager the combat manager to register
     */
    public static void setCombatManager(ICombatManager manager) {
        combatManager = manager;
    }

    /**
     * Returns the active loot and drop manager instance.
     *
     * @return the active loot manager
     * @throws NullPointerException if the loot manager has not been initialized
     */
    public static ILootManager getLootManager() {
        return Objects.requireNonNull(lootManager, "ILootManager has not been initialized yet!");
    }

    /**
     * Registers the active loot and drop manager instance.
     *
     * @param manager the loot manager to register
     */
    public static void setLootManager(ILootManager manager) {
        lootManager = manager;
    }

    /**
     * Returns the active spawner manager instance.
     *
     * @return the active spawner manager
     * @throws NullPointerException if the spawner manager has not been initialized
     */
    public static ISpawnerManager getSpawnerManager() {
        return Objects.requireNonNull(spawnerManager, "ISpawnerManager has not been initialized yet!");
    }

    /**
     * Registers the active spawner manager instance.
     *
     * @param manager the spawner manager to register
     */
    public static void setSpawnerManager(ISpawnerManager manager) {
        spawnerManager = manager;
    }

    /**
     * Returns the active custom item provider instance.
     *
     * @return the active item provider
     * @throws NullPointerException if the item provider has not been initialized
     */
    public static IItemProvider getItemProvider() {
        return Objects.requireNonNull(itemProvider, "IItemProvider has not been initialized yet!");
    }

    /**
     * Registers the active custom item provider instance.
     *
     * @param provider the item provider to register
     */
    public static void setItemProvider(IItemProvider provider) {
        itemProvider = provider;
    }

    /**
     * Checks whether core gameplay services (mobs, skills, combat) have been initialized.
     *
     * @return true if mob, skill, and combat managers are non-null
     */
    public static boolean isReady() {
        return mobManager != null && skillManager != null && combatManager != null;
    }
}
