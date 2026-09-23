package vn.haohan.lunar.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.system.mob.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fired when loot drops are generated following the death of a custom mob.
 * Supports inspecting and modifying the drop list, as well as accessing the recipient player for instanced drops.
 */
public final class LootGenerateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob mob;
    private final Player recipient;
    private final List<ItemStack> drops;
    private boolean cancelled;

    public LootGenerateEvent(Mob mob, Player recipient, List<ItemStack> drops) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.recipient = recipient;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    /**
     * Returns the dying mob instance whose loot was generated.
     *
     * @return the mob instance
     */
    public Mob mob() {
        return mob;
    }

    /**
     * Returns the player receiving the instanced loot, or empty if drops are public.
     *
     * @return optional containing the recipient player
     */
    public Optional<Player> recipient() {
        return Optional.ofNullable(recipient);
    }

    /**
     * Returns the mutable list of item drops to be spawned.
     *
     * @return mutable list of item drops
     */
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
