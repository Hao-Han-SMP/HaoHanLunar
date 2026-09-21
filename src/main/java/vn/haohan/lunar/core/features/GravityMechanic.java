package vn.haohan.lunar.core.features;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

public class GravityMechanic implements Listener, LunarSubSystem {

    private final HaoHanLunarPlugin plugin;
    private final NamespacedKey modifierKey;

    public GravityMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        this.modifierKey = new NamespacedKey(plugin, "lunar_physic");
    }

    @Override
    public String name() {
        return "Gravity";
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void init(HaoHanLunarPlugin plugin) {
        initializeLoadedLunarEntities();
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeLunarAttributes(player);
        }
    }

    @Override
    public void tick() {
        World lunarWorld = HaoHanLunarPlugin.getLunarWorld();
        if (lunarWorld == null) {
            return;
        }

        // Apply low gravity to falling items in the lunar world
        for (Item item : lunarWorld.getEntitiesByClass(Item.class)) {
            if (!item.isOnGround()) {
                var vel = item.getVelocity();
                vel.setY(vel.getY() + 0.033372);
                item.setVelocity(vel);
            }
        }

        // Apply low gravity to falling blocks in the lunar world
        for (FallingBlock fb : lunarWorld.getEntitiesByClass(FallingBlock.class)) {
            if (!fb.isOnGround()) {
                var vel = fb.getVelocity();
                vel.setY(vel.getY() + 0.033372);
                fb.setVelocity(vel);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeLunarAttributes(event.getPlayer());
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && HaoHanLunarPlugin.isLunarWorld(living.getWorld())) {
            applyLunarAttributes(living);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (HaoHanLunarPlugin.isLunarWorld(player.getWorld())) {
            applyLunarAttributes(player);
        }
    }

    /** Applies attributes to entities already loaded before this plugin enables. */
    public void initializeLoadedLunarEntities() {
        World lunarWorld = HaoHanLunarPlugin.getLunarWorld();
        if (lunarWorld != null) {
            for (LivingEntity entity : lunarWorld.getLivingEntities()) {
                applyLunarAttributes(entity);
            }
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            if (event.getTo() != null && HaoHanLunarPlugin.isLunarWorld(event.getTo().getWorld())) {
                applyLunarAttributes(living);
            } else if (event.getTo() != null) {
                removeLunarAttributes(living);
            }
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && HaoHanLunarPlugin.isLunarWorld(event.getTo().getWorld())) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (event.getRespawnLocation().getWorld() != null
                && HaoHanLunarPlugin.isLunarWorld(event.getRespawnLocation().getWorld())) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    public void applyLunarAttributes(LivingEntity entity) {
        if (entity.getScoreboardTags().contains("hh_lunar_physic")) return;

        applyModifier(entity, Attribute.GENERIC_GRAVITY, -0.8343, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(entity, Attribute.GENERIC_SAFE_FALL_DISTANCE, 15.0, AttributeModifier.Operation.ADD_NUMBER);
        applyModifier(entity, Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER, -0.8, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(entity, Attribute.GENERIC_ATTACK_KNOCKBACK, 0.75, AttributeModifier.Operation.ADD_NUMBER);

        if (entity instanceof Player player) {
            applyModifier(player, Attribute.PLAYER_BLOCK_BREAK_SPEED, -0.2, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }

        entity.addScoreboardTag("hh_lunar_physic");
    }

    public void removeLunarAttributes(LivingEntity entity) {
        if (!entity.getScoreboardTags().contains("hh_lunar_physic")) return;

        removeModifier(entity, Attribute.GENERIC_GRAVITY);
        removeModifier(entity, Attribute.GENERIC_SAFE_FALL_DISTANCE);
        removeModifier(entity, Attribute.GENERIC_FALL_DAMAGE_MULTIPLIER);
        removeModifier(entity, Attribute.GENERIC_ATTACK_KNOCKBACK);

        if (entity instanceof Player player) {
            removeModifier(player, Attribute.PLAYER_BLOCK_BREAK_SPEED);
            // Also reset mining modifiers just in case
            plugin.getMiningMechanic().resetMiningModifiers(player);
        }

        entity.removeScoreboardTag("hh_lunar_physic");
    }

    private void applyModifier(LivingEntity entity, Attribute attr, double amount, AttributeModifier.Operation op) {
        var instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.removeModifier(modifierKey);
            AttributeModifier modifier = new AttributeModifier(modifierKey, amount, op, EquipmentSlotGroup.ANY);
            instance.addModifier(modifier);
        }
    }

    private void removeModifier(LivingEntity entity, Attribute attr) {
        var instance = entity.getAttribute(attr);
        if (instance != null) {
            instance.removeModifier(modifierKey);
        }
    }

    public boolean wearsSpacesuit(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        return isSpacesuitPart(helmet, "haohan:spacesuit_helmet")
                && isSpacesuitPart(chest, "haohan:spacesuit_chestplate")
                && isSpacesuitPart(legs, "haohan:spacesuit_leggings")
                && isSpacesuitPart(boots, "haohan:spacesuit_boots");
    }

    private boolean isSpacesuitPart(ItemStack item, String expectedModel) {
        if (item == null || !item.hasItemMeta()) return false;
        try {
            var itemService = HaoHanItemCore.get().getItemService();
            if (itemService != null && itemService.isItem(item, expectedModel)) {
                return true;
            }
        } catch (Throwable ignored) {}
        int expectedCmd = switch (expectedModel) {
            case "haohan:spacesuit_helmet" -> 1001;
            case "haohan:spacesuit_chestplate" -> 1002;
            case "haohan:spacesuit_leggings" -> 1003;
            case "haohan:spacesuit_boots" -> 1004;
            default -> -1;
        };
        return expectedCmd != -1 && item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == expectedCmd;
    }
}
