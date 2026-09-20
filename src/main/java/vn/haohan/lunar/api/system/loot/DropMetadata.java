package vn.haohan.lunar.api.system.loot;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

/**
 * Contextual metadata when resolving and rolling loot from a drop table.
 */
public record DropMetadata(ActiveMob dropper,
                           LivingEntity killer,
                           Location location,
                           double amountModifier,
                           long tick) {

    public DropMetadata {
        amountModifier = Double.isFinite(amountModifier) ? Math.max(0.0, amountModifier) : 1.0;
    }

    public static DropMetadata of(ActiveMob dropper, LivingEntity killer, Location location) {
        return new DropMetadata(dropper, killer, location, 1.0, 0L);
    }
}
