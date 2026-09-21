package vn.haohan.lunar.api.system.combat.skill.targeter.impl;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.targeter.LocationTargeter;
import vn.haohan.lunar.api.system.world.pin.PinManager;
import vn.haohan.lunar.api.system.world.pin.PinRegion;

import java.util.*;

/**
 * Resolves floor block locations situated within a designated PinRegion polygon.
 * Syntax: {@code @BlocksInPinRegion{region=ARENA_1}}
 */
public final class BlocksInPinRegionTargeter implements LocationTargeter {

    @Override
    public String name() {
        return "blocks_in_pin_region";
    }

    @Override
    public Collection<Location> resolve(SkillCastContext context, Map<String, Object> parameters) {
        if (parameters == null) {
            return List.of();
        }

        String regionName = (String) parameters.getOrDefault("region", parameters.get("r"));
        if (regionName == null || regionName.isBlank()) {
            return List.of();
        }

        Optional<PinRegion> regionOpt = PinManager.get().getRegion(regionName);
        if (regionOpt.isEmpty()) {
            return List.of();
        }

        PinRegion region = regionOpt.get();
        World world = null;
        if (context != null && context.origin() != null && context.origin().getWorld() != null) {
            world = context.origin().getWorld();
        } else if (context != null && context.caster() != null && context.caster().entity() != null) {
            world = context.caster().entity().getWorld();
        } else {
            try {
                world = Bukkit.getWorld(region.worldName());
            } catch (Throwable ignored) {}
        }

        double floorY = region.minY();
        Object yObj = parameters.get("y");
        if (yObj instanceof Number n) {
            floorY = n.doubleValue();
        }

        int minX = (int) Math.floor(region.minX());
        int maxX = (int) Math.ceil(region.maxX());
        int minZ = (int) Math.floor(region.minZ());
        int maxZ = (int) Math.ceil(region.maxZ());

        List<Location> locations = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (region.contains(x + 0.5, floorY, z + 0.5)) {
                    locations.add(new Location(world, x, floorY, z));
                }
            }
        }

        return Collections.unmodifiableList(locations);
    }
}
