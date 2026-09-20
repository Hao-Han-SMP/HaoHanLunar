package vn.haohan.lunar.core.system.item;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.skill.target.TargetRef;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runtime coordinator that handles active item skill execution, cooldown tracking, and event dispatch.
 */
public final class ItemSkillRuntime implements Listener {

    @FunctionalInterface
    public interface SkillInvoker {
        void invoke(Player caster, String skillId, TargetRef target);
    }

    private final ItemDefinitionRegistry itemRegistry;
    private final ItemCooldownManager cooldownManager;
    private SkillInvoker skillInvoker;
    private Consumer<Player> onCooldownNotification = (p) -> {};

    public ItemSkillRuntime(ItemDefinitionRegistry itemRegistry, ItemCooldownManager cooldownManager) {
        this.itemRegistry = Objects.requireNonNull(itemRegistry, "Item registry must not be null");
        this.cooldownManager = Objects.requireNonNull(cooldownManager, "Cooldown manager must not be null");
    }

    public void setSkillInvoker(SkillInvoker skillInvoker) {
        this.skillInvoker = skillInvoker;
    }

    public void setOnCooldownNotification(Consumer<Player> notifier) {
        this.onCooldownNotification = notifier != null ? notifier : (p) -> {};
    }

    public ItemCooldownManager cooldownManager() {
        return cooldownManager;
    }

    public ItemDefinitionRegistry itemRegistry() {
        return itemRegistry;
    }

    /**
     * Executes bound skills for the given trigger if the item is a custom MythicItem.
     *
     * @return true if at least one skill was executed
     */
    public boolean triggerItemSkill(Player player, ItemStack item, ItemSkillTrigger trigger,
                                    Entity targetEntity, Location targetLoc, long currentTick) {
        if (player == null || item == null) return false;

        Optional<String> itemIdOpt = ItemDefinitionRegistry.getItemId(item);
        if (itemIdOpt.isEmpty()) return false;
        String itemId = itemIdOpt.get();

        Optional<MythicItemDefinition> defOpt = itemRegistry.get(itemId);
        if (defOpt.isEmpty()) return false;
        MythicItemDefinition def = defOpt.get();

        boolean executed = false;
        for (ItemSkillBinding binding : def.skills()) {
            if (binding.trigger() == trigger) {
                if (cooldownManager.isOnCooldown(player.getUniqueId(), def.id(), currentTick)) {
                    onCooldownNotification.accept(player);
                    return false;
                }

                if (skillInvoker != null) {
                    TargetRef target = targetEntity != null
                            ? TargetRef.entity(targetEntity)
                            : (targetLoc != null ? TargetRef.location(targetLoc) : TargetRef.entity(player));
                    skillInvoker.invoke(player, binding.skillId(), target);
                }

                if (binding.cooldownTicks() > 0) {
                    cooldownManager.setCooldown(player.getUniqueId(), def.id(), binding.cooldownTicks(), currentTick);
                }
                executed = true;
            }
        }
        return executed;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        Action action = event.getAction();
        long currentTick = player.getServer() != null ? player.getServer().getCurrentTick() : 0L;

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Location clickedLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
            if (triggerItemSkill(player, item, ItemSkillTrigger.ON_USE, null, clickedLoc, currentTick)) {
                event.setCancelled(true);
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            Location clickedLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : null;
            if (triggerItemSkill(player, item, ItemSkillTrigger.ON_SWING, null, clickedLoc, currentTick)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            long currentTick = player.getServer() != null ? player.getServer().getCurrentTick() : 0L;
            triggerItemSkill(player, weapon, ItemSkillTrigger.ON_DAMAGE, event.getEntity(), event.getEntity().getLocation(), currentTick);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        long currentTick = player.getServer() != null ? player.getServer().getCurrentTick() : 0L;
        triggerItemSkill(player, item, ItemSkillTrigger.ON_CONSUME, null, player.getLocation(), currentTick);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer() != null) {
            cooldownManager.resetAll(event.getPlayer().getUniqueId());
        }
    }
}
