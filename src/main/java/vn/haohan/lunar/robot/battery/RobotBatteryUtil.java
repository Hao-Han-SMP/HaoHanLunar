package vn.haohan.lunar.robot.battery;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.itemcore.api.HaoHanItemCore;

import java.util.ArrayList;
import java.util.List;

public final class RobotBatteryUtil {

    public static final String BATTERY_STANDARD = "small";
    public static final String BATTERY_SMALL = "small";
    public static final String BATTERY_MEDIUM = "medium";
    public static final String BATTERY_LARGE = "large";

    public static final int NOMINAL_CAPACITY = 35000;
    public static final int CAPACITY_SMALL = NOMINAL_CAPACITY;
    public static final int CAPACITY_MEDIUM = NOMINAL_CAPACITY;
    public static final int CAPACITY_LARGE = NOMINAL_CAPACITY;

    private static final NamespacedKey ENERGY_KEY = new NamespacedKey("haohan", "battery_energy");
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("haohan", "battery_health");
    private static final NamespacedKey CYCLES_KEY = new NamespacedKey("haohan", "battery_cycles");

    private RobotBatteryUtil() {}

    public static boolean isBatteryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String id = getCustomItemId(item);
        return id != null && (id.equals("haohan:robot_battery") || id.startsWith("haohan:robot_battery_"));
    }

    public static String getBatteryType(ItemStack item) {
        if (!isBatteryItem(item)) return null;
        return BATTERY_STANDARD;
    }

    public static int getCapacity(String batteryType) {
        if (batteryType == null || "none".equalsIgnoreCase(batteryType)) return 0;
        return NOMINAL_CAPACITY;
    }

    public static int calculateEffectiveCapacity(double health) {
        double clampedHealth = Math.max(0.0, Math.min(100.0, health));
        double ratio = clampedHealth / 100.0;
        // Exponential decay: (health/100)^2 * NOMINAL_CAPACITY
        int effective = (int) Math.round(NOMINAL_CAPACITY * (ratio * ratio));
        return Math.max(1000, effective);
    }

    public static double getBatteryHealth(ItemStack item) {
        if (!isBatteryItem(item)) return 100.0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 100.0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(HEALTH_KEY, PersistentDataType.DOUBLE)) {
            Double val = pdc.get(HEALTH_KEY, PersistentDataType.DOUBLE);
            if (val != null) {
                return Math.max(0.0, Math.min(100.0, val));
            }
        }
        return 100.0;
    }

    public static void setBatteryHealth(ItemStack item, double health) {
        if (!isBatteryItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        double clamped = Math.max(0.0, Math.min(100.0, health));
        meta.getPersistentDataContainer().set(HEALTH_KEY, PersistentDataType.DOUBLE, clamped);
        item.setItemMeta(meta);

        // Re-clamp current energy to new effective capacity & refresh lore
        int current = getBatteryEnergy(item);
        setBatteryEnergy(item, current);
    }

    public static int getBatteryCycles(ItemStack item) {
        if (!isBatteryItem(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(CYCLES_KEY, PersistentDataType.INTEGER)) {
            Integer val = pdc.get(CYCLES_KEY, PersistentDataType.INTEGER);
            if (val != null) {
                return Math.max(0, val);
            }
        }
        return 0;
    }

    public static void setBatteryCycles(ItemStack item, int cycles) {
        if (!isBatteryItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(CYCLES_KEY, PersistentDataType.INTEGER, Math.max(0, cycles));
        item.setItemMeta(meta);

        int current = getBatteryEnergy(item);
        setBatteryEnergy(item, current);
    }

    public static void incrementBatteryCycles(ItemStack item) {
        if (!isBatteryItem(item)) return;
        int nextCycles = getBatteryCycles(item) + 1;
        setBatteryCycles(item, nextCycles);
        double currentHealth = getBatteryHealth(item);
        setBatteryHealth(item, Math.max(5.0, currentHealth - 0.5));
    }

    public static int getEffectiveCapacity(ItemStack item) {
        if (!isBatteryItem(item)) return 0;
        double health = getBatteryHealth(item);
        return calculateEffectiveCapacity(health);
    }

    public static int getBatteryEnergy(ItemStack item) {
        if (!isBatteryItem(item)) return 0;
        int effectiveCap = getEffectiveCapacity(item);
        if (effectiveCap <= 0) return 0;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(ENERGY_KEY, PersistentDataType.INTEGER)) {
            Integer val = pdc.get(ENERGY_KEY, PersistentDataType.INTEGER);
            if (val != null) {
                return Math.max(0, Math.min(effectiveCap, val));
            }
        }

        // Fallback: read from Damageable if PDC not yet set (e.g. freshly crafted)
        if (meta instanceof Damageable dmg) {
            int damage = dmg.getDamage();
            int remaining = Math.max(0, effectiveCap - damage);
            return Math.min(effectiveCap, remaining);
        }

        return effectiveCap;
    }

    public static void setBatteryEnergy(ItemStack item, int energy) {
        if (!isBatteryItem(item)) return;
        int effectiveCap = getEffectiveCapacity(item);
        if (effectiveCap <= 0) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        int clampedEnergy = Math.max(0, Math.min(effectiveCap, energy));

        // 1. Set PDC value
        meta.getPersistentDataContainer().set(ENERGY_KEY, PersistentDataType.INTEGER, clampedEnergy);

        // 2. Set Damageable durability
        if (meta instanceof Damageable dmg) {
            if (!dmg.hasMaxDamage() || dmg.getMaxDamage() != effectiveCap) {
                dmg.setMaxDamage(effectiveCap);
            }
            int damage = Math.max(0, Math.min(effectiveCap, effectiveCap - clampedEnergy));
            dmg.setDamage(damage);
        }

        // 3. Update Lore
        double health = getBatteryHealth(item);
        int cycles = getBatteryCycles(item);
        updateBatteryLore(meta, clampedEnergy, effectiveCap, health, cycles);

        item.setItemMeta(meta);
    }

    public static boolean chargeBattery(ItemStack item, int amount) {
        if (!isBatteryItem(item) || amount <= 0) return false;
        int cap = getEffectiveCapacity(item);
        int current = getBatteryEnergy(item);
        if (current >= cap) return false;

        int next = Math.min(cap, current + amount);
        setBatteryEnergy(item, next);
        return true;
    }

    public static ItemStack createBattery(String batteryType, int energy) {
        var itemFactory = HaoHanItemCore.get().getItemFactory();
        ItemStack item = itemFactory.create("haohan:robot_battery_small", 1);
        if (item == null) return null;

        setBatteryHealth(item, 100.0);
        setBatteryCycles(item, 0);
        setBatteryEnergy(item, energy);
        return item;
    }

    private static void updateBatteryLore(ItemMeta meta, int energy, int effectiveCap, double health, int cycles) {
        List<String> rawLore = new ArrayList<>();
        rawLore.add("§7Lõi năng lượng công nghệ cao cho Robot 4 Chân.");

        String healthTag = health >= 80.0 ? "§a[TỐT]" : (health >= 50.0 ? "§e[TRUNG BÌNH]" : "§c[CHAI PIN]");
        rawLore.add("§a▪ Sức khỏe pin: §f" + String.format("%.1f", health) + "% " + healthTag);
        rawLore.add("§b▪ Chu kỳ sạc: §f" + cycles + " lần");

        int pct = (int) Math.round(((double) energy / effectiveCap) * 100.0);
        String formattedEnergy = String.format("%,d", energy);
        String formattedEffective = String.format("%,d", effectiveCap);
        String formattedNominal = String.format("%,d", NOMINAL_CAPACITY);

        if (energy <= 0) {
            rawLore.add("§c▪ Dung lượng: §c0 §7/ §f" + formattedEffective + " EU §c[CẠN PIN]");
        } else if (energy >= effectiveCap) {
            rawLore.add("§a▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedEffective + " EU §a(100%)");
        } else if (pct >= 40) {
            rawLore.add("§e▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedEffective + " EU §e(" + pct + "%)");
        } else {
            rawLore.add("§c▪ Dung lượng: §f" + formattedEnergy + " §7/ §f" + formattedEffective + " EU §c(" + pct + "%)");
        }
        rawLore.add("§8▪ Thiết kế danh định: " + formattedNominal + " EU");
        rawLore.add("§8Đặt vào Trạm Sạc để nạp năng lượng.");

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
