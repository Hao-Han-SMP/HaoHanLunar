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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.robot.LunarRobotEntity;
import vn.haohan.lunar.robot.LunarRobotMechanic;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BatteryChargerMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private final Map<String, BatteryChargerStation> stations = new ConcurrentHashMap<>();
    private final NamespacedKey chargerKey;
    private long tickCounter = 0;

    public BatteryChargerMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        this.chargerKey = new NamespacedKey("haohan", "battery_charger_station");
        loadAll();
    }

    public Map<String, BatteryChargerStation> getStations() {
        return stations;
    }

    /**
     * Checks if the held tool satisfies the Diamond tier pickaxe requirement for mining Battery Charger Stations.
     * Supports both custom metallurgy pickaxes resolved via ToolTierRegistry and vanilla Diamond/Netherite pickaxes.
     */
    public static boolean isAllowedChargerPickaxe(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return false;
        }

        try {
            var core = HaoHanItemCore.get();
            if (core != null) {
                // Check ToolType
                vn.haohan.itemcore.api.block.ToolType toolType = null;
                if (core.getItemService() != null) {
                    String id = core.getItemService().getId(tool);
                    if (id != null) {
                        var def = core.getItemService().getDefinition(id);
                        if (def != null && def.getProperties() != null) {
                            Object tt = def.getProperties().get("tool_type");
                            if (tt instanceof String s) {
                                toolType = vn.haohan.itemcore.api.block.ToolType.valueOf(s.toUpperCase(Locale.ROOT));
                            }
                        }
                    }
                }
                if (toolType == null) {
                    toolType = vn.haohan.itemcore.api.block.ToolType.fromMaterial(tool.getType());
                }
                if (toolType != vn.haohan.itemcore.api.block.ToolType.PICKAXE) {
                    return false;
                }

                // Check ToolTier
                if (core.getToolTierRegistry() != null) {
                    var tierRegistry = core.getToolTierRegistry();
                    var toolTier = tierRegistry.findTier(tool);
                    if (toolTier != null) {
                        var diamondTier = tierRegistry.get("diamond").orElse(vn.haohan.itemcore.api.block.ToolTier.DIAMOND);
                        return toolTier.level() >= diamondTier.level();
                    }
                }
            }
        } catch (Throwable ignored) {}

        Material mat = tool.getType();
        return mat == Material.DIAMOND_PICKAXE || mat == Material.NETHERITE_PICKAXE;
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
                    if (player.getGameMode() != GameMode.CREATIVE && !isAllowedChargerPickaxe(player.getInventory().getItemInMainHand())) {
                        player.sendMessage("§c§l[Trạm Sạc] §cBạn cần cúp từ cấp độ §bKim Cương§c trở lên để đào Trạm Sạc!");
                        player.playSound(clickedBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
                        return;
                    }
                    breakStation(player, clickedBlock, station);
                    return;
                }

                // RIGHT CLICK -> Feed Raw Anorthosite or View Info
                if (action == Action.RIGHT_CLICK_BLOCK) {
                    ItemStack handItem = player.getInventory().getItemInMainHand();

                    // Fuel: ONLY raw_anorthosite (+3500 EU / 4s)
                    if (isRawAnorthosite(handItem)) {
                        int amountToFeed = player.isSneaking() ? handItem.getAmount() : 1;
                        if (amountToFeed > 0) {
                            station.addFuelBuffer(amountToFeed * 3500);
                            if (player.getGameMode() != GameMode.CREATIVE) {
                                if (handItem.getAmount() > amountToFeed) {
                                    handItem.subtract(amountToFeed);
                                } else {
                                    player.getInventory().setItemInMainHand(null);
                                }
                            }
                            player.playSound(clickedBlock.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.8f, 1.4f);
                            player.sendMessage("§e§l[Trạm Sạc] §aĐã nạp §f" + amountToFeed + "x §aRaw Anorthosite! §e(+ " + String.format("%,d", amountToFeed * 3500) + " EU)");
                            updateStationDisplay(station);
                            saveAll();
                            return;
                        }
                    }

                    // Status report on click without raw anorthosite
                    String statusName = switch (station.getCurrentState()) {
                        case RED -> "§cKhông khả dụng (Cạn năng lượng)";
                        case BLUE -> "§9Sẵn sàng (Đang chờ Robot)";
                        case YELLOW -> "§eĐang sạc Robot";
                        case CYAN -> "§bRobot đã sạc đầy";
                    };
                    player.sendMessage("§9§l[Trạm Sạc]");
                    player.sendMessage("§7▪ Trạng thái: " + statusName);
                    player.sendMessage("§7▪ Năng lượng dự trữ: §f" + String.format("%,d", station.getFuelBuffer()) + " EU");
                    player.sendMessage("§8Cầm Raw Anorthosite bấm chuột phải để tiếp năng lượng (+3,500 EU / 4s).");
                    player.playSound(clickedBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.6f);
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
                Block targetBlock = clickedBlock.isReplaceable() ? clickedBlock : clickedBlock.getRelative(face);

                if (targetBlock.getY() < targetBlock.getWorld().getMinHeight() || targetBlock.getY() >= targetBlock.getWorld().getMaxHeight()) {
                    return;
                }

                org.bukkit.util.BoundingBox targetBox = org.bukkit.util.BoundingBox.of(targetBlock).expand(-0.05);
                if (player.getBoundingBox().overlaps(targetBox)) {
                    player.sendMessage("§c§l[Trạm Sạc] §cBạn đang đứng quá gần vị trí đặt!");
                    return;
                }

                Block ground = targetBlock.getRelative(BlockFace.DOWN);
                if (!ground.getType().isSolid()) {
                    player.sendMessage("§c§l[Trạm Sạc] §cPhải đặt Trạm Sạc trên một bề mặt vững chắc!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }

                // Check 3x3x2 space requirement
                if (!is3x3x2AreaClear(targetBlock.getLocation(), targetBlock)) {
                    player.sendMessage("§c§l[Trạm Sạc] §cKhông đủ không gian để đặt! Trạm sạc yêu cầu khoảng trống 3x3x2.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return;
                }

                placeStation(player, targetBlock, itemInHand);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isChargerBlock(block)) {
            String key = formatKey(block.getLocation());
            BatteryChargerStation station = stations.get(key);
            if (station != null) {
                Player player = event.getPlayer();
                if (player.getGameMode() != GameMode.CREATIVE && !isAllowedChargerPickaxe(player.getInventory().getItemInMainHand())) {
                    event.setCancelled(true);
                    player.sendMessage("§c§l[Trạm Sạc] §cBạn cần cúp từ cấp độ §bKim Cương§c trở lên để đào Trạm Sạc!");
                    player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
                    return;
                }
                event.setCancelled(true);
                breakStation(player, block, station);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        Location pLoc = placed.getLocation();
        World w = pLoc.getWorld();
        if (w == null) return;
        int px = pLoc.getBlockX();
        int py = pLoc.getBlockY();
        int pz = pLoc.getBlockZ();

        for (BatteryChargerStation station : stations.values()) {
            Location sLoc = station.getLocation();
            if (sLoc == null || !w.equals(sLoc.getWorld())) continue;
            int sx = sLoc.getBlockX();
            int sy = sLoc.getBlockY();
            int sz = sLoc.getBlockZ();

            // Exclude the station's own barrier block
            if (px == sx && py == sy && pz == sz) continue;

            // 3x3x2 Bounding Box Protection: dx in [-1, 1], dz in [-1, 1], dy in [0, 1]
            if (Math.abs(px - sx) <= 1 && Math.abs(pz - sz) <= 1 && (py == sy || py == sy + 1)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c§l[Trạm Sạc] §cKhông thể đặt khối trong vùng hoạt động 3x3x2 của Trạm Sạc!");
                return;
            }
        }
    }

    public static boolean is3x3x2AreaClear(Location center, Block placedBlock) {
        if (center == null || center.getWorld() == null) return false;
        World w = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;
                    Block b = w.getBlockAt(bx, by, bz);
                    if (placedBlock != null && b.getLocation().equals(placedBlock.getLocation())) {
                        continue;
                    }
                    if (!b.getType().isAir() && !b.isReplaceable()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void placeStation(Player player, Block block, ItemStack itemInHand) {
        Location loc = block.getLocation();
        String key = formatKey(loc);

        // 0. Defense-in-depth: Clear any residual custom block storage entry from ItemCore if previously registered
        try {
            if (HaoHanItemCore.get() != null && HaoHanItemCore.get().getCustomBlockService() != null) {
                HaoHanItemCore.get().getCustomBlockService().removeCustomBlock(loc);
            }
        } catch (Throwable ignored) {}

        // 1. Place Barrier Block
        block.setType(Material.BARRIER);

        // 2. Calculate snap yaw facing the player
        final float finalYaw = Math.round((player.getLocation().getYaw() + 180.0f) / 90.0f) * 90.0f;

        // 3. Spawn 3D ItemDisplay: initially RED because fuelBuffer is 0
        Location displayLoc = loc.clone().add(0.5, 0.5, 0.5);
        ItemDisplay display = loc.getWorld().spawn(displayLoc, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);

            ItemStack modelStack = createDisplayItemStack("haohan:robot_charger_red");
            if (modelStack != null) {
                entity.setItemStack(modelStack);
            }
            entity.setRotation(finalYaw, 0.0f);
            entity.getPersistentDataContainer().set(chargerKey, PersistentDataType.STRING, key);
        });

        // 4. Register Station
        BatteryChargerStation station = new BatteryChargerStation(loc, display.getUniqueId(), player.getUniqueId(), finalYaw);
        station.setCurrentState(BatteryChargerStation.ChargerState.RED);
        stations.put(key, station);
        saveAll();

        // 5. Consume item
        if (player.getGameMode() != GameMode.CREATIVE) {
            if (itemInHand.getAmount() > 1) {
                itemInHand.subtract(1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        }

        // 6. Effects
        try {
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.4f);
            loc.getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(0.5, 0.6, 0.5), 15, 0.3, 0.3, 0.3, 0.05);
        } catch (Throwable ignored) {}
        if (player != null) {
            player.sendMessage("§9§l[Trạm Sạc] §aĐã đặt Trạm Sạc thành công! Hãy nạp Raw Anorthosite để kích hoạt.");
        }
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

        // Defense-in-depth: Ensure any residual custom block storage entry from ItemCore is purged
        try {
            if (HaoHanItemCore.get() != null && HaoHanItemCore.get().getCustomBlockService() != null) {
                HaoHanItemCore.get().getCustomBlockService().removeCustomBlock(loc);
            }
        } catch (Throwable ignored) {}

        // 4. Drop Station Item (Always drop base blue robot_charger)
        var itemFactory = HaoHanItemCore.get().getItemFactory();
        ItemStack dropCharger = itemFactory.create("haohan:robot_charger", 1);
        if (dropCharger != null && player.getGameMode() != GameMode.CREATIVE) {
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.3, 0.5), dropCharger);
        }

        // 5. Refund remaining raw anorthosite fuel if any
        if (station.getFuelBuffer() >= 3500 && player.getGameMode() != GameMode.CREATIVE) {
            int returnCount = station.getFuelBuffer() / 3500;
            ItemStack raw = itemFactory.create("haohan:raw_anorthosite", returnCount);
            if (raw != null) {
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), raw);
            }
        }

        // 6. Effects
        try {
            loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_BREAK, 0.8f, 1.2f);
            loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0.5, 0.5, 0.5), 25, 0.4, 0.4, 0.4, Material.IRON_BLOCK.createBlockData());
        } catch (Throwable ignored) {}
        if (player != null) {
            player.sendMessage("§9§l[Trạm Sạc] §cĐã thu hồi Trạm Sạc.");
        }
    }

    public void tick() {
        tickCounter++;
        // Run charging logic every 5 ticks (0.25s) for responsive real-time animation
        if (tickCounter % 5 != 0) return;

        for (BatteryChargerStation station : stations.values()) {
            Location loc = station.getLocation();
            if (loc == null || loc.getWorld() == null || !loc.isChunkLoaded()) continue;

            ensureDisplayEntity(station);

            // Handle Cyan display duration countdown
            if (station.getCyanDisplayTicks() > 0) {
                station.setCyanDisplayTicks(station.getCyanDisplayTicks() - 5);
            }

            // Verify docked robot validity
            if (station.getDockedRobotUuid() != null) {
                Entity robotEnt = Bukkit.getEntity(station.getDockedRobotUuid());
                if (robotEnt == null || !robotEnt.isValid() || robotEnt.isDead() || robotEnt.getLocation().distanceSquared(loc.clone().add(0.5, 0.5, 0.5)) > 9.0) {
                    station.setDockedRobotUuid(null);
                }
            }

            // Update 4-state visual display
            updateStationDisplay(station);

            // Ambient particles
            switch (station.getCurrentState()) {
                case YELLOW -> loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0.5, 0.7, 0.5), 3, 0.2, 0.2, 0.2, 0.05);
                case BLUE -> {
                    if (tickCounter % 20 == 0) {
                        loc.getWorld().spawnParticle(Particle.WAX_ON, loc.clone().add(0.5, 0.6, 0.5), 2, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case CYAN -> {
                    if (tickCounter % 10 == 0) {
                        loc.getWorld().spawnParticle(Particle.WAX_OFF, loc.clone().add(0.5, 0.6, 0.5), 3, 0.25, 0.25, 0.25, 0.02);
                    }
                }
                case RED -> {
                    if (tickCounter % 40 == 0) {
                        loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0.5, 0.7, 0.5), 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }
            }
        }
    }

    public void updateStationDisplay(BatteryChargerStation station) {
        if (station == null) return;
        BatteryChargerStation.ChargerState targetState;

        if (station.getDockedRobotUuid() != null) {
            targetState = BatteryChargerStation.ChargerState.YELLOW;
        } else if (station.getCyanDisplayTicks() > 0) {
            targetState = BatteryChargerStation.ChargerState.CYAN;
        } else if (station.getFuelBuffer() > 0) {
            targetState = BatteryChargerStation.ChargerState.BLUE;
        } else {
            targetState = BatteryChargerStation.ChargerState.RED;
        }

        if (station.getCurrentState() != targetState) {
            station.setCurrentState(targetState);
        }

        if (station.getDisplayEntityUuid() != null) {
            Entity ent = Bukkit.getEntity(station.getDisplayEntityUuid());
            if (ent instanceof ItemDisplay display && display.isValid()) {
                ItemStack modelStack = createDisplayItemStack(targetState.getCustomItemId());
                if (modelStack != null) {
                    display.setItemStack(modelStack);
                }
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

            String itemId = station.getCurrentState() != null ? station.getCurrentState().getCustomItemId() : "haohan:robot_charger_red";
            ItemStack modelStack = createDisplayItemStack(itemId);
            if (modelStack != null) {
                entity.setItemStack(modelStack);
            }
            entity.setRotation(station.getYaw(), 0.0f);
            entity.getPersistentDataContainer().set(chargerKey, PersistentDataType.STRING, station.getKey());
        });
        station.setDisplayEntityUuid(display.getUniqueId());
    }

    public static ItemStack createDisplayItemStack(String customItemId) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            try {
                meta.setItemModel(NamespacedKey.fromString(customItemId));
            } catch (Throwable ignored) {}

            int cmd = switch (customItemId) {
                case "haohan:robot_charger_red" -> 6207;
                case "haohan:robot_charger_yellow" -> 6208;
                case "haohan:robot_charger_cyan" -> 6209;
                case "haohan:robot_charger_blue" -> 6210;
                default -> 6206;
            };
            try {
                meta.setCustomModelData(cmd);
            } catch (Throwable ignored) {}

            stack.setItemMeta(meta);
        }
        return stack;
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

    public boolean isChargerBlock(Block block) {
        if (block == null || block.getType() != Material.BARRIER) return false;
        return stations.containsKey(formatKey(block.getLocation()));
    }

    public boolean isChargerItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            if (id == null) return false;
            return id.equals("haohan:robot_charger")
                    || id.equals("haohan:robot_charger_blue");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRawAnorthosite(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            String id = HaoHanItemCore.get().getItemService().getId(item);
            return "haohan:raw_anorthosite".equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getFuelValue(ItemStack item) {
        if (isRawAnorthosite(item)) {
            return 3500;
        }
        return 0;
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
                st.setCurrentState(fuel > 0 ? BatteryChargerStation.ChargerState.BLUE : BatteryChargerStation.ChargerState.RED);

                stations.put(formatKey(loc), st);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse charger entry: " + e.getMessage());
            }
        }
    }

    public void cleanupOnDisable() {
        saveAll();
        for (BatteryChargerStation st : stations.values()) {
            if (st.getDisplayEntityUuid() != null) {
                Entity ent = Bukkit.getEntity(st.getDisplayEntityUuid());
                if (ent != null) ent.remove();
            }
            cleanupNearbyDisplayEntities(st.getLocation(), st.getKey());
        }
    }
}
