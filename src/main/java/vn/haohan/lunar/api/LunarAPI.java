package vn.haohan.lunar.api;

import vn.haohan.lunar.api.system.item.ItemProvider;
import vn.haohan.lunar.api.manager.*;

import java.util.Objects;

/**
 * Global entry point and service locator for the HaoHanLunar Public API.
 * Provides unified access to all gameplay subsystems for external plugins.
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
     * @return The active mob manager instance.
     */
    public static MobManager getMobManager() {
        return Objects.requireNonNull(mobManager, "MobManager has not been initialized yet!");
    }

    public static void setMobManager(MobManager manager) {
        mobManager = manager;
    }

    /**
     * @return The active skill manager instance.
     */
    public static SkillManager getSkillManager() {
        return Objects.requireNonNull(skillManager, "SkillManager has not been initialized yet!");
    }

    public static void setSkillManager(SkillManager manager) {
        skillManager = manager;
    }

    /**
     * @return The active combat pipeline manager instance.
     */
    public static CombatManager getCombatManager() {
        return Objects.requireNonNull(combatManager, "CombatManager has not been initialized yet!");
    }

    public static void setCombatManager(CombatManager manager) {
        combatManager = manager;
    }

    /**
     * @return The active loot and drop manager instance.
     */
    public static LootManager getLootManager() {
        return Objects.requireNonNull(lootManager, "LootManager has not been initialized yet!");
    }

    public static void setLootManager(LootManager manager) {
        lootManager = manager;
    }

    /**
     * @return The active spawner manager instance.
     */
    public static SpawnerManager getSpawnerManager() {
        return Objects.requireNonNull(spawnerManager, "SpawnerManager has not been initialized yet!");
    }

    public static void setSpawnerManager(SpawnerManager manager) {
        spawnerManager = manager;
    }

    /**
     * @return The active custom item provider instance.
     */
    public static ItemProvider getItemProvider() {
        return Objects.requireNonNull(itemProvider, "ItemProvider has not been initialized yet!");
    }

    public static void setItemProvider(ItemProvider provider) {
        itemProvider = provider;
    }

    /**
     * @return True if core services are initialized and ready for consumption.
     */
    public static boolean isReady() {
        return mobManager != null && skillManager != null && combatManager != null;
    }
}
