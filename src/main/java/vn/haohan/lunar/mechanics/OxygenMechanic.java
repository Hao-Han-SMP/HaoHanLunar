package vn.haohan.lunar.mechanics;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.data.PlayerLunarData;
import vn.haohan.lunar.item.LunarItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class OxygenMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;

    public OxygenMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getKey().toString().equals("haohan:lunar")) {
                tickPlayerOxygen(player);
            } else {
                // If player leaves lunar dimension, reset oxygen parameters if they had the tag
                if (player.getScoreboardTags().contains("hh_lunar_oxygen")) {
                    resetPlayerOxygen(player);
                }
            }
        }
    }

    private void tickPlayerOxygen(Player player) {
        PlayerLunarData data = plugin.getLunarDataManager().get(player);
        if (!player.getScoreboardTags().contains("hh_lunar_oxygen")) {
            data.setOxygen(600);
            data.setOxygenDmg(0);
            player.addScoreboardTag("hh_lunar_oxygen");
        }

        Location loc = player.getLocation();
        boolean inRestBase = isInRestBase(loc);
        boolean inSpaceStation = isInSpaceStation(loc);
        boolean inSafeZone = inRestBase || inSpaceStation;

        if (inSafeZone) {
            // Reset active tank values inside safe regen areas
            data.setTankO2(0);
            data.setTankTier(0);
            data.setTankActive(false);

            // Refill logic
            if (inRestBase) {
                data.setSsRegen(0);
                data.setOxygenDmg(0);
                data.setRbRegen(data.getRbRegen() + 1);
                if (data.getRbRegen() >= 60) {
                    data.setOxygen(data.getOxygen() + 100);
                    data.setRbRegen(0);
                }
            } else {
                data.setRbRegen(0);
                data.setOxygenDmg(0);
                data.setSsRegen(data.getSsRegen() + 1);
                if (data.getSsRegen() >= 40) {
                    data.setOxygen(data.getOxygen() + 150);
                    data.setSsRegen(0);
                }
            }

            // Charge held oxygen tank in safe structures
            boolean isCharging = chargeHeldOxygenTank(player, data);
            if (isCharging) {
                return;
            }
        } else {
            // Outside safe zone: reset safe structures variables
            data.setRbRegen(0);
            data.setSsRegen(0);
            data.setTankCharge(0);

            // Oxygen consumption
            if (data.isTankActive() && data.getTankO2() > 0) {
                // Subtract 1 from active tank oxygen
                data.setTankO2(data.getTankO2() - 1);

                // Transfer from tank to base oxygen if needed (hh_oxygen < 600)
                if (data.getOxygen() < 600) {
                    int needed = 600 - data.getOxygen();
                    int transfer = Math.min(needed, Math.min(10, data.getTankO2()));
                    data.setOxygen(data.getOxygen() + transfer);
                    data.setTankO2(data.getTankO2() - transfer);
                }

                data.setOxygenDmg(0);

                // Check if depleted
                if (data.getTankO2() <= 0) {
                    player.showTitle(net.kyori.adventure.title.Title.title(
                        Component.empty(),
                        Component.text("⚠ Bình oxy đã cạn!", NamedTextColor.RED)
                    ));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BREATH, SoundCategory.MASTER, 2.0f, 0.4f);
                    data.setTankTier(0);
                    data.setTankActive(false);
                }
            } else {
                // Decay base oxygen
                if (data.getOxygen() > 0) {
                    data.setOxygen(data.getOxygen() - 1);
                }

                // Suffocation damage
                if (data.getOxygen() <= 0) {
                    data.setOxygenDmg(data.getOxygenDmg() + 1);
                    if (data.getOxygenDmg() >= 20) {
                        DamageSource source = DamageSource.builder(DamageType.DROWN).build();
                        player.damage(1.0, source);
                        data.setOxygenDmg(0);
                    }
                }
            }
        }

        // Display oxygen bar
        displayOxygen(player, data);
    }

    private boolean chargeHeldOxygenTank(Player player, PlayerLunarData data) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String customId = vn.haohan.itemmanager.api.HaoHanItemManager.get().getItemService().getId(hand);
        
        int capacity = 0;
        int tier = 0;
        int maxTicks = 0;
        String name = "";

        if ("haohan:oxygen_tank_small".equals(customId)) {
            capacity = 1500;
            tier = 1;
            maxTicks = 100;
            name = "nhỏ";
        } else if ("haohan:oxygen_tank_medium".equals(customId)) {
            capacity = 3000;
            tier = 2;
            maxTicks = 200;
            name = "vừa";
        } else if ("haohan:oxygen_tank_large".equals(customId)) {
            capacity = 6800;
            tier = 3;
            maxTicks = 320;
            name = "lớn";
        }

        if (tier == 0 || !(hand.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() == 0) {
            data.setTankCharge(0);
            return false;
        }

        int damage = damageable.getDamage();
        data.setTankCharge(data.getTankCharge() + 1);

        // 1. Calculate base percentage from initial damage
        // start_capacity = max_capacity - damage
        double startCap = capacity - damage;
        double basePct = (startCap * 100.0) / capacity;

        // 2. Add progress percentage
        double progressPct = (data.getTankCharge() * 100.0) / maxTicks;
        double addedPct = progressPct * (100.0 - basePct) / 100.0;

        int displayPct = (int) Math.min(100, Math.ceil(basePct + addedPct));

        player.sendActionBar(Component.text("⚡ Đang nạp bình oxy " + name + "... ", NamedTextColor.YELLOW)
            .append(Component.text(displayPct + "%", NamedTextColor.GREEN)));

        if (data.getTankCharge() >= maxTicks) {
            damageable.setDamage(0);
            hand.setItemMeta(damageable);
            data.setTankCharge(0);

            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text("🔋 Bình oxy đã được nạp xong!", NamedTextColor.GREEN)
            ));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 2.0f, 1.73f);
        }

        return true;
    }

    private void displayOxygen(Player player, PlayerLunarData data) {
        int oxygen = data.getOxygen();
        int fullBubbles = (int) Math.ceil(oxygen / 60.0);
        if (oxygen <= 0) fullBubbles = 0;
        int emptyBubbles = 10 - fullBubbles;

        var message = Component.text();
        if (fullBubbles > 0) {
            String fullText = "● ".repeat(fullBubbles);
            if (emptyBubbles == 0) {
                fullText = fullText.trim();
            }
            message.append(Component.text(fullText, NamedTextColor.BLUE));
        }
        if (emptyBubbles > 0) {
            String emptyText = "○ ".repeat(emptyBubbles).trim();
            message.append(Component.text(emptyText, NamedTextColor.DARK_GRAY));
        }

        // Sound effect on bubble loss
        if (oxygen == 540 || oxygen == 480 || oxygen == 420 || oxygen == 360 
            || oxygen == 300 || oxygen == 240 || oxygen == 180 || oxygen == 120 
            || oxygen == 60 || oxygen == 1) {
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, SoundCategory.MASTER, 1.5f, 1.0f);
        }

        // Active tank percentage
        if (data.isTankActive()) {
            int capacity = 0;
            if (data.getTankTier() == 1) capacity = 1500;
            else if (data.getTankTier() == 2) capacity = 3000;
            else if (data.getTankTier() == 3) capacity = 6800;

            if (capacity > 0) {
                int pct = (data.getTankO2() * 100) / capacity;
                NamedTextColor color = NamedTextColor.GREEN;
                if (pct <= 25) color = NamedTextColor.RED;
                else if (pct <= 50) color = NamedTextColor.YELLOW;

                message.append(Component.text("  🔋 " + pct + "%", color));
            }
        }

        player.sendActionBar(message.build());
    }

    public void resetPlayerOxygen(Player player) {
        PlayerLunarData data = plugin.getLunarDataManager().get(player);
        data.setOxygen(600);
        data.setOxygenDmg(0);
        data.setRbRegen(0);
        data.setSsRegen(0);
        data.setTankO2(0);
        data.setTankTier(0);
        data.setTankActive(false);
        data.setTankCharge(0);
        player.removeScoreboardTag("hh_lunar_oxygen");
        player.removeScoreboardTag("hh_o2tank_active");
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getKey().toString().equals("haohan:lunar")) {
            resetPlayerOxygen(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getKey().toString().equals("haohan:lunar")) {
            // Save state to PDC when player quits
            plugin.getLunarDataManager().saveAndRemove(player);
        } else {
            plugin.getLunarDataManager().remove(player);
        }
    }

    public boolean isInSafeZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        if (!world.getKey().toString().equals("haohan:lunar")) return false;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var structures = world.getStructures(chunkX + dx, chunkZ + dz);
                for (var gen : structures) {
                    String key = gen.getStructure().getKey().toString();
                    if (key.equals("haohan:rest_base") || key.equals("haohan:space_station")) {
                        if (gen.getBoundingBox().contains(loc.toVector())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean isInRestBase(Location loc) {
        return isInSpecificStructure(loc, "haohan:rest_base");
    }

    public boolean isInSpaceStation(Location loc) {
        return isInSpecificStructure(loc, "haohan:space_station");
    }

    private boolean isInSpecificStructure(Location loc, String structureKey) {
        if (loc == null || loc.getWorld() == null) return false;
        World world = loc.getWorld();
        if (!world.getKey().toString().equals("haohan:lunar")) return false;

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                var structures = world.getStructures(chunkX + dx, chunkZ + dz);
                for (var gen : structures) {
                    String key = gen.getStructure().getKey().toString();
                    if (key.equals(structureKey)) {
                        if (gen.getBoundingBox().contains(loc.toVector())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
