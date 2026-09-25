package vn.haohan.lunar.robot.battery;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.List;

public final class RobotBatteryUtil {

    public static final String BATTERY_SMALL = "small";
    public static final String BATTERY_MEDIUM = "medium";
    public static final String BATTERY_LARGE = "large";

    public static final int CAPACITY_SMALL = 5000;
    public static final int CAPACITY_MEDIUM = 15000;
    public static final int CAPACITY_LARGE = 30000;

    private static final NamespacedKey ENERGY_KEY = new NamespacedKey("haohan", "battery_energy");

    private RobotBatteryUtil() {}

    public static boolean isBatteryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = getCustomItemId(item);
        return id != null && id.startsWith("haohan:robot_battery_");
    }

    public static String getBatteryType(ItemStack item) {
        if (!isBatteryItem(item)) return null;
        String id = getCustomItemId(item);
        if (id == null) return null;
        if (id.contains("large")) return BATTERY_LARGE;
        if (id.contains("medium")) return BATTERY_MEDIUM;
        if (id.contains("small")) return BATTERY_SMALL;
        return null;
    }

    public static int getCapacity(String batteryType) {
        if (batteryType == null) return 0;
        return switch (batteryType.toLowerCase()) {
            case BATTERY_LARGE -> CAPACITY_LARGE;
            case BATTERY_MEDIUM -> CAPACITY_MEDIUM;
            case BATTERY_SMALL -> CAPACITY_SMALL;
            default -> 0;
        };
    }

    public static int getBatteryEnergy(ItemStack item) {
        if (!isBatteryItem(item)) return 0;
        String type = getBatteryType(item);
        int capacity = getCapacity(type);
        if (capacity <= 0) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(ENERGY_KEY, PersistentDataType.INTEGER)) {
            Integer val = pdc.get(ENERGY_KEY, PersistentDataType.INTEGER);
            if (val != null) {
                return Math.max(0, Math.min(capacity, val));
            }
        }

        // Fallback: read from Damageable if PDC not yet set (e.g. freshly crafted)
        if (meta instanceof Damageable dmg) {
            int damage = dmg.getDamage();
            int remaining = Math.max(0, capacity - damage);
            return Math.min(capacity, remaining);
        }

        return capacity;
    }

    public static void setBatteryEnergy(ItemStack item, int energy) {
        if (!isBatteryItem(item)) return;
        String type = getBatteryType(item);
        int capacity = getCapacity(type);
        if (capacity <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int clampedEnergy = Math.max(0, Math.min(capacity, energy));

        // 1. Set PDC value
        meta.getPersistentDataContainer().set(ENERGY_KEY, PersistentDataType.INTEGER, clampedEnergy);

        // 2. Set Damageable durability
        if (meta instanceof Damageable dmg) {
            if (!dmg.hasMaxDamage() || dmg.getMaxDamage() != capacity) {
                dmg.setMaxDamage(capacity);
            }
            int damage = Math.max(0, Math.min(capacity, capacity - clampedEnergy));
            dmg.setDamage(damage);
        }

        // 3. Update Lore with dynamic formatted energy gauge
        updateBatteryLore(meta, type, clampedEnergy, capacity);

        item.setItemMeta(meta);
    }

    public static boolean chargeBattery(ItemStack item, int amount) {
        if (!isBatteryItem(item) || amount <= 0) return false;
        String type = getBatteryType(item);
        int capacity = getCapacity(type);
        int current = getBatteryEnergy(item);
        if (current >= capacity) return false;

        int next = Math.min(capacity, current + amount);
        setBatteryEnergy(item, next);
        return true;
    }

    public static ItemStack createBattery(String batteryType, int energy) {
        int capacity = getCapacity(batteryType);
        if (capacity <= 0) return null;

        var itemFactory = HaoHanItemCore.get().getItemFactory();
        String id = "haohan:robot_battery_" + batteryType.toLowerCase();
        ItemStack item = itemFactory.create(id, 1);
        if (item == null) return null;

        setBatteryEnergy(item, energy);
        return item;
    }

    private static void updateBatteryLore(ItemMeta meta, String type, int energy, int capacity) {
        List<String> rawLore = new ArrayList<>();
        String tierDesc = switch (type) {
            case BATTERY_LARGE -> "§7Lõi năng lượng lượng tử cho Robot 4 Chân.";
            case BATTERY_MEDIUM -> "§7Nguồn năng lượng tiêu chuẩn cho Robot 4 Chân.";
            default -> "§7Nguồn năng lượng sơ cấp cho Robot 4 Chân.";
        };
        rawLore.add(tierDesc);

        int pct = (int) Math.round(((double) energy / capacity) * 100.0);
        String formattedEnergy = String.format("%,d", energy);
        String formattedCapacity = String.format("%,d", capacity);

        if (energy <= 0) {
            rawLore.add("§c▪ Dung lượng: §c0 §7/ §f" + formattedCapacity + " EU §c[CẠN PIN]");
        } else if (energy >= capacity) {
            rawLore.add("§a▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedCapacity + " EU §a(100%)");
        } else if (pct >= 40) {
            rawLore.add("§e▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedCapacity + " EU §e(" + pct + "%)");
        } else {
            rawLore.add("§c▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedCapacity + " EU §c(" + pct + "%)");
        }

        rawLore.add("§8Đặt vào Trạm Sạc để nạp lại năng lượng.");

        List<Component> components = rawLore.stream()
                .map(line -> (Component) LegacyComponentSerializer.legacySection().deserialize(line))
                .toList();
        meta.lore(components);
    }

    private static String getCustomItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        try {
            return HaoHanItemCore.get().getItemService().getId(item);
        } catch (Exception e) {
            return null;
        }
    }
}
