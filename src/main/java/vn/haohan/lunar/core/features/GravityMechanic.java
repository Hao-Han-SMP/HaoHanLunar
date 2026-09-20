package vn.haohan.lunar.core.subsystem.features;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

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
        for (World world : Bukkit.getWorlds()) {
            if (world.getKey().toString().equals("haohan:lunar")) {
                // Apply low gravity to falling items
                for (Item item : world.getEntitiesByClass(Item.class)) {
                    if (!item.isOnGround()) {
                        var vel = item.getVelocity();
                        vel.setY(vel.getY() + 0.033372);
                        item.setVelocity(vel);
                    }
                }

                // Apply low gravity to falling blocks
                for (FallingBlock fb : world.getEntitiesByClass(FallingBlock.class)) {
                    if (!fb.isOnGround()) {
                        var vel = fb.getVelocity();
                        vel.setY(vel.getY() + 0.033372);
                        fb.setVelocity(vel);
                    }
                }
            } else {
                // Reset attributes for players not in Lunar world
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getWorld().getKey().toString().equals("haohan:lunar")) {
                        if (player.getScoreboardTags().contains("hh_lunar_physic")) {
                            removeLunarAttributes(player);
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getKey().toString().equals("haohan:lunar")) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && living.getWorld().getKey().toString().equals("haohan:lunar")) {
            applyLunarAttributes(living);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getKey().toString().equals("haohan:lunar")) {
            applyLunarAttributes(player);
        }
    }

    /** Applies attributes to entities already loaded before this plugin enables. */
    public void initializeLoadedLunarEntities() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getKey().toString().equals("haohan:lunar")) {
                for (LivingEntity entity : world.getLivingEntities()) applyLunarAttributes(entity);
            }
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            if (event.getTo() != null && event.getTo().getWorld().getKey().toString().equals("haohan:lunar")) {
                applyLunarAttributes(living);
            } else if (event.getTo() != null) {
                removeLunarAttributes(living);
            }
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && event.getTo().getWorld() != null
                && event.getTo().getWorld().getKey().toString().equals("haohan:lunar")) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (event.getRespawnLocation().getWorld() != null
                && event.getRespawnLocation().getWorld().getKey().toString().equals("haohan:lunar")) {
            applyLunarAttributes(player);
        } else {
            removeLunarAttributes(player);
        }
    }

    public void applyLunarAttributes(LivingEntity entity) {
        if (entity.getScoreboardTags().contains("hh_lunar_physic")) return;

        applyModifier(entity, Attribute.GRAVITY, -0.8343, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(entity, Attribute.SAFE_FALL_DISTANCE, 15.0, AttributeModifier.Operation.ADD_NUMBER);
        applyModifier(entity, Attribute.FALL_DAMAGE_MULTIPLIER, -0.8, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyModifier(entity, Attribute.ATTACK_KNOCKBACK, 0.75, AttributeModifier.Operation.ADD_NUMBER);

        if (entity instanceof Player player) {
            applyModifier(player, Attribute.BLOCK_BREAK_SPEED, -0.2, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        }

        entity.addScoreboardTag("hh_lunar_physic");
    }

    public void removeLunarAttributes(LivingEntity entity) {
        if (!entity.getScoreboardTags().contains("hh_lunar_physic")) return;

        removeModifier(entity, Attribute.GRAVITY);
        removeModifier(entity, Attribute.SAFE_FALL_DISTANCE);
        removeModifier(entity, Attribute.FALL_DAMAGE_MULTIPLIER);
        removeModifier(entity, Attribute.ATTACK_KNOCKBACK);

        if (entity instanceof Player player) {
            removeModifier(player, Attribute.BLOCK_BREAK_SPEED);
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
        NamespacedKey model = item.getItemMeta().getItemModel();
        return model != null && model.toString().equals(expectedModel);
    }
}
