package vn.haohan.lunar.features;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.engine.core.service.IService;

import java.util.Set;

public class MiningMechanic implements Listener, IService {

    public static final Set<String> LUNAR_ORE_BLOCK_IDS = Set.of(
            "haohan:anorthosite_ore",
            "haohan:ilmenite_ore",
            "haohan:pyroxene_ore",
            "haohan:kreep_basalt"
    );

    private final NamespacedKey slowMiningKey;
    private final NamespacedKey noMiningKey;

    public MiningMechanic(HaoHanLunarPlugin plugin) {
        this.slowMiningKey = plugin != null ? new NamespacedKey(plugin, "slow_mining") : new NamespacedKey("haohanlunar", "slow_mining");
        this.noMiningKey = plugin != null ? new NamespacedKey(plugin, "no_mining") : new NamespacedKey("haohanlunar", "no_mining");
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
        return false;
    }

    @Override
    public void disable(org.bukkit.plugin.Plugin plugin) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetMiningModifiers(player);
        }
    }

    @Override
    public void tick() {
        // Mining speed, tool tier verification, and break progressions
        // are now fully managed by HaoHanItemCore's CustomBlockService.
    }

    /** The only tool that can mine Anorthosite Ore in the lunar dimension. */
    public static boolean isAllowedLunarPickaxe(Material material) {
        return material == Material.NETHERITE_PICKAXE;
    }

    /**
     * Checks if the held tool satisfies the Netherite tier requirement for Lunar Anorthosite Ore.
     * Accurately supports custom pickaxes (e.g., HaoHanMetallurgy) resolved via ToolTierRegistry.
     */
    public static boolean isAllowedLunarPickaxe(org.bukkit.inventory.ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return false;
        }

        try {
            var core = HaoHanItemCore.get();
            if (core != null && core.getToolTierRegistry() != null) {
                var tierRegistry = core.getToolTierRegistry();
                var toolTier = tierRegistry.findTier(tool);
                if (toolTier != null) {
                    var netheriteTier = tierRegistry.get("netherite").orElse(vn.haohan.itemcore.api.block.ToolTier.NETHERITE);
                    return toolTier.level() >= netheriteTier.level();
                }
            }
        } catch (Throwable ignored) {}

        return isAllowedLunarPickaxe(tool.getType());
    }

    public boolean isLookingAtLunarOre(Player player) {
        if (player == null) return false;
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.BARRIER) {
            return false;
        }

        try {
            var core = HaoHanItemCore.get();
            if (core != null && core.getCustomBlockService() != null) {
                var cbService = core.getCustomBlockService();
                if (cbService.isCustomBlock(target)) {
                    String id = cbService.getCustomBlockId(target);
                    return id != null && LUNAR_ORE_BLOCK_IDS.contains(id);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public void resetMiningModifiers(Player player) {
        var instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance != null) {
            if (instance.getModifier(slowMiningKey) != null) {
                instance.removeModifier(slowMiningKey);
            }
            if (instance.getModifier(noMiningKey) != null) {
                instance.removeModifier(noMiningKey);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        if (!HaoHanLunarPlugin.isLunarWorld(event.getPlayer().getWorld())) {
            resetMiningModifiers(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        resetMiningModifiers(event.getPlayer());
    }
}
