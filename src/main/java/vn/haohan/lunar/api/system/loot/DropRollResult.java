package vn.haohan.lunar.api.system.loot;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Composite result of rolling a drop table, supporting items, dropped exp, and nested tables.
 */
public record DropRollResult(List<ItemStack> items, int experience, List<String> nestedTables) {

    public static final DropRollResult EMPTY = new DropRollResult(List.of(), 0, List.of());

    public DropRollResult {
        items = items != null ? List.copyOf(items) : List.of();
        nestedTables = nestedTables != null ? List.copyOf(nestedTables) : List.of();
    }

    public List<String> subTables() {
        return nestedTables();
    }
}
