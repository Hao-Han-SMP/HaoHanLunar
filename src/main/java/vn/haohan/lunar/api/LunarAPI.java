package vn.haohan.lunar.api;

import vn.haohan.lunar.api.manager.*;
import vn.haohan.lunar.api.system.item.ItemProvider;

import java.util.Objects;

/**
 * Service locator for the HaoHanLunar API.
 * Provides access to core managers for mobs, skills, combat, loot, spawners, and items.
 */
public final class LunarAPI {

    private static MobManager mobManager;
    private static SkillManager skillManager;
    private static CombatManager combatManager;
    private static LootManager lootManager;
    private static SpawnerManager spawnerManager;
    private static ItemProvider itemProvider;

    private LunarAPI() {}

    /**
     * Returns the active mob manager instance.
     *
     * @return the active mob manager
     * @throws NullPointerException if the mob manager has not been initialized
     */
    public static MobManager getMobManager() {
        return Objects.requireNonNull(mobManager, "MobManager has not been initialized yet!");
    }

    /**
     * Registers the active mob manager instance.
     *
     * @param manager the mob manager to register
     */
    public static void setMobManager(MobManager manager) {
        mobManager = manager;
    }

    /**
     * Returns the active skill manager instance.
     *
     * @return the active skill manager
     * @throws NullPointerException if the skill manager has not been initialized
     */
    public static SkillManager getSkillManager() {
        return Objects.requireNonNull(skillManager, "SkillManager has not been initialized yet!");
    }

    /**
     * Registers the active skill manager instance.
     *
     * @param manager the skill manager to register
     */
    public static void setSkillManager(SkillManager manager) {
        skillManager = manager;
    }

    /**
     * Returns the active combat pipeline manager instance.
     *
     * @return the active combat manager
     * @throws NullPointerException if the combat manager has not been initialized
     */
    public static CombatManager getCombatManager() {
        return Objects.requireNonNull(combatManager, "CombatManager has not been initialized yet!");
    }

    /**
     * Registers the active combat pipeline manager instance.
     *
     * @param manager the combat manager to register
     */
    public static void setCombatManager(CombatManager manager) {
        combatManager = manager;
    }

    /**
     * Returns the active loot and drop manager instance.
     *
     * @return the active loot manager
     * @throws NullPointerException if the loot manager has not been initialized
     */
    public static LootManager getLootManager() {
        return Objects.requireNonNull(lootManager, "LootManager has not been initialized yet!");
    }

    /**
     * Registers the active loot and drop manager instance.
     *
     * @param manager the loot manager to register
     */
    public static void setLootManager(LootManager manager) {
        lootManager = manager;
    }

    /**
     * Returns the active spawner manager instance.
     *
     * @return the active spawner manager
     * @throws NullPointerException if the spawner manager has not been initialized
     */
    public static SpawnerManager getSpawnerManager() {
        return Objects.requireNonNull(spawnerManager, "SpawnerManager has not been initialized yet!");
    }

    /**
     * Registers the active spawner manager instance.
     *
     * @param manager the spawner manager to register
     */
    public static void setSpawnerManager(SpawnerManager manager) {
        spawnerManager = manager;
    }

    /**
     * Returns the active custom item provider instance.
     *
     * @return the active item provider
     * @throws NullPointerException if the item provider has not been initialized
     */
    public static ItemProvider getItemProvider() {
        return Objects.requireNonNull(itemProvider, "ItemProvider has not been initialized yet!");
    }

    /**
     * Registers the active custom item provider instance.
     *
     * @param provider the item provider to register
     */
    public static void setItemProvider(ItemProvider provider) {
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
