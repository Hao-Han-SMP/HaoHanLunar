package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Selects the single nearest valid player to the caster or origin location.
 * Syntax: {@code @NearestPlayer{r=25}} or {@code @PIRNearest{r=25}}
 */
public final class NearestPlayerTargeter implements EntityTargeter {

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) return List.of();

        LivingEntity caster = context.caster().entity();
        if (caster == null || !caster.isValid()) return List.of();

        Location center = context.origin() != null ? context.origin() : caster.getLocation();
        World world = center.getWorld();
        if (world == null) return List.of();

        double radius = TargeterFilter.parseRadius(parameters);
        Player nearest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player && TargeterFilter.isTargetable(player)) {
                if (player.getUniqueId().equals(caster.getUniqueId())) continue;

                double distSq = center.distanceSquared(player.getLocation());
                if (distSq <= radius * radius && distSq < minDistanceSq) {
                    minDistanceSq = distSq;
                    nearest = player;
                }
            }
        }

        return nearest != null ? List.of(nearest) : List.of();
    }
}
