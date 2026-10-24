package vn.haohan.lunar.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.itemcore.api.item.ItemBehavior;
import vn.haohan.itemcore.api.item.ItemContext;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.api.system.data.PlayerLunarData;

import java.util.Map;
import java.util.UUID;

public class OxygenTankBehavior implements ItemBehavior {

    public static final String TANK_UUID_KEY = "tank_uuid";

    public static String getOrAssignTankUuid(ItemStack item, HaoHanLunarPlugin plugin) {
        if (item == null || !item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        NamespacedKey key = new NamespacedKey(plugin, TANK_UUID_KEY);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuid = pdc.get(key, PersistentDataType.STRING);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            pdc.set(key, PersistentDataType.STRING, uuid);
            item.setItemMeta(meta);
        }
        return uuid;
    }

    @Override
    public void onUse(ItemContext context) {
        Player player = context.player();
        ItemStack item = context.item();
        
        // 1. Must be in lunar dimension
        if (!HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            player.sendActionBar(Component.text("⚠ Bình oxy chỉ dùng tại Mặt Trăng!", NamedTextColor.RED));
            return;
        }

        HaoHanLunarPlugin plugin = HaoHanLunarPlugin.getInstance();
        PlayerLunarData data = plugin.getLunarDataManager().get(player);

        // Ensure item has a unique tank UUID
        String tankUuid = getOrAssignTankUuid(item, plugin);

        // Check if player is clicking the currently active tank -> Toggle OFF
        if (data.isTankActive() && tankUuid != null && tankUuid.equals(data.getActiveTankUuid())) {
            // Flush any accumulated damage before toggling off
            int accum = data.getTankDamageAccumulator();
            if (accum > 0 && item.getItemMeta() instanceof Damageable damageable) {
                damageable.setDamage(damageable.getDamage() + accum);
                item.setItemMeta(damageable);
                data.setTankDamageAccumulator(0);
            }

            data.setTankActive(false);
            data.setActiveTankUuid(null);
            data.setActiveTankSlot(-1);
            player.removeScoreboardTag("hh_o2tank_active");

            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 1.0f, 1.0f);
            player.sendActionBar(Component.text("⏸ Đã tắt bình oxy dưỡng khí.", NamedTextColor.YELLOW));
            return;
        }

        // 2. Must wear full spacesuit to toggle ON
        if (!plugin.getGravityMechanic().wearsSpacesuit(player)) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("⚠"),
                Component.text("Không thể dùng bình oxy", NamedTextColor.RED)
            ));
            player.sendMessage(Component.text("[HaoHan] ", NamedTextColor.GOLD)
                .append(Component.text("Bạn cần mặc đầy đủ bộ đồ Spacesuit mới có thể sử dụng bình oxy!", NamedTextColor.RED)));
            return;
        }

        // 3. Must not be in safe zones to toggle ON
        if (plugin.getOxygenMechanic().isInSafeZone(player.getLocation())) {
            return;
        }

        // Determine capacity and tier from definition properties
        Map<String, Object> properties = context.definition().getProperties();
        if (properties == null || !Boolean.TRUE.equals(properties.get("oxygen_tank"))) {
            return;
        }

        int capacity = ((Number) properties.getOrDefault("oxygen_tank_capacity", 0)).intValue();
        int tier = ((Number) properties.getOrDefault("oxygen_tank_tier", 0)).intValue();

        if (tier == 0 || capacity == 0) return;

        // Check durability
        int damage = 0;
        if (item.getItemMeta() instanceof Damageable damageable) {
            damage = damageable.getDamage();
        }

        if (damage >= capacity) {
            player.sendActionBar(Component.text("⚠ Bình oxy đã cạn kiệt!", NamedTextColor.RED));
            return;
        }

        // Toggle ON
        data.setActiveTankUuid(tankUuid);
        data.setActiveTankSlot(player.getInventory().getHeldItemSlot());
        data.setTankTier(tier);
        data.setTankActive(true);
        data.setTankDamageAccumulator(0);
        data.setBreathSoundTick(0);

        player.addScoreboardTag("hh_o2tank_active");

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 1.0f, 1.0f);

        player.showTitle(net.kyori.adventure.title.Title.title(
            Component.empty(),
            Component.text("⚡ Kích hoạt bình dưỡng khí!", NamedTextColor.GREEN)
        ));
    }
}
