package vn.haohan.lunar.charger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BatteryChargerUi implements InventoryHolder, Listener {

    public static final int SLOT_BATTERY = 11;
    public static final int SLOT_STATUS = 13;
    public static final int SLOT_FUEL = 15;

    private final Plugin plugin;
    private final BatteryChargerMechanic mechanic;
    private final BatteryChargerStation station;
    private final Player player;
    private final Inventory inventory;

    public BatteryChargerUi(Plugin plugin, BatteryChargerMechanic mechanic, BatteryChargerStation station, Player player) {
        this.plugin = plugin;
        this.mechanic = mechanic;
        this.station = station;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, "§3§lTrạm Sạc Pin Robot");
        setupInventory();
    }

    private void setupInventory() {
        // Border Panes
        ItemStack cyanBorder = createGuiItem(Material.CYAN_STAINED_GLASS_PANE, "§b[Trạm Sạc]", List.of());
        ItemStack grayBorder = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());

        for (int i = 0; i < 27; i++) {
            if (i == SLOT_BATTERY || i == SLOT_STATUS || i == SLOT_FUEL) continue;
            if (i == 0 || i == 8 || i == 18 || i == 26) {
                inventory.setItem(i, cyanBorder);
            } else if (i == 10) {
                inventory.setItem(i, createGuiItem(Material.HOPPER, "§b§l[Khoang Đặt Pin] §e➔", List.of(
                        "§7Đặt Pin Robot vào ô bên cạnh để nạp.",
                        "§7Nhận: Pin Nhỏ, Vừa, Lớn."
                )));
            } else if (i == 12) {
                inventory.setItem(i, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "§a⚡ Dòng Điện ➔", List.of("§7Năng lượng nạp vào pin.")));
            } else if (i == 14) {
                inventory.setItem(i, createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "§6⬅ Tiếp Năng Lượng", List.of("§7Nguồn nhiên liệu xúc tác.")));
            } else if (i == 16) {
                inventory.setItem(i, createGuiItem(Material.BLAST_FURNACE, "§6§l[Khoang Nhiên Liệu]", List.of(
                        "§7Đặt nguyên liệu để tạo năng lượng sạc:",
                        "§c▪ Đá đỏ: §f+500 EU",
                        "§c▪ Khối Đá đỏ: §f+4,500 EU",
                        "§e▪ Bột Kreep: §f+2,000 EU",
                        "§b▪ Quặng Ilmenite: §f+3,000 EU",
                        "§8▪ Than/Than củi: §f+200 EU",
                        "§c⚠ Bắt buộc có nhiên liệu trạm mới hoạt động!",
                        "§e⚡ Tốc độ nạp tối ưu dựa theo địa hình xung quanh."
                )));
            } else {
                inventory.setItem(i, grayBorder);
            }
        }

        // Populate existing battery in station if any
        if (station.getBatteryItem() != null && station.getBatteryItem().getType() != Material.AIR) {
            inventory.setItem(SLOT_BATTERY, station.getBatteryItem());
        }

        // Populate existing fuel in station if any
        if (station.getFuelItem() != null && station.getFuelItem().getType() != Material.AIR) {
            inventory.setItem(SLOT_FUEL, station.getFuelItem());
        }

        updateStatusCore();
    }

    public void open() {
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.3f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public BatteryChargerStation getStation() {
        return station;
    }

    public Player getPlayer() {
        return player;
    }

    private int flowAnimationStep = 0;

    public void updateUi() {
        boolean isCharging = false;
        ItemStack bat = inventory.getItem(SLOT_BATTERY);
        if (bat != null && bat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(bat)) {
            int cap = RobotBatteryUtil.getCapacity(RobotBatteryUtil.getBatteryType(bat));
            int energy = RobotBatteryUtil.getBatteryEnergy(bat);
            if (energy < cap) {
                isCharging = true;
            }
        }
        updateFlowIndicators(isCharging);
        updateStatusCore();
    }

    /**
     * Called every charging tick (every 5 ticks = 0.25s) when this UI is open.
     * Charges the battery in Slot 11 in real-time and refreshes all visual gauges.
     */
    public boolean tickCharging() {
        ItemStack bat = inventory.getItem(SLOT_BATTERY);
        boolean isCharging = false;

        // Auto-consume fuel from slot 15 if station buffer is running low
        if (station.getFuelBuffer() < 5000) {
            processFuel();
        }

        double multiplier = station.getCachedMultiplier();
        int chargeRate = station.getCachedChargeRate();
        if (chargeRate <= 0) {
            multiplier = BatteryChargerMechanic.calculateConditionMultiplier(station.getLocation());
            chargeRate = BatteryChargerMechanic.calculateChargeRate(multiplier);
            station.setCachedMultiplier(multiplier);
            station.setCachedChargeRate(chargeRate);
        }

        if (bat != null && bat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(bat)) {
            String type = RobotBatteryUtil.getBatteryType(bat);
            int cap = RobotBatteryUtil.getCapacity(type);
            int current = RobotBatteryUtil.getBatteryEnergy(bat);

            // STRICT: Must have fuelBuffer > 0 to charge! No free energy.
            if (current < cap && station.getFuelBuffer() > 0) {
                int cycleCharge = Math.max(1, chargeRate / 4); // 4 cycles per sec
                int needed = cap - current;
                int actualCharge = Math.min(cycleCharge, Math.min(needed, station.getFuelBuffer()));

                if (actualCharge > 0) {
                    station.setFuelBuffer(station.getFuelBuffer() - actualCharge);
                    int nextEnergy = current + actualCharge;
                    RobotBatteryUtil.setBatteryEnergy(bat, nextEnergy);
                    // Send slot update packet to player so durability bar & lore advance in real-time
                    inventory.setItem(SLOT_BATTERY, bat);
                    station.setBatteryItem(bat.clone());
                    isCharging = true;

                    if (nextEnergy >= cap) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
                        player.sendMessage("§e§l[Trạm Sạc] §a✔ Pin Robot đã được nạp đầy 100%!");
                    } else {
                        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.2f, 1.9f);
                    }
                }
            } else {
                station.setBatteryItem(bat.clone());
            }
        } else {
            station.setBatteryItem(null);
        }

        updateFlowIndicators(isCharging);
        updateStatusCore();
        return isCharging;
    }

    private void updateFlowIndicators(boolean isCharging) {
        flowAnimationStep++;
        if (isCharging) {
            if (flowAnimationStep % 2 == 0) {
                inventory.setItem(12, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "§a⚡ Dòng Điện Đang Nạp ➔", List.of(
                        "§7Năng lượng đang được truyền vào pin...",
                        "§a⚡ Cập nhật thời gian thực"
                )));
            } else {
                inventory.setItem(12, createGuiItem(Material.YELLOW_STAINED_GLASS_PANE, "§e⚡ Dòng Điện Đang Nạp ➔", List.of(
                        "§7Năng lượng đang được truyền vào pin...",
                        "§a⚡ Cập nhật thời gian thực"
                )));
            }
        } else {
            String reason = station.getFuelBuffer() <= 0
                    ? "§cTrạm sạc đang hết nhiên liệu!"
                    : "§7Không có pin hoặc pin đã đầy 100%.";
            inventory.setItem(12, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "§7⚡ Dòng Điện Tạm Dừng ➔", List.of(
                    reason
            )));
        }

        if (station.getFuelBuffer() > 0) {
            inventory.setItem(14, createGuiItem(Material.ORANGE_STAINED_GLASS_PANE, "§6⬅ Tiếp Năng Lượng (Kích Hoạt)", List.of(
                    "§7Dự trữ: §f" + String.format("%,d", station.getFuelBuffer()) + " EU",
                    "§eTrạm đang có nhiên liệu để phát điện!"
            )));
        } else {
            inventory.setItem(14, createGuiItem(Material.GRAY_STAINED_GLASS_PANE, "§8⬅ Tiếp Năng Lượng (Trống)", List.of(
                    "§cChưa có nhiên liệu!",
                    "§7Đặt nguyên liệu vào ô bên phải",
                    "§7để kích hoạt trạm sạc."
            )));
        }
    }

    private void processFuel() {
        ItemStack fuelItem = inventory.getItem(SLOT_FUEL);
        if (fuelItem == null || fuelItem.getType() == Material.AIR) {
            station.setFuelItem(null);
            return;
        }

        int value = getFuelValue(fuelItem);
        if (value > 0) {
            station.addFuelBuffer(value);
            fuelItem.subtract(1);
            if (fuelItem.getAmount() <= 0) {
                inventory.setItem(SLOT_FUEL, null);
                station.setFuelItem(null);
            } else {
                station.setFuelItem(fuelItem.clone());
            }
            player.playSound(player.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.8f, 1.4f);
        }
    }

    public static int getFuelValue(ItemStack item) {
        return BatteryChargerMechanic.getFuelValue(item);
    }

    private void updateStatusCore() {
        double mult = station.getCachedMultiplier();
        int rate = station.getCachedChargeRate();
        ItemStack bat = inventory.getItem(SLOT_BATTERY);

        if (bat == null || bat.getType() == Material.AIR || !RobotBatteryUtil.isBatteryItem(bat)) {
            inventory.setItem(SLOT_STATUS, createGuiItem(Material.RED_STAINED_GLASS_PANE, "§c§l[CHƯA CÓ PIN]", List.of(
                    "§7Hãy đặt Pin Robot vào khoang bên trái để nạp.",
                    "§7Hỗ trợ Pin Nhỏ (5k), Vừa (15k), Lớn (30k).",
                    "",
                    "§6⚙ Hệ số địa hình: §e" + String.format(Locale.ROOT, "x%.2f", mult),
                    "§e⚡ Công suất tối ưu: §f" + String.format("%,d", rate) + " EU/s",
                    "§b⚡ Dự trữ nhiên liệu trạm: §f" + String.format("%,d", station.getFuelBuffer()) + " EU"
            )));
            return;
        }

        String type = RobotBatteryUtil.getBatteryType(bat);
        int cap = RobotBatteryUtil.getCapacity(type);
        int energy = RobotBatteryUtil.getBatteryEnergy(bat);
        int pct = cap > 0 ? (int) Math.round(((double) energy / cap) * 100.0) : 0;

        String bar = buildProgressBar(pct);

        if (energy >= cap) {
            inventory.setItem(SLOT_STATUS, createGuiItem(Material.EMERALD, "§a§l✔ ĐÃ NẠP ĐẦY 100%", List.of(
                    "§a▪ Loại Pin: §f" + type.toUpperCase(),
                    "§a▪ Dung lượng: §f" + String.format("%,d", cap) + " / " + String.format("%,d", cap) + " EU",
                    "§8" + bar,
                    "",
                    "§6⚙ Hệ số địa hình: §e" + String.format(Locale.ROOT, "x%.2f", mult),
                    "§7Pin đã đạt công suất tối đa!",
                    "§eNhấp vào pin bên trái để lấy ra sử dụng."
            )));
        } else if (station.getFuelBuffer() <= 0) {
            inventory.setItem(SLOT_STATUS, createGuiItem(Material.RED_STAINED_GLASS_PANE, "§c§l⚡ TẠM DỪNG: HẾT NHIÊN LIỆU", List.of(
                    "§a▪ Loại Pin: §f" + type.toUpperCase(),
                    "§e▪ Tiến trình: §f" + String.format("%,d", energy) + " §7/ §f" + String.format("%,d", cap) + " EU §e(" + pct + "%)",
                    "§8" + bar,
                    "",
                    "§c▪ Trạm sạc đã cạn kiệt nhiên liệu!",
                    "§7Hãy đặt Đá đỏ / Than / Quặng vào khoang bên phải.",
                    "§6⚙ Hệ số địa hình: §e" + String.format(Locale.ROOT, "x%.2f", mult),
                    "§c⚡ Tốc độ nạp: §c0 EU/s (Cần nhiên liệu)"
            )));
        } else {
            inventory.setItem(SLOT_STATUS, createGuiItem(Material.DAYLIGHT_DETECTOR, "§e§l⚡ ĐANG NẠP NĂNG LƯỢNG...", List.of(
                    "§a▪ Loại Pin: §f" + type.toUpperCase(),
                    "§e▪ Tiến trình: §f" + String.format("%,d", energy) + " §7/ §f" + String.format("%,d", cap) + " EU §e(" + pct + "%)",
                    "§8" + bar,
                    "",
                    "§6⚙ Hệ số địa hình: §e" + String.format(Locale.ROOT, "x%.2f", mult),
                    "§a⚡ Tốc độ nạp: §f+" + String.format("%,d", rate) + " EU/s",
                    "§b⚡ Dự trữ nhiên liệu: §f" + String.format("%,d", station.getFuelBuffer()) + " EU",
                    "§7(Sạc trực tiếp trong trạm nhanh gấp 5x so với sạc robot)"
            )));
        }
    }

    private String buildProgressBar(int pct) {
        int totalBars = 16;
        int filled = Math.max(0, Math.min(totalBars, (int) Math.round((pct / 100.0) * totalBars)));
        StringBuilder sb = new StringBuilder("§a[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filled) sb.append("█");
            else sb.append("§8░");
        }
        sb.append("§a] §f").append(pct).append("%");
        return sb.toString();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) return;

        int rawSlot = event.getRawSlot();

        // Locked slots
        if (rawSlot >= 0 && rawSlot < 27) {
            if (rawSlot != SLOT_BATTERY && rawSlot != SLOT_FUEL) {
                event.setCancelled(true);
                return;
            }

            // In battery slot: only allow valid batteries
            if (rawSlot == SLOT_BATTERY) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (!RobotBatteryUtil.isBatteryItem(cursor)) {
                        event.setCancelled(true);
                        player.sendMessage("§c§l[Trạm Sạc] §cKhoang này chỉ nhận Pin Robot!");
                        return;
                    }
                }
            }

            // In fuel slot: only allow valid fuels
            if (rawSlot == SLOT_FUEL) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (getFuelValue(cursor) <= 0) {
                        event.setCancelled(true);
                        player.sendMessage("§c§l[Trạm Sạc] §cVật phẩm này không thể làm nhiên liệu sạc!");
                        return;
                    }
                }
            }
        }

        // Shift click from player inventory into charger
        if (event.isShiftClick() && rawSlot >= 27) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            event.setCancelled(true);
            if (RobotBatteryUtil.isBatteryItem(clicked)) {
                ItemStack curBat = inventory.getItem(SLOT_BATTERY);
                if (curBat == null || curBat.getType() == Material.AIR) {
                    inventory.setItem(SLOT_BATTERY, clicked.clone());
                    clicked.setAmount(0);
                    station.setBatteryItem(inventory.getItem(SLOT_BATTERY));
                    updateUi();
                } else {
                    player.sendMessage("§c§l[Trạm Sạc] §cKhoang đặt pin đã có pin!");
                }
            } else if (getFuelValue(clicked) > 0) {
                ItemStack curFuel = inventory.getItem(SLOT_FUEL);
                if (curFuel == null || curFuel.getType() == Material.AIR) {
                    inventory.setItem(SLOT_FUEL, clicked.clone());
                    clicked.setAmount(0);
                    updateUi();
                } else if (curFuel.isSimilar(clicked)) {
                    int space = curFuel.getMaxStackSize() - curFuel.getAmount();
                    int move = Math.min(space, clicked.getAmount());
                    if (move > 0) {
                        curFuel.setAmount(curFuel.getAmount() + move);
                        clicked.subtract(move);
                        updateUi();
                    }
                }
            }
            return;
        }

        // Hotbar number key
        if (event.getClick() == ClickType.NUMBER_KEY && (rawSlot == SLOT_BATTERY || rawSlot == SLOT_FUEL)) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                    if (rawSlot == SLOT_BATTERY && !RobotBatteryUtil.isBatteryItem(hotbarItem)) {
                        event.setCancelled(true);
                        return;
                    }
                    if (rawSlot == SLOT_FUEL && getFuelValue(hotbarItem) <= 0) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // Delay UI refresh slightly after click
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack curBat = inventory.getItem(SLOT_BATTERY);
            if (curBat != null && curBat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(curBat)) {
                station.setBatteryItem(curBat.clone());
            } else {
                station.setBatteryItem(null);
            }
            ItemStack curFuel = inventory.getItem(SLOT_FUEL);
            if (curFuel != null && curFuel.getType() != Material.AIR && getFuelValue(curFuel) > 0) {
                station.setFuelItem(curFuel.clone());
            } else {
                station.setFuelItem(null);
            }
            updateUi();
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) return;

        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < 27 && slot != SLOT_BATTERY && slot != SLOT_FUEL) {
                event.setCancelled(true);
                return;
            }
            if (slot == SLOT_BATTERY && !RobotBatteryUtil.isBatteryItem(event.getOldCursor())) {
                event.setCancelled(true);
                return;
            }
            if (slot == SLOT_FUEL && getFuelValue(event.getOldCursor()) <= 0) {
                event.setCancelled(true);
                return;
            }
        }
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack curBat = inventory.getItem(SLOT_BATTERY);
            if (curBat != null && curBat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(curBat)) {
                station.setBatteryItem(curBat.clone());
            } else {
                station.setBatteryItem(null);
            }
            ItemStack curFuel = inventory.getItem(SLOT_FUEL);
            if (curFuel != null && curFuel.getType() != Material.AIR && getFuelValue(curFuel) > 0) {
                station.setFuelItem(curFuel.clone());
            } else {
                station.setFuelItem(null);
            }
            updateUi();
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;

        // 1. Sync battery item to station
        ItemStack bat = inventory.getItem(SLOT_BATTERY);
        if (bat != null && bat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(bat)) {
            station.setBatteryItem(bat.clone());
        } else {
            station.setBatteryItem(null);
        }

        // 2. Keep fuel item inside station so it stays stored and auto-fuels in background
        ItemStack fuel = inventory.getItem(SLOT_FUEL);
        if (fuel != null && fuel.getType() != Material.AIR && getFuelValue(fuel) > 0) {
            station.setFuelItem(fuel.clone());
        } else {
            station.setFuelItem(null);
        }

        mechanic.unregisterOpenUi(this);
        org.bukkit.event.HandlerList.unregisterAll(this);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.3f);
    }

    private static ItemStack createGuiItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> compLore = lore.stream()
                        .map(line -> (Component) LegacyComponentSerializer.legacySection().deserialize(line))
                        .toList();
                meta.lore(compLore);
            }
            item.setItemMeta(meta);
        }
        return item;
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
