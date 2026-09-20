package vn.haohan.lunar.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.mob.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fired when loot is generated for an Mob death.
 * Supports per-player instanced loot generation and modifying generated drop lists.
 */
public final class LunarLootGenerateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob mob;
    private final Player recipient;
    private final List<ItemStack> drops;
    private boolean cancelled;

    public LunarLootGenerateEvent(Mob mob, Player recipient, List<ItemStack> drops) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.recipient = recipient;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    public Mob mob() {
        return mob;
    }

    public Optional<Player> recipient() {
        return Optional.ofNullable(recipient);
    }

    public List<ItemStack> drops() {
        return drops;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
