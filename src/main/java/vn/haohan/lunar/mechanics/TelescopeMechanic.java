package vn.haohan.lunar.mechanics;

import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TelescopeMechanic implements Listener {

    private static class RevealedMarker {
        final ItemDisplay display;
        long expireTime;

        RevealedMarker(ItemDisplay display, long expireTime) {
            this.display = display;
            this.expireTime = expireTime;
        }
    }

    private final HaoHanLunarPlugin plugin;
    private final Map<UUID, Long> lastFailSound = new HashMap<>();
    private final Map<UUID, Long> lastRadarSound = new HashMap<>();

    // Theo dõi thời gian người chơi bắt đầu ngắm vào từng khối (cần ngắm >= 350ms)
    private final Map<UUID, Map<Block, Long>> playerFocusMap = new HashMap<>();

    // Danh sách các khối đã phát sáng thành công (tồn tại 5 - 10s kể cả khi hạ kính)
    private final Map<Block, RevealedMarker> revealedMarkers = new HashMap<>();

    // Danh sách các outline display tạm thời (fade trong 0.5s)
    private final List<ItemDisplay> temporaryOutlines = new ArrayList<>();

    private final BukkitTask scannerTask;

    public TelescopeMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        // Chạy quét mỗi 2 ticks (~0.1s) để phản hồi nhanh và chính xác
        this.scannerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanTick, 2L, 2L);
    }

    private void scanTick() {
        long now = System.currentTimeMillis();

        // 1. Quét tầm ngắm cho các người chơi đang dùng kính viễn vọng
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isAiming(player)) {
                scanPlayerFocus(player, now);
            } else {
                playerFocusMap.remove(player.getUniqueId());
            }
        }

        // 2. Cập nhật và dọn dẹp các marker đã hết hạn 5-10s hoặc khối bị phá hủy
        revealedMarkers.entrySet().removeIf(entry -> {
            Block b = entry.getKey();
            RevealedMarker marker = entry.getValue();
            boolean expired = now >= marker.expireTime;
            boolean blockDestroyed = b.getType() != Material.SUSPICIOUS_GRAVEL && b.getType() != Material.SUSPICIOUS_SAND;
            boolean invalid = !marker.display.isValid();

            if (expired || blockDestroyed || invalid) {
                if (marker.display.isValid()) {
                    marker.display.remove();
                }
                return true;
            }
            return false;
        });

        // Dọn dẹp outline đã không còn hợp lệ
        temporaryOutlines.removeIf(d -> !d.isValid());
    }

    private boolean isAiming(Player player) {
        if (!player.isHandRaised()) {
            return false;
        }
        var raisedSlot = player.getHandRaised();
        if (raisedSlot == null) {
            return false;
        }
        ItemStack item = player.getInventory().getItem(raisedSlot);
        if (item == null || item.getType() != Material.SPYGLASS) {
            return false;
        }
        var itemService = HaoHanItemCore.get().getItemService();
        return itemService.isItem(item, "haohan:telescope");
    }

    /**
     * Quét hình nón vét cạn 100% không góc chết từ 3.0 đến 90.0 blocks
     */
    private void scanPlayerFocus(Player player, long now) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        double eyeX = eye.getX();
        double eyeY = eye.getY();
        double eyeZ = eye.getZ();

        double dirX = dir.getX();
        double dirY = dir.getY();
        double dirZ = dir.getZ();

        int minHeight = world.getMinHeight();
        int maxHeight = world.getMaxHeight();

        Set<Block> visibleBlocks = new HashSet<>();
        Set<Long> visitedCoordinates = new HashSet<>();

        // Quét vét cạn hình nón theo hướng nhìn từ cự ly 3 block đến 90 block (góc hẹp ~8.5 độ)
        for (double dist = 3.0; dist <= 90.0; dist += 2.0) {
            double centerX = eyeX + dirX * dist;
            double centerY = eyeY + dirY * dist;
            double centerZ = eyeZ + dirZ * dist;

            double radius = Math.max(1.0, dist * 0.15);
            double radiusSq = radius * radius;
            int rCeil = (int) Math.ceil(radius);

            int cx = (int) Math.floor(centerX);
            int cy = (int) Math.floor(centerY);
            int cz = (int) Math.floor(centerZ);

            // Kiểm tra chunk trước khi quét để chống tải chunk đồng bộ (Sync Chunk Load)
            if (!world.isChunkLoaded(cx >> 4, cz >> 4)) {
                continue;
            }

            int minY = Math.max(minHeight, cy - rCeil);
            int maxY = Math.min(maxHeight - 1, cy + rCeil);

            for (int x = cx - rCeil; x <= cx + rCeil; x++) {
                double dx = (x + 0.5) - centerX;
                double dxSq = dx * dx;

                for (int z = cz - rCeil; z <= cz + rCeil; z++) {
                    double dz = (z + 0.5) - centerZ;
                    double dxzSq = dxSq + dz * dz;

                    if (dxzSq > radiusSq) {
                        continue;
                    }

                    for (int y = minY; y <= maxY; y++) {
                        double dy = (y + 0.5) - centerY;
                        if (dxzSq + dy * dy <= radiusSq) {
                            long coordKey = (((long) x & 0x3FFFFFF) << 38) | (((long) z & 0x3FFFFFF) << 12) | ((long) y & 0xFFF);
                            if (visitedCoordinates.add(coordKey)) {
                                Block b = world.getBlockAt(x, y, z);
                                Material mat = b.getType();
                                if ((mat == Material.SUSPICIOUS_GRAVEL || mat == Material.SUSPICIOUS_SAND)
                                        && hasLineOfSight(eye, b, world)) {
                                    visibleBlocks.add(b);
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<Block, Long> focusTimes = playerFocusMap.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        // Xóa các khối không còn trong tầm ngắm
        focusTimes.keySet().removeIf(b -> !visibleBlocks.contains(b));

        // Kiểm tra thời gian ngắm liên tục vào từng khối
        for (Block b : visibleBlocks) {
            Long firstSeen = focusTimes.putIfAbsent(b, now);
            if (firstSeen == null) {
                firstSeen = now;
            }

            // Nếu người chơi ngắm đủ 350ms vào khối
            if (now - firstSeen >= 350) {
                RevealedMarker existingMarker = revealedMarkers.get(b);
                if (existingMarker == null || !existingMarker.display.isValid()) {
                    // Tạo marker mới tồn tại ngẫu nhiên 5 - 10 giây (5000ms - 10000ms)
                    long duration = ThreadLocalRandom.current().nextLong(5000, 10001);
                    ItemDisplay display = spawnMarker(b, world);
                    revealedMarkers.put(b, new RevealedMarker(display, now + duration));

                    // Hiệu ứng fade outline khối trong 0.5s màu xanh
                    spawnFadeOutline(b, world);

                    playRadarPing(player);
                } else {
                    // Nếu đã hiện rồi mà vẫn đang ngắm thì duy trì thời gian tối thiểu 5s
                    existingMarker.expireTime = Math.max(existingMarker.expireTime, now + 5000L);
                }
            }
        }
    }

    /**
     * Kiểm tra tầm nhìn trực tiếp (Line of Sight - LOS) từ mắt người chơi đến khối.
     * Trả về true nếu người chơi thực sự nhìn thấy khối mà không bị tường chắn ở giữa.
     */
    private boolean hasLineOfSight(Location eye, Block block, World world) {
        Location bLoc = block.getLocation();
        double bx = bLoc.getX();
        double by = bLoc.getY();
        double bz = bLoc.getZ();

        // Kiểm tra tâm khối và các điểm bề mặt của khối
        double[][] testPoints = {
                {0.5, 0.5, 0.5},
                {0.5, 0.85, 0.5},
                {0.15, 0.5, 0.5},
                {0.85, 0.5, 0.5},
                {0.5, 0.5, 0.15},
                {0.5, 0.5, 0.85}
        };

        for (double[] pt : testPoints) {
            Location target = new Location(world, bx + pt[0], by + pt[1], bz + pt[2]);
            Vector dir = target.toVector().subtract(eye.toVector());
            double dist = dir.length();
            if (dist <= 0.001) {
                return true;
            }
            dir.normalize();

            RayTraceResult hit = world.rayTraceBlocks(eye, dir, dist, FluidCollisionMode.NEVER, true);
            if (hit == null) {
                return true;
            }
            Block hitBlock = hit.getHitBlock();
            if (hitBlock != null && hitBlock.equals(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tạo hiệu ứng viền sáng (Outline) màu xanh bao quanh khối và mờ dần (fade out) trong 0.5s (10 ticks)
     */
    private void spawnFadeOutline(Block block, World world) {
        Location centerLoc = block.getLocation().add(0.5, 0.5, 0.5);
        ItemDisplay outline = world.spawn(centerLoc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.CYAN_STAINED_GLASS));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0, 255, 200)); // Màu xanh Cyan-Green sáng
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(2.0005f, 2.0005f, 2.0005f),
                    new AxisAngle4f()
            ));
            d.setViewRange(3.0f);
        });

        temporaryOutlines.add(outline);

        // Hiệu ứng Fade Out: Giảm dần độ sáng và sắc độ Glow trong 10 ticks (0.5s)
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!outline.isValid()) {
                task.cancel();
                temporaryOutlines.remove(outline);
                return;
            }

            long currentLife = outline.getTicksLived();
            int totalTicks = 10; // 0.5s

            if (currentLife >= totalTicks) {
                outline.remove();
                temporaryOutlines.remove(outline);
                task.cancel();
                return;
            }

            float progress = (float) currentLife / totalTicks; // 0.0 -> 1.0
            float factor = Math.max(0.0f, 1.0f - progress);   // 1.0 -> 0.0

            int g = (int) (255 * factor);
            int b = (int) (200 * factor);

            if (g > 5 || b > 5) {
                outline.setGlowColorOverride(Color.fromRGB(0, g, b));
                int brightness = Math.max(0, (int) (15 * factor));
                outline.setBrightness(new Display.Brightness(brightness, brightness));
            } else {
                outline.setGlowing(false);
            }
        }, 2L, 2L);

        // Hạt viền sáng tại các góc khối
        spawnBlockOutlineParticles(block, world);
    }

    private void spawnBlockOutlineParticles(Block block, World world) {
        Location min = block.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 255, 200), 1.0f);
        double[][] corners = {
                {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0},
                {0, 0, 1}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}
        };
        for (double[] c : corners) {
            world.spawnParticle(Particle.DUST, min.clone().add(c[0], c[1], c[2]), 1, 0, 0, 0, 0, dust);
        }
    }

    private ItemDisplay spawnMarker(Block block, World world) {
        Location loc = block.getLocation().add(0.5, 1.2, 0.5);
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.BRUSH));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(0, 229, 255));
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(),
                    new Vector3f(0.7f, 0.7f, 0.7f),
                    new AxisAngle4f()
            ));
            d.setViewRange(3.0f);
        });
        world.spawnParticle(Particle.WAX_OFF, loc, 6, 0.15, 0.15, 0.15, 0.02);
        return display;
    }

    private void playRadarPing(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastRadarSound.getOrDefault(player.getUniqueId(), 0L) > 1000) {
            lastRadarSound.put(player.getUniqueId(), now);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.9f, 1.8f);
        }
    }

    public void removeAllMarkers() {
        if (scannerTask != null && !scannerTask.isCancelled()) {
            scannerTask.cancel();
        }
        for (RevealedMarker marker : revealedMarkers.values()) {
            if (marker.display.isValid()) {
                marker.display.remove();
            }
        }
        for (ItemDisplay outline : temporaryOutlines) {
            if (outline.isValid()) {
                outline.remove();
            }
        }
        temporaryOutlines.clear();
        revealedMarkers.clear();
        playerFocusMap.clear();
    }

    public boolean isBrokenTelescope(ItemStack item) {
        if (item == null) {
            return false;
        }

        var itemService = HaoHanItemCore.get().getItemService();
        if (itemService.isItem(item, "haohan:telescope_broken")) {
            return true;
        }

        if (item.getType() == Material.SPYGLASS && itemService.isItem(item, "haohan:telescope")) {
            if (item.getItemMeta() instanceof Damageable damageable) {
                int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : 60;
                return damageable.getDamage() >= maxDamage;
            }
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Player player = event.getPlayer();

        // 1. Kiểm tra kính đã hỏng
        if (isBrokenTelescope(item)) {
            breakIfDamagedMax(player, item, event.getHand());
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);

            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                playFailSound(player);
            }
            return;
        }

        // 2. Chỉ xử lý khi người chơi bắt đầu zoom (Right Click Air hoặc Right Click Block)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (item.getType() != Material.SPYGLASS) {
            return;
        }

        var itemService = HaoHanItemCore.get().getItemService();
        if (!itemService.isItem(item, "haohan:telescope")) {
            return;
        }

        // Nếu click vào block tương tác (rương, bàn chế tạo...) mà không sneak thì ưu tiên mở block thay vì zoom kính
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getBlockData().getMaterial().isInteractable() && !player.isSneaking()) {
                return;
            }
        }

        // Trừ độ bền ngay khi bắt đầu zoom (vẫn cho phép zoom lần cuối này)
        EquipmentSlot hand = event.getHand();
        consumeTelescopeDurability(player, item, hand);
    }

    private void consumeTelescopeDurability(Player player, ItemStack item, EquipmentSlot hand) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }

        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : 60;
        int currentDamage = damageable.getDamage();

        // Nếu đã hỏng rồi thì không xử lý thêm
        if (currentDamage >= maxDamage) {
            return;
        }

        // Mất ngẫu nhiên 7 - 15 độ bền cho mỗi lần sử dụng
        int loss = ThreadLocalRandom.current().nextInt(7, 16);
        int newDamage = Math.min(maxDamage, currentDamage + loss);

        // Cập nhật độ bền mới (nếu newDamage == maxDamage, kính sẽ vỡ sau khi kết thúc lần ngắm này)
        damageable.setDamage(newDamage);
        item.setItemMeta(damageable);
        if (hand != null) {
            player.getInventory().setItem(hand, item);
        }
    }

    @SuppressWarnings("deprecation")
    private boolean breakIfDamagedMax(Player player, ItemStack item, EquipmentSlot hand) {
        if (item == null || item.getType() != Material.SPYGLASS) {
            return false;
        }

        var itemService = HaoHanItemCore.get().getItemService();
        if (!itemService.isItem(item, "haohan:telescope")) {
            return false;
        }

        if (!(item.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }

        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : 60;
        if (damageable.getDamage() < maxDamage) {
            return false;
        }

        // Đạt tối đa hư hỏng -> Chuyển thành haohan:telescope_broken (Material.PAPER, không thể ngắm được nữa)
        ItemStack broken = HaoHanItemCore.get().getItemFactory().create("haohan:telescope_broken");
        if (broken != null) {
            if (broken.getItemMeta() instanceof Damageable brokenDamageable) {
                brokenDamageable.setDamage(maxDamage);
                broken.setItemMeta(brokenDamageable);
            }
            if (hand != null) {
                player.getInventory().setItem(hand, broken);
            } else {
                item.setType(broken.getType());
                item.setItemMeta(broken.getItemMeta());
            }
        }

        // Phát âm thanh nứt kính nhẹ và hạt vụn kính vỡ
        var loc = player.getLocation().add(0, 1.2, 0);
        player.playSound(loc, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.BLOCK, loc, 12, 0.2, 0.2, 0.2, 0.05, Material.GLASS.createBlockData());
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.1, 0.1, 0.02);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (isBrokenTelescope(item)) {
            breakIfDamagedMax(event.getPlayer(), item, event.getHand());
            event.setCancelled(true);
            playFailSound(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (isBrokenTelescope(item)) {
            breakIfDamagedMax(event.getPlayer(), item, event.getHand());
            event.setCancelled(true);
            playFailSound(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerStopUsingItem(PlayerStopUsingItemEvent event) {
        // Reset thời gian focus khi hạ kính xuống (các khối đã phát sáng 5-10s vẫn giữ nguyên)
        playerFocusMap.remove(event.getPlayer().getUniqueId());

        ItemStack item = event.getItem();
        breakIfDamagedMax(event.getPlayer(), item, null);
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        playerFocusMap.remove(event.getPlayer().getUniqueId());

        Player player = event.getPlayer();
        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
        breakIfDamagedMax(player, prevItem, EquipmentSlot.HAND);
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        playerFocusMap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        playerFocusMap.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastFailSound.remove(uuid);
        lastRadarSound.remove(uuid);
        playerFocusMap.remove(uuid);
    }

    private void playFailSound(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastFailSound.getOrDefault(player.getUniqueId(), 0L) > 400) {
            lastFailSound.put(player.getUniqueId(), now);
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8f, 1.5f);
        }
    }
}
