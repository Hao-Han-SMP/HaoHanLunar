package vn.haohan.lunar.item;

import vn.haohan.itemcore.api.item.ItemBehavior;
import vn.haohan.itemcore.api.item.ItemContext;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.data.PlayerLunarData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Map;

public class OxygenTankBehavior implements ItemBehavior {

    @Override
    public void onUse(ItemContext context) {
        Player player = context.player();
        ItemStack item = context.item();
        
        // 1. Must be in lunar dimension
        if (!player.getWorld().getKey().toString().equals("haohan:lunar")) {
            player.sendActionBar(Component.text("⚠ Bình oxy chỉ dùng tại Mặt Trăng!", NamedTextColor.RED));
            return;
        }

        HaoHanLunarPlugin plugin = HaoHanLunarPlugin.getInstance();
        PlayerLunarData data = plugin.getLunarDataManager().get(player);

        // 2. Must wear full spacesuit
        if (!plugin.getGravityMechanic().wearsSpacesuit(player)) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("⚠"),
                Component.text("Không thể dùng bình oxy", NamedTextColor.RED)
            ));
            player.sendMessage(Component.text("[HaoHan] ", NamedTextColor.GOLD)
                .append(Component.text("Bạn cần mặc đầy đủ bộ đồ Spacesuit mới có thể sử dụng bình oxy!", NamedTextColor.RED)));
            return;
        }

        // 3. Must not be in safe zones
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

        // Read current damage/durability
        int damage = 0;
        if (item.getItemMeta() instanceof Damageable damageable) {
            damage = damageable.getDamage();
        }

        int remaining = capacity - damage;
        int emptyThreshold = (int) (capacity * 0.15); // under 15% is empty

        if (remaining <= emptyThreshold) {
            // Already empty
            return;
        }

        // Set hand item to fully damaged (empty)
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(capacity);
            item.setItemMeta(damageable);
        }

        // Apply state
        boolean wasActive = data.isTankActive() && data.getTankO2() > 0;
        data.setTankO2(remaining);
        data.setTankTier(tier);
        data.setTankActive(true);

        if (wasActive) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text("🔋 Đã chuyển sang bình oxy mới", NamedTextColor.YELLOW)
            ));
        } else {
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text("🔋 Bình oxy đã được kích hoạt", NamedTextColor.GREEN)
            ));
        }

        // Play breath sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 2.0f, 0.4f);
    }
}
