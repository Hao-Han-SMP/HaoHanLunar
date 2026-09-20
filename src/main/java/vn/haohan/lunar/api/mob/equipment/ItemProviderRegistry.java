package vn.haohan.lunar.api.mob.equipment;

import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.integration.bridge.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.item.ItemProvider;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry and resolver for item generation across custom mob equipment and drops.
 * Delegates to HaoHanItemBridge and allows additional prefix hooks.
 */
public final class ItemProviderRegistry implements ItemProvider {

    private final Map<String, Function<String, ItemStack>> customResolvers = new ConcurrentHashMap<>();

    public ItemProviderRegistry() {
    }

    /**
     * Registers a custom item hook resolver (e.g. MMOItems or custom plugin).
     */
    public void registerHook(String prefix, Function<String, ItemStack> resolver) {
        Objects.requireNonNull(prefix, "Hook prefix must not be null");
        Objects.requireNonNull(resolver, "Resolver must not be null");
        customResolvers.put(prefix.toLowerCase(Locale.ROOT), resolver);
    }

    /**
     * Resolves an item ID string into an ItemStack.
     * Examples:
     * - "DIAMOND_SWORD" -> Bukkit Material / HaoHanItemBridge
     * - "haohan:dark_scythe" -> custom resolver or HaoHanItemBridge
     * - "AIR" or null -> empty
     */
    public Optional<ItemStack> resolveItem(String rawItemId) {
        if (rawItemId == null || rawItemId.isBlank()) {
            return Optional.empty();
        }

        String trimmed = rawItemId.trim();
        int colonIdx = trimmed.indexOf(':');

        if (colonIdx > 0) {
            String prefix = trimmed.substring(0, colonIdx).toLowerCase(Locale.ROOT);
            String itemId = trimmed.substring(colonIdx + 1);

            Function<String, ItemStack> resolver = customResolvers.get(prefix);
            if (resolver != null) {
                try {
                    ItemStack custom = resolver.apply(itemId);
                    if (custom != null) {
                        return Optional.of(custom.clone());
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        // Delegate to HaoHanItemBridge which handles test mock factories and material matching
        return HaoHanItemBridge.get().createItemStack(trimmed, 1);
    }
}
