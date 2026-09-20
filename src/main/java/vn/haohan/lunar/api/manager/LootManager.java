package vn.haohan.lunar.api.manager;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Manages custom drop tables and loot generation for mobs upon death.
 */
public interface LootManager {

    /**
     * Generates a list of dropped items for a victim mob based on a configured drop table.
     *
     * @param dropTableId identifier of the drop table configuration
     * @param victim      the dying mob entity
     * @param killer      the player that delivered the killing blow, or null if killed by other sources
     * @param luckBonus   additional luck modifier influencing roll chances
     * @return list of generated item stacks
     */
    List<ItemStack> generateDrops(String dropTableId, LivingEntity victim, Player killer, double luckBonus);

    /**
     * Checks if a drop table is registered under the given identifier.
     *
     * @param dropTableId the drop table identifier
     * @return true if the drop table exists
     */
    boolean hasDropTable(String dropTableId);
}
