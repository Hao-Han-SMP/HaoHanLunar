package vn.haohan.lunar.robot.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;
import vn.haohan.lunar.robot.LunarRobotData;
import vn.haohan.lunar.robot.LunarRobotEntity;
import vn.haohan.lunar.robot.LunarRobotMechanic;

import java.util.List;

public class LunarRobotInventoryUi implements InventoryHolder, Listener {
    private final Plugin plugin;
    private final LunarRobotMechanic mechanic;
    private final Player player;
    private final LunarRobotEntity robot;
    private final LunarRobotData data;
    private final Inventory inventory;

    public LunarRobotInventoryUi(Plugin plugin, LunarRobotMechanic mechanic, Player player, LunarRobotEntity robot) {
        this.plugin = plugin;
        this.mechanic = mechanic;
        this.player = player;
        this.robot = robot;
        this.data = robot.getData();
        this.inventory = Bukkit.createInventory(this, 9, "§3§lKhoang Lắp Module Robot (1x9)");
        setupInventory();
    }

    private void setupInventory() {
        var itemFactory = HaoHanItemCore.get().getItemFactory();

        // 1. Populate Module 1 in Slot 0
        if (data.getModule1Id() != null) {
            ItemStack m1 = itemFactory.create(data.getModule1Id(), 1);
            if (m1 != null) {
                applyEfficiencyLore(m1, data.getModule1Efficiency());
                inventory.setItem(0, m1);
            }
        }

        // 2. Populate Module 2 in Slot 1
        if (data.getModule2Id() != null) {
            ItemStack m2 = itemFactory.create(data.getModule2Id(), 1);
            if (m2 != null) {
                applyEfficiencyLore(m2, data.getModule2Efficiency());
                inventory.setItem(1, m2);
            }
        }

        // 3. Populate Battery in Slot 2
        if (!"none".equals(data.getBatteryType()) && data.getBatteryType() != null) {
            ItemStack bat = RobotBatteryUtil.createBattery(data.getBatteryType(), data.getEnergy());
            if (bat != null) {
                inventory.setItem(2, bat);
            }
        }

        // 4. Fill Slots 3..8 with Locked Glass Panes
        ItemStack lockedPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = lockedPane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8[Khoang Khóa]");
            meta.setLore(List.of("§7Chỉ sử dụng Slot 1 & 2 cho Module,", "§7và Slot 3 cho Pin Năng Lượng."));
            lockedPane.setItemMeta(meta);
        }
        for (int i = 3; i < 9; i++) {
            inventory.setItem(i, lockedPane);
        }
    }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        int rawSlot = event.getRawSlot();

        // 1. Block interactions with locked slots 3..8
        if (rawSlot >= 3 && rawSlot < 9) {
            event.setCancelled(true);
            return;
        }

        // 2. Handle Shift-Click from player inventory into robot slots
        if (event.isShiftClick()) {
            if (rawSlot >= 9) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;

                event.setCancelled(true);
                if (isModuleItem(clicked)) {
                    if (inventory.getItem(0) == null || inventory.getItem(0).getType() == Material.AIR) {
                        inventory.setItem(0, clicked.clone());
                        clicked.setAmount(0);
                    } else if (inventory.getItem(1) == null || inventory.getItem(1).getType() == Material.AIR) {
                        inventory.setItem(1, clicked.clone());
                        clicked.setAmount(0);
                    } else {
                        player.sendMessage("§c§l[Robot] §cKhoang Module đã đầy!");
                    }
                } else if (isBatteryItem(clicked)) {
                    if (inventory.getItem(2) == null || inventory.getItem(2).getType() == Material.AIR) {
                        inventory.setItem(2, clicked.clone());
                        clicked.setAmount(0);
                    } else {
                        player.sendMessage("§c§l[Robot] §cKhoang Pin đã có pin!");
                    }
                }
                return;
            }
        }

        // 3. Number Key swap protection
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                    if (rawSlot == 0 || rawSlot == 1) {
                        if (!isModuleItem(hotbarItem)) {
                            event.setCancelled(true);
                            player.sendMessage("§c§l[Robot] §cSlot này chỉ nhận Module Robot!");
                            return;
                        }
                    } else if (rawSlot == 2) {
                        if (!isBatteryItem(hotbarItem)) {
                            event.setCancelled(true);
                            player.sendMessage("§c§l[Robot] §cSlot này chỉ nhận Pin Robot!");
                            return;
                        }
                    }
                }
            }
        }

        // 4. Cursor placement validation in slots 0, 1, 2
        if (rawSlot == 0 || rawSlot == 1 || rawSlot == 2) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (rawSlot == 0 || rawSlot == 1) {
                    if (!isModuleItem(cursor)) {
                        event.setCancelled(true);
                        player.sendMessage("§c§l[Robot] §cSlot này chỉ nhận các Module Robot!");
                    }
                } else {
                    if (!isBatteryItem(cursor)) {
                        event.setCancelled(true);
                        player.sendMessage("§c§l[Robot] §cSlot này chỉ nhận Pin Robot!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) return;

        for (int slot : event.getRawSlots()) {
            if (slot >= 3 && slot < 9) {
                event.setCancelled(true);
                return;
            }
            if (slot == 0 || slot == 1) {
                if (!isModuleItem(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (slot == 2) {
                if (!isBatteryItem(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;

        // 1. Read Module 1
        ItemStack item0 = inventory.getItem(0);
        if (item0 != null && item0.getType() != Material.AIR && isModuleItem(item0)) {
            String id = getCustomItemId(item0);
            data.setModule1Id(id);
            double eff = getEfficiency(item0);
            data.setModule1Efficiency(eff);
        } else {
            data.setModule1Id(null);
            data.setModule1Efficiency(100.0);
        }

        // 2. Read Module 2
        ItemStack item1 = inventory.getItem(1);
        if (item1 != null && item1.getType() != Material.AIR && isModuleItem(item1)) {
            String id = getCustomItemId(item1);
            data.setModule2Id(id);
            double eff = getEfficiency(item1);
            data.setModule2Efficiency(eff);
        } else {
            data.setModule2Id(null);
            data.setModule2Efficiency(100.0);
        }

        // 3. Read Battery (with durability & energy sync)
        ItemStack item2 = inventory.getItem(2);
        if (item2 != null && item2.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(item2)) {
            String type = RobotBatteryUtil.getBatteryType(item2);
            int batteryEnergy = RobotBatteryUtil.getBatteryEnergy(item2);
            if (type != null) {
                data.setBatteryType(type);
                data.setEnergy(batteryEnergy);
            } else {
                data.setBatteryType("none");
                data.setEnergy(0);
            }
        } else {
            data.setBatteryType("none");
            data.setEnergy(0);
        }

        robot.updateCustomName();
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.2f);
        player.sendMessage("§a§l[Robot] §eĐã cập nhật cấu hình Module và Pin cho Robot thành công!");
    }

    private boolean isModuleItem(ItemStack item) {
        String id = getCustomItemId(item);
        return id != null && id.contains("haohan:robot_module_");
    }

    private boolean isBatteryItem(ItemStack item) {
        return RobotBatteryUtil.isBatteryItem(item);
    }

    private String getCustomItemId(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            if (id != null) return id;
        } catch (Throwable ignored) {}
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id1 = pdc.get(new NamespacedKey("haohanitemcore", "item_id"), PersistentDataType.STRING);
        if (id1 != null) return id1;
        return pdc.get(new NamespacedKey("haohan", "item_id"), PersistentDataType.STRING);
    }

    private double getEfficiency(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 100.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Double eff = pdc.get(new NamespacedKey("haohanitemcore", "module_efficiency"), PersistentDataType.DOUBLE);
        if (eff == null) {
            eff = pdc.get(new NamespacedKey("haohan", "module_efficiency"), PersistentDataType.DOUBLE);
        }
        return eff != null ? eff : 100.0;
    }

    private void applyEfficiencyLore(ItemStack item, double eff) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey("haohanitemcore", "module_efficiency"), PersistentDataType.DOUBLE, eff);
            pdc.set(new NamespacedKey("haohan", "module_efficiency"), PersistentDataType.DOUBLE, eff);
            List<String> lore = meta.getLore();
            if (lore != null) {
                lore.removeIf(line -> line.contains("Độ hiệu quả:"));
                lore.add("§7Độ hiệu quả: §a" + String.format("%.1f", eff) + "%");
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
    }
}
