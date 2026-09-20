package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Targets one random valid player within a specified radius around origin.
 * Excludes dead, offline, Spectator and Creative players.
 * Caps radius at {@link TargeterFilter#MAX_RADIUS} blocks.
 */
public final class RandomPlayerTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "random_player";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location center = context.origin();
        if (center == null || center.getWorld() == null) {
            return List.of();
        }

        double radius = TargeterFilter.parseRadius(parameters);
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

        if (matched.isEmpty()) {
            return List.of();
        }
        if (matched.size() == 1) {
            return List.of(matched.getFirst());
        }

        int index = ThreadLocalRandom.current().nextInt(matched.size());
        return List.of(matched.get(index));
    }
}
