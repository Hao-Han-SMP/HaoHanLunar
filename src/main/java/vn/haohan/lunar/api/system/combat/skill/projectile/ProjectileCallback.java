package vn.haohan.lunar.api.system.combat.skill.projectile;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Event callbacks fired during a projectile's flight and collision lifecycle.
 */
public interface ProjectileCallback {

    default void onTick(ActiveProjectile projectile) {}

    default void onHitEntity(ActiveProjectile projectile, LivingEntity target) {}

    default void onHitBlock(ActiveProjectile projectile, Location blockLocation, Vector surfaceNormal) {}

    default void onEnd(ActiveProjectile projectile) {}
}
