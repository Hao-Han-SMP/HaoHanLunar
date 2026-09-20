package vn.haohan.lunar.api.system.item;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Public provider interface for resolving custom or vanilla item stacks.
 */
@FunctionalInterface
public interface ItemProvider {

    /**
     * Resolves an item ID string into an ItemStack.
     *
     * @param rawItemId The raw item identifier (e.g., "DIAMOND_SWORD", "haohan:dark_scythe").
     * @return An Optional containing the resolved ItemStack, or empty if resolution failed.
     */
    Optional<ItemStack> resolveItem(String rawItemId);
}
