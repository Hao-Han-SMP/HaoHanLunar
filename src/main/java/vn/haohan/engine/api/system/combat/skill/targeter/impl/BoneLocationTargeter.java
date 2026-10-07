package vn.haohan.engine.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import vn.haohan.engine.api.integration.modelengine.IBoneLocationResolver;
import vn.haohan.engine.api.integration.modelengine.DefaultBoneLocationResolver;
import vn.haohan.engine.api.system.combat.skill.SkillCastContext;
import vn.haohan.engine.api.system.combat.skill.targeter.ILocationTargeter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * ITargeter {@code @BoneLocation{bone=head|right_hand|mouth|chest}} for querying real-time 3D coordinates
 * of specific bones on ModelEngine active models.
 * Automatically falls back to EyeLocation or Location if ModelEngine is missing or bone is not found.
 */
public final class BoneLocationTargeter implements ILocationTargeter {

    private final IBoneLocationResolver resolver;

    public BoneLocationTargeter() {
        this(DefaultBoneLocationResolver.getInstance());
    }

    public BoneLocationTargeter(IBoneLocationResolver resolver) {
        this.resolver = resolver != null ? resolver : DefaultBoneLocationResolver.getInstance();
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || context.caster() == null) {
            return List.of();
        }

        Entity entity = context.caster().entity();
        if (entity == null) {
            return List.of();
        }

        String bone = "head";
        if (parameters != null && parameters.containsKey("bone")) {
            bone = String.valueOf(parameters.get("bone"));
        } else if (parameters != null && parameters.containsKey("name")) {
            bone = String.valueOf(parameters.get("name"));
        }

        Location loc = resolver.resolveBoneLocation(entity, bone);
        return loc != null ? List.of(loc) : List.of();
    }
}
