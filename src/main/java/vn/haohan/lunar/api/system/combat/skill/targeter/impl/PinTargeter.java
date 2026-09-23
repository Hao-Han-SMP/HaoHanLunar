package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Location;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.SinglePin;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Targets the spatial location of a named persistent Pin registered in {@link PinManager}.
 * Syntax: {@code @Pin{name=pedestal_center}} or {@code @Pin{pin=portal_1}}
 */
public final class PinTargeter implements LocationTargeter {

    @Override
    public String name() {
        return "pin";
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }

        Object pinNameObj = parameters.get("name");
        if (pinNameObj == null) pinNameObj = parameters.get("pin");
        if (pinNameObj == null) pinNameObj = parameters.get("id");
        if (pinNameObj == null) return List.of();

        String pinName = String.valueOf(pinNameObj).trim();
        if (pinName.isBlank()) return List.of();

        return PinManager.get().getPin(pinName)
                .map(pin -> {
                    Location loc = pin.toLocation();
                    if (loc.getWorld() == null && context != null) {
                        if (context.origin() != null) {
                            loc.setWorld(context.origin().getWorld());
                        } else if (context.caster() != null && context.caster().entity() != null && context.caster().entity().getLocation() != null) {
                            loc.setWorld(context.caster().entity().getLocation().getWorld());
                        }
                    }
                    return loc;
                })
                .map(List::of)
                .orElse(List.of());
    }
}
