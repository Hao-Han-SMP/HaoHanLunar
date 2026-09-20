package vn.haohan.lunar.api.system.combat.skill.target;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * Validated inputs shared by all targeters.
 */
public record TargeterContext(
        LivingEntity caster,
        LivingEntity target,
        Location origin,
        double radius,
        int maxResults
) {
    public static final double MAX_RADIUS = 128.0;
    public static final int MAX_RESULTS = 128;

    public TargeterContext {
        caster = Objects.requireNonNull(caster, "Caster must not be null");
        origin = Objects.requireNonNull(origin, "Origin must not be null").clone();
        if (origin.getWorld() == null) {
            throw new IllegalArgumentException("Origin world must not be null");
        }
        if (!Double.isFinite(radius) || radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Radius must be between 0 and " + MAX_RADIUS);
        }
        if (maxResults < 1 || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("Max results must be between 1 and " + MAX_RESULTS);
        }
    }

    public TargeterContext(LivingEntity caster, LivingEntity target, double radius, int maxResults) {
        this(caster, target, Objects.requireNonNull(caster, "Caster must not be null").getLocation(), radius, maxResults);
    }

    public Optional<LivingEntity> targetOptional() {
        return Optional.ofNullable(target);
    }

    public World world() {
        return origin.getWorld();
    }
}
