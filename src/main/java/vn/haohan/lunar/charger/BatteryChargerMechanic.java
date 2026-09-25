package vn.haohan.lunar.charger;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.robot.LunarRobotEntity;
import vn.haohan.lunar.robot.LunarRobotMechanic;
import vn.haohan.lunar.robot.battery.RobotBatteryUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BatteryChargerMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private final Map<String, BatteryChargerStation> stations = new ConcurrentHashMap<>();
    private final List<BatteryChargerUi> activeUis = new ArrayList<>();
    private final NamespacedKey chargerKey;
    private long tickCounter = 0;

    public BatteryChargerMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        this.chargerKey = new NamespacedKey(plugin, "battery_charger_station");
        loadAll();
    }

    public Map<String, BatteryChargerStation> getStations() {
        return stations;
    }

    public void registerOpenUi(BatteryChargerUi ui) {
        activeUis.add(ui);
    }

    public void unregisterOpenUi(BatteryChargerUi ui) {
        activeUis.remove(ui);
    }

    public BatteryChargerUi getOpenUi(BatteryChargerStation station) {
        for (BatteryChargerUi ui : activeUis) {
            if (ui.getStation() == station && ui.getPlayer().isOnline() && ui.getPlayer().getOpenInventory().getTopInventory() == ui.getInventory()) {
                return ui;
            }
        }
        return null;
    }

    public void openChargerUi(Player player, BatteryChargerStation station) {
        BatteryChargerUi existingUi = getOpenUi(station);
        if (existingUi != null && existingUi.getPlayer().isOnline() && !existingUi.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§c§l[Trạm Sạc] §cTrạm sạc này đang được người chơi " + existingUi.getPlayer().getName() + " sử dụng!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        BatteryChargerUi ui = new BatteryChargerUi(plugin, this, station, player);
        plugin.getServer().getPluginManager().registerEvents(ui, plugin);
        registerOpenUi(ui);
        ui.open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();

        // 1. Check if clicking on an existing Charger Block
        if (clickedBlock != null && isChargerBlock(clickedBlock)) {
            String key = formatKey(clickedBlock.getLocation());
            BatteryChargerStation station = stations.get(key);
            if (station != null) {
                event.setCancelled(true);

                // LEFT CLICK -> Break / Retrieve Station
                if (action == Action.LEFT_CLICK_BLOCK) {
                    breakStation(player, clickedBlock, station);
                    return;
                }

                // RIGHT CLICK -> Open GUI or Quick-insert battery
                if (action == Action.RIGHT_CLICK_BLOCK) {
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    if (player.isSneaking() && RobotBatteryUtil.isBatteryItem(handItem)) {
                        // Quick insert/swap
                        ItemStack currentBat = station.getBatteryItem();
                        station.setBatteryItem(handItem.clone());
                        if (player.getGameMode() != GameMode.CREATIVE) {
                            handItem.setAmount(0);
                        }
                        if (currentBat != null && currentBat.getType() != Material.AIR) {
                            player.getInventory().addItem(currentBat);
                        }
                        player.playSound(clickedBlock.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.6f);
                        player.sendMessage("§e§l[Trạm Sạc] §aĐã đặt Pin vào trạm sạc!");
                        return;
                    }

                    openChargerUi(player, station);
                    return;
                }
            }
        }

        // 2. Check if placing a new Charger Station
        if (action == Action.RIGHT_CLICK_BLOCK && clickedBlock != null) {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (isChargerItem(itemInHand)) {
                event.setCancelled(true);

                BlockFace face = event.getBlockFace();
                Block targetBlock = clickedBlock.getRelative(face);

                if (!targetBlock.getType().isAir() && !targetBlock.isReplaceable()) {
                    player.sendMessage("§c§l[Trạm Sạc] §cKhông có đủ không gian để đặt Trạm Sạc!");
                    return;
                }

                placeStation(player, targetBlock, itemInHand);
            }
        }
    }

    public void placeStation(Player player, Block block, ItemStack itemInHand) {
        Location loc = block.getLocation();
        String key = formatKey(loc);

        // 1. Place Barrier Block
        block.setType(Material.BARRIER);

        // 2. Calculate snap yaw facing the player
        final float finalYaw = Math.round((player.getLocation().getYaw() + 180.0f) / 90.0f) * 90.0f;

        // 3. Spawn 3D ItemDisplay
        Location displayLoc = loc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = loc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            var itemFactory = HaoHanItemCore.get().getItemFactory();
            ItemStack modelStack = itemFactory.create("haohan:battery_charger", 1);
            if (modelStack != null) {
                entity.setItemStack(modelStack);
            }
            entity.setRotation(finalYaw, 0.0f);
            entity.getPersistentDataContainer().set(chargerKey, PersistentDataType.STRING, key);
        });

        // 4. Register Station
        BatteryChargerStation station = new BatteryChargerStation(loc, display.getUniqueId(), player.getUniqueId(), finalYaw);
        stations.put(key, station);
        saveAll();

        // 5. Consume item
        if (player.getGameMode() != GameMode.CREATIVE) {
            itemInHand.subtract(1);
        }

        // 6. Effects
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.4f);
        loc.getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(0.5, 0.6, 0.5), 15, 0.3, 0.3, 0.3, 0.05);
        player.sendMessage("§e§l[Trạm Sạc] §aĐã đặt Trạm Sạc Pin Robot thành công!");
    }

    public void breakStation(Player player, Block block, BatteryChargerStation station) {
        Location loc = block.getLocation();
        String key = formatKey(loc);

        // 1. Remove from map
        stations.remove(key);
        saveAll();

        // 2. Remove Display Entity
        if (station.getDisplayEntityUuid() != null) {
            Entity ent = Bukkit.getEntity(station.getDisplayEntityUuid());
            if (ent != null) ent.remove();
        }
        cleanupNearbyDisplayEntities(loc, key);

        // 3. Restore block to air
        block.setType(Material.AIR);

        // 4. Drop Station Item & Battery
        var itemFactory = HaoHanItemCore.get().getItemFactory();
        ItemStack dropCharger = itemFactory.create("haohan:battery_charger", 1);
        if (dropCharger != null && player.getGameMode() != GameMode.CREATIVE) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.3, 0.5), dropCharger);
        }

        if (station.getBatteryItem() != null && station.getBatteryItem().getType() != Material.AIR) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), station.getBatteryItem());
        }

        // 5. Close any open UIs for this station
        for (BatteryChargerUi ui : new ArrayList<>(activeUis)) {
            if (ui.getStation() == station) {
                ui.getPlayer().closeInventory();
            }
        }

        // 6. Effects
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.2f);
        loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 25, 0.4, 0.4, 0.4, Material.IRON_BLOCK.createBlockData());
        player.sendMessage("§e§l[Trạm Sạc] §cĐã thu hồi Trạm Sạc Pin Robot.");
    }

    public void tick() {
        tickCounter++;
        // Run charging logic every 5 ticks (0.25s) for smooth real-time animation
        if (tickCounter % 5 != 0) return;

        LunarRobotMechanic robotMechanic = plugin.getLunarRobotMechanic();

        // Prune stale or closed UIs
        activeUis.removeIf(ui -> !ui.getPlayer().isOnline() || ui.getPlayer().getOpenInventory().getTopInventory() != ui.getInventory());

        for (BatteryChargerStation station : stations.values()) {
            Location loc = station.getLocation();
            if (loc == null || loc.getWorld() == null || !loc.isChunkLoaded()) continue;

            // Ensure ItemDisplay is alive
            ensureDisplayEntity(station);

            boolean hasChargingActivity = false;

            // 1. Check if station currently has an open UI
            BatteryChargerUi openUi = getOpenUi(station);
            if (openUi != null) {
                // Charge directly inside the open UI in real-time
                boolean charged = openUi.tickCharging();
                if (charged) {
                    hasChargingActivity = true;
                }
            } else {
                // Background passive charge for closed station
                ItemStack bat = station.getBatteryItem();
                if (bat != null && bat.getType() != Material.AIR && RobotBatteryUtil.isBatteryItem(bat)) {
                    String type = RobotBatteryUtil.getBatteryType(bat);
                    int cap = RobotBatteryUtil.getCapacity(type);
                    int current = RobotBatteryUtil.getBatteryEnergy(bat);

                    if (current < cap) {
                        int chargeAmount = 15; // Base passive ambient charge (+60 EU/s)
                        if (station.getFuelBuffer() > 0) {
                            int bonus = Math.min(35, station.getFuelBuffer());
                            chargeAmount += bonus;
                            station.setFuelBuffer(station.getFuelBuffer() - bonus);
                        }

                        boolean charged = RobotBatteryUtil.chargeBattery(bat, chargeAmount);
                        if (charged) {
                            hasChargingActivity = true;
                        }
                    }
                }
            }

            // 2. Wireless charging for nearby Quadruped Robot
            if (robotMechanic != null) {
                for (Entity entity : loc.getWorld().getNearbyEntities(loc, 2.8, 2.5, 2.8)) {
                    if (entity instanceof LivingEntity living && robotMechanic.isRobotAlive(living)) {
                        LunarRobotEntity robot = robotMechanic.getOrRegisterRobot(living);
                        if (robot != null && robot.getData().isTamed()) {
                            var data = robot.getData();
                            if (data.getBatteryType() != null && !"none".equals(data.getBatteryType())) {
                                int curE = data.getEnergy();
                                int maxE = data.getMaxEnergy();
                                if (curE < maxE) {
                                    int wirelessCharge = 10; // +40 EU/s
                                    if (station.getFuelBuffer() > 0) {
                                        int bonus = Math.min(20, station.getFuelBuffer());
                                        wirelessCharge += bonus;
                                        station.setFuelBuffer(station.getFuelBuffer() - bonus);
                                    }
                                    data.setEnergy(Math.min(maxE, curE + wirelessCharge));
                                    hasChargingActivity = true;

                                    // Electric arc between charger and robot
                                    Location beamStart = loc.clone().add(0.5, 0.9, 0.5);
                                    Location beamEnd = living.getLocation().add(0, 0.6, 0);
                                    spawnBeamParticles(beamStart, beamEnd);
                                }
                            }
                        }
                    }
                }
            }

            // Charging ambient particles
            if (hasChargingActivity) {
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0.5, 0.7, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
    }

    private void ensureDisplayEntity(BatteryChargerStation station) {
        Location loc = station.getLocation();
        if (station.getDisplayEntityUuid() != null) {
            Entity ent = Bukkit.getEntity(station.getDisplayEntityUuid());
            if (ent instanceof ItemDisplay && ent.isValid()) {
                return;
            }
        }

        // Respawn if missing
        Location displayLoc = loc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = loc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            var itemFactory = HaoHanItemCore.get().getItemFactory();
            ItemStack modelStack = itemFactory.create("haohan:battery_charger", 1);
            if (modelStack != null) {
                entity.setItemStack(modelStack);
            }
            entity.setRotation(station.getYaw(), 0.0f);
            entity.getPersistentDataContainer().set(chargerKey, PersistentDataType.STRING, station.getKey());
        });
        station.setDisplayEntityUuid(display.getUniqueId());
    }

    private void cleanupNearbyDisplayEntities(Location loc, String key) {
        if (loc.getWorld() == null) return;
        for (ItemDisplay display : loc.getWorld().getEntitiesByClass(ItemDisplay.class)) {
            if (display.getLocation().distanceSquared(loc.clone().add(0.5, 0.5, 0.5)) < 2.0) {
                String k = display.getPersistentDataContainer().get(chargerKey, PersistentDataType.STRING);
                if (key.equals(k)) {
                    display.remove();
                }
            }
        }
    }

    private void spawnBeamParticles(Location start, Location end) {
        World w = start.getWorld();
        if (w == null) return;
        int points = 5;
        double dx = (end.getX() - start.getX()) / points;
        double dy = (end.getY() - start.getY()) / points;
        double dz = (end.getZ() - start.getZ()) / points;
        for (int i = 1; i <= points; i++) {
            w.spawnParticle(Particle.WAX_OFF, start.getX() + dx * i, start.getY() + dy * i, start.getZ() + dz * i, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public boolean isChargerBlock(Block block) {
        if (block == null || block.getType() != Material.BARRIER) return false;
        return stations.containsKey(formatKey(block.getLocation()));
    }

    public boolean isChargerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            return "haohan:battery_charger".equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    public String formatKey(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void saveAll() {
        File file = new File(plugin.getDataFolder(), "chargers.yml");
        YamlConfiguration config = new YamlConfiguration();

        List<Map<String, Object>> list = new ArrayList<>();
        for (BatteryChargerStation st : stations.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("world", st.getLocation().getWorld().getName());
            map.put("x", st.getLocation().getBlockX());
            map.put("y", st.getLocation().getBlockY());
            map.put("z", st.getLocation().getBlockZ());
            map.put("yaw", st.getYaw());
            map.put("fuelBuffer", st.getFuelBuffer());
            if (st.getOwnerUuid() != null) {
                map.put("owner", st.getOwnerUuid().toString());
            }
            if (st.getBatteryItem() != null && st.getBatteryItem().getType() != Material.AIR) {
                map.put("battery", st.getBatteryItem());
            }
            list.add(map);
        }
        config.set("chargers", list);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save chargers.yml: " + e.getMessage());
        }
    }

    public void loadAll() {
        stations.clear();
        File file = new File(plugin.getDataFolder(), "chargers.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> list = config.getMapList("chargers");

        for (Map<?, ?> map : list) {
            try {
                String worldName = (String) map.get("world");
                World w = Bukkit.getWorld(worldName);
                if (w == null) continue;

                int x = ((Number) map.get("x")).intValue();
                int y = ((Number) map.get("y")).intValue();
                int z = ((Number) map.get("z")).intValue();
                float yaw = map.containsKey("yaw") ? ((Number) map.get("yaw")).floatValue() : 0.0f;
                int fuel = map.containsKey("fuelBuffer") ? ((Number) map.get("fuelBuffer")).intValue() : 0;

                UUID owner = null;
                if (map.containsKey("owner")) {
                    owner = UUID.fromString((String) map.get("owner"));
                }

                Location loc = new Location(w, x, y, z);
                BatteryChargerStation st = new BatteryChargerStation(loc, null, owner, yaw);
                st.setFuelBuffer(fuel);

                if (map.containsKey("battery")) {
                    Object batObj = map.get("battery");
                    if (batObj instanceof ItemStack is) {
                        st.setBatteryItem(is);
                    }
                }

                stations.put(formatKey(loc), st);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse charger entry: " + e.getMessage());
            }
        }
    }

    public void cleanupOnDisable() {
        for (BatteryChargerUi ui : new ArrayList<>(activeUis)) {
            ui.getPlayer().closeInventory();
        }
        saveAll();
        // Remove all non-persistent ItemDisplays spawned for stations
        for (BatteryChargerStation st : stations.values()) {
            if (st.getDisplayEntityUuid() != null) {
                Entity ent = Bukkit.getEntity(st.getDisplayEntityUuid());
                if (ent != null) ent.remove();
            }
            cleanupNearbyDisplayEntities(st.getLocation(), st.getKey());
        }
        activeUis.clear();
    }
}
