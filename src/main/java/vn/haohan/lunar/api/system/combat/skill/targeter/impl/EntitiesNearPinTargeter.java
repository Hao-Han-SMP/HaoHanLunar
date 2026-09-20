package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.SinglePin;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.EntityTargeter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves entities within a given radius around a named SinglePin.
 * Syntax: {@code @EntitiesNearPin{pin=CENTER_CRYSTAL;r=8}}
 */
public final class EntitiesNearPinTargeter implements EntityTargeter {

    @Override
    public String name() {
        return "entities_near_pin";
    }

    @Override
    public Collection<LivingEntity> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (context == null || parameters == null) {
            return List.of();
        }

        String pinName = (String) parameters.getOrDefault("pin", parameters.get("name"));
        if (pinName == null || pinName.isBlank()) {
            return List.of();
        }

        Optional<SinglePin> pinOpt = PinManager.get().getPin(pinName);
        if (pinOpt.isEmpty()) {
            return List.of();
        }

        SinglePin pin = pinOpt.get();
        double radius = 8.0;
        Object rObj = parameters.getOrDefault("r", parameters.get("radius"));
        if (rObj instanceof Number num) {
            radius = num.doubleValue();
        } else if (rObj instanceof String str) {
            try {
                radius = Double.parseDouble(str);
            } catch (NumberFormatException ignored) {}
        }
        double radiusSq = radius * radius;

        World world = null;
        if (context != null && context.origin() != null && context.origin().getWorld() != null) {
            world = context.origin().getWorld();
        } else if (context != null && context.caster() != null && context.caster().entity() != null) {
            world = context.caster().entity().getWorld();
        } else {
            try {
                world = Bukkit.getWorld(pin.worldName());
            } catch (Throwable ignored) {}
        }

        if (world == null) {
            return List.of();
        }

        Location pinLoc = pin.toLocation();
        List<LivingEntity> results = new ArrayList<>();
        try {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.isValid() && !entity.isDead()) {
                    if (entity.getLocation().distanceSquared(pinLoc) <= radiusSq) {
                        results.add(entity);
                    }
                }
            }
        } catch (Throwable ignored) {}

        return Collections.unmodifiableList(results);
    }
}
