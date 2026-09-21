package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Selects online players within perceptual range to receive VFX, audio packets, or display packets.
 * Unlike combat targeters, creative mode players are included because they can perceive effects.
 * Spectators can optionally be included or excluded via {@code includeSpectator=false}.
 * Syntax: {@code @Audience{r=32}} or {@code @SkillAudience{r=48}}
 */
public final class AudienceTargeter implements EntityTargeter {

    private static final double DEFAULT_AUDIENCE_RADIUS = 32.0;

    private static double parseAudienceRadius(Map<String, Object> parameters) {
        if (parameters == null) return DEFAULT_AUDIENCE_RADIUS;
        Object raw = parameters.get("radius");
        if (raw == null) raw = parameters.get("r");
        if (raw instanceof Number n) return Math.clamp(n.doubleValue(), 1.0, 128.0);
        if (raw instanceof String s) {
            try {
                return Math.clamp(Double.parseDouble(s.trim()), 1.0, 128.0);
            }
            catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_AUDIENCE_RADIUS;
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null) return List.of();

        Location center = context.origin();
        if (center == null && context.caster() != null && context.caster().entity() != null) {
            center = context.caster().entity().getLocation();
        }
        if (center == null) return List.of();

        World world = center.getWorld();
        if (world == null) return List.of();

        double radius = parseAudienceRadius(parameters);
        boolean includeSpectators = Boolean.parseBoolean(String.valueOf(parameters != null ? parameters.getOrDefault("spectators", false) : false));

        List<LivingEntity> audience = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player player && player.isOnline() && player.isValid()) {
                if (!includeSpectators && player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                audience.add(player);
            }
        }
        return List.copyOf(audience);
    }
}
