package vn.haohan.lunar.api.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import vn.haohan.lunar.api.mob.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fired when an Mob dies in combat.
 * Allows inspection of killer, total threat/damage record, and modification of drops.
 */
public final class LunarMobDeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Mob mob;
    private final LivingEntity killer;
    private final List<ItemStack> drops;
    private final double totalDamage;

    public LunarMobDeathEvent(Mob mob, LivingEntity killer, List<ItemStack> drops, double totalDamage) {
        this.mob = Objects.requireNonNull(mob, "Mob must not be null");
        this.killer = killer;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
        this.totalDamage = Math.max(0.0, totalDamage);
    }

    public Mob mob() {
        return mob;
    }

    public Optional<LivingEntity> killer() {
        return Optional.ofNullable(killer);
    }

    public List<ItemStack> drops() {
        return drops;
    }

    public double totalDamage() {
        return totalDamage;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
