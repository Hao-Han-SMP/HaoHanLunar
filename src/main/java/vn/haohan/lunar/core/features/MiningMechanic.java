package vn.haohan.lunar.core.features;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class MiningMechanic implements Listener, LunarSubSystem {

    private final NamespacedKey slowMiningKey;
    private final NamespacedKey noMiningKey;

    public MiningMechanic(HaoHanLunarPlugin plugin) {
        this.slowMiningKey = new NamespacedKey(plugin, "slow_mining");
        this.noMiningKey = new NamespacedKey(plugin, "no_mining");
    }

    @Override
    public String name() {
        return "Mining";
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetMiningModifiers(player);
        }
    }

    @Override
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayerMining(player);
        }
    }

    private void tickPlayerMining(Player player) {
        if (!player.getWorld().getKey().toString().equals("haohan:lunar")) {
            resetMiningModifiers(player);
            return;
        }
        boolean lookingAtOre = isLookingAtLunarOre(player);
        updateMiningAttributes(player, lookingAtOre);
    }

    /** The only tool that can mine Anorthosite Ore in the lunar dimension. */
    public static boolean isAllowedLunarPickaxe(Material material) {
        return material == Material.NETHERITE_PICKAXE;
    }

    private boolean isLookingAtLunarOre(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target != null && target.getType() == Material.NOTE_BLOCK) {
            if (target.getBlockData() instanceof NoteBlock noteBlock) {
                int note = noteBlock.getNote().getId();
                return (note >= 21 && note <= 24) &&
                        noteBlock.getInstrument() == org.bukkit.Instrument.PLING;
            }
        }
        return false;
    }

    private void updateMiningAttributes(Player player, boolean lookingAtOre) {
        var instance = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
        if (instance == null)
            return;

        if (lookingAtOre) {
            boolean hasNetherite = isAllowedLunarPickaxe(player.getInventory().getItemInMainHand().getType());
            applyOreMiningModifier(instance, hasNetherite);
        } else {
            // Remove modifiers
            instance.removeModifier(slowMiningKey);
            instance.removeModifier(noMiningKey);
        }
    }

    private void applyOreMiningModifier(org.bukkit.attribute.AttributeInstance instance, boolean hasNetherite) {
        if (hasNetherite) {
            // Apply slow mining
            if (instance.getModifiers().stream().noneMatch(m -> m.getKey().equals(slowMiningKey))) {
                instance.removeModifier(noMiningKey);
                AttributeModifier modifier = new AttributeModifier(slowMiningKey, -0.974,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY);
                instance.addModifier(modifier);
            }
        } else {
            // Apply no mining
            if (instance.getModifiers().stream().noneMatch(m -> m.getKey().equals(noMiningKey))) {
                instance.removeModifier(slowMiningKey);
                AttributeModifier modifier = new AttributeModifier(noMiningKey, -1.0,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY);
                instance.addModifier(modifier);
            }
        }
    }

    public void resetMiningModifiers(Player player) {
        var instance = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
        if (instance != null) {
            instance.removeModifier(slowMiningKey);
            instance.removeModifier(noMiningKey);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resetMiningModifiers(event.getPlayer());
    }
}
