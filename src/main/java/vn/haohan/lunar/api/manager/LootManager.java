package vn.haohan.lunar.api.manager;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Public interface for generating custom mob loot and querying drop tables.
 */
public interface LootManager {

    /**
     * Generates a list of dropped items for a victim mob based on a configured drop table.\n     *
     * @param dropTableId ID of the drop table to generate from.
     * @param victim      The dying mob entity.
     * @param killer      The killing player, if present.
     * @param luckBonus   Extra luck modifier bonus.
     * @return List of generated ItemStacks.
     */
    List<ItemStack> generateDrops(String dropTableId, LivingEntity victim, Player killer, double luckBonus);

    /**
     * Checks if a drop table ID is registered.
     */
    boolean hasDropTable(String dropTableId);
}
