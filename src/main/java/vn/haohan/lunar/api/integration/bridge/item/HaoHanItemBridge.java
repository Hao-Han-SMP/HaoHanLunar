package vn.haohan.lunar.core.integration.bridge.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import vn.haohan.itemcore.api.HaoHanItemCore;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Safe item resolver bridging HaoHanItemCore and vanilla Bukkit Materials.
 * Gracefully degrades if HaoHanItemCore is not available or item ID is vanilla.
 */
public final class HaoHanItemBridge {

    private static final HaoHanItemBridge INSTANCE = new HaoHanItemBridge();

    private BiFunction<String, Integer, Optional<ItemStack>> customResolver;
    private BiFunction<Material, Integer, ItemStack> itemStackFactory = (mat, amt) -> {
        try {
            return new ItemStack(mat, amt);
        } catch (Throwable ignored) {
            return null;
        }
    };

    public static HaoHanItemBridge get() {
        return INSTANCE;
    }

    /**
     * Overrides item resolution for testing or mock environments.
     */
    public void setCustomResolver(BiFunction<String, Integer, Optional<ItemStack>> resolver) {
        this.customResolver = resolver;
    }

    /**
     * Overrides vanilla ItemStack construction for headless test environments without Bukkit Server.
     */
    public void setItemStackFactory(BiFunction<Material, Integer, ItemStack> factory) {
        this.itemStackFactory = factory != null ? factory : (mat, amt) -> {
            try {
                return new ItemStack(mat, amt);
            } catch (Throwable ignored) {
                return null;
            }
        };
    }

    /**
     * Resolves an item reference into a Bukkit {@link ItemStack}.
     * Priority:
     * 1. Custom test resolver (if configured)
     * 2. HaoHanItemCore API (if loaded)
     * 3. Bukkit Material whitelist fallback
     *
     * @param itemId the namespaced or vanilla item id
     * @param amount the stack amount (minimum 1)
     * @return Optional containing ItemStack if valid, empty otherwise
     */
    public Optional<ItemStack> createItemStack(String itemId, int amount) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        int clampedAmount = Math.max(1, amount);
        String trimmed = itemId.trim();

        // 1. Check custom test resolver if set
        if (customResolver != null) {
            Optional<ItemStack> custom = customResolver.apply(trimmed, clampedAmount);
            if (custom != null && custom.isPresent()) {
                return custom;
            }
        }

        // 2. Try HaoHanItemCore API
        try {
            HaoHanItemCore core = HaoHanItemCore.get();
            if (core != null && core.getItemService() != null) {
                if (core.getItemService().exists(trimmed)) {
                    ItemStack customItem = core.getItemService().create(trimmed, clampedAmount);
                    if (customItem != null) {
                        return Optional.of(customItem);
                    }
                }
            }
        } catch (Throwable ignored) {
            // HaoHanItemCore not loaded or threw exception
        }

        // 3. Fallback to Bukkit Material
        Material material = Material.matchMaterial(trimmed);
        if (material == null && trimmed.contains(":")) {
            String stripped = trimmed.substring(trimmed.indexOf(':') + 1);
            material = Material.matchMaterial(stripped);
        }

        if (material != null && isSafeItemMaterial(material)) {
            ItemStack stack = itemStackFactory.apply(material, clampedAmount);
            if (stack != null) {
                return Optional.of(stack);
            }
        }

        return Optional.empty();
    }

    private static boolean isSafeItemMaterial(Material material) {
        if (material == null) return false;
        String name = material.name();
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR") || name.startsWith("LEGACY_")) {
            return false;
        }
        try {
            if (material.isAir() || !material.isItem()) {
                return false;
            }
        } catch (Throwable ignored) {
            // Gracefully tolerate headless tests where Bukkit RegistryAccess is not present
        }
        return true;
    }
}
