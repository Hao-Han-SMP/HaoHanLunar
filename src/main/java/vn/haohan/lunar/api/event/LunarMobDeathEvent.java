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
 * Fired when a custom mob dies in combat.
 * Provides access to the killer entity, mutable drop list, and total damage accumulated during combat.
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

    /**
     * Returns the mob that died.
     *
     * @return the dead mob
     */
    public Mob mob() {
        return mob;
    }

    /**
     * Returns the entity that delivered the killing blow, if present.
     *
     * @return optional containing the killer entity
     */
    public Optional<LivingEntity> killer() {
        return Optional.ofNullable(killer);
    }

    /**
     * Returns the mutable list of items dropped upon death.
     *
     * @return mutable list of item drops
     */
    public List<ItemStack> drops() {
        return drops;
    }

    /**
     * Returns the total recorded damage dealt to this mob across its combat session.
     *
     * @return total damage accumulated
     */
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
