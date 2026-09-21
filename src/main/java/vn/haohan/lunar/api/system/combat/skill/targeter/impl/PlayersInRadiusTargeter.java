package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.*;

/**
 * Targets all valid players within a specified radius around origin.
 * Excludes dead, offline, Spectator and Creative players.
 * Caps radius at {@link TargeterFilter#MAX_RADIUS} blocks.
 */
public final class PlayersInRadiusTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "players_in_radius";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location center = context.origin();
        if (center == null || center.getWorld() == null) {
            return List.of();
        }

        double radius = TargeterFilter.parseRadius(parameters);
        int limit = TargeterFilter.parseLimit(parameters);
        double radiusSq = radius * radius;
        World world = center.getWorld();

        List<Player> matched = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!TargeterFilter.isTargetable(player)) continue;
            Location loc = player.getLocation();
            if (!TargeterFilter.isSameWorld(center, loc)) continue;
            if (center.distanceSquared(loc) <= radiusSq) {
                matched.add(player);
            }
        }

        matched.sort(Comparator.comparingDouble(p -> center.distanceSquared(p.getLocation())));

        List<LivingEntity> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, matched.size()); i++) {
            result.add(matched.get(i));
        }
        return List.copyOf(result);
    }
}
