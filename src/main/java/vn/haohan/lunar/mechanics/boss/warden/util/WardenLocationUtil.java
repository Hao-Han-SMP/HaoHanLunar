package vn.haohan.lunar.mechanics.boss.warden.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

public final class WardenLocationUtil {
    private WardenLocationUtil() {}

    public static boolean isSafeBossStandLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block groundBlock = loc.clone().subtract(0, 0.2, 0).getBlock();
        if (groundBlock.isPassable() || !groundBlock.getType().isSolid()) {
            return false;
        }

        double[] yOffsets = {0.1, 1.0, 2.0};
        for (double dy : yOffsets) {
            Block b = loc.clone().add(0, dy, 0).getBlock();
            if (!b.isPassable() && b.getType().isSolid()) {
                return false;
            }
        }

        return true;
    }

    public static Location findSafeNavigableSurface(Location loc, double heightOffset) {
        if (loc == null || loc.getWorld() == null) return loc;
        World world = loc.getWorld();
        int blockX = loc.getBlockX();
        int blockZ = loc.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, loc.getBlockY() + 12);
        int minY = Math.max(world.getMinHeight() + 1, loc.getBlockY() - 16);

        for (int y = startY; y >= minY; y--) {
            Block ground = world.getBlockAt(blockX, y, blockZ);
            if (!ground.isPassable() && ground.getType().isSolid()) {
                Block feet = world.getBlockAt(blockX, y + 1, blockZ);
                Block body = world.getBlockAt(blockX, y + 2, blockZ);
                if (feet.isPassable() && body.isPassable()) {
                    return new Location(world, loc.getX(), y + 1.0 + heightOffset, loc.getZ(), loc.getYaw(), loc.getPitch());
                }
            }
        }
        return loc;
    }

    public static Location adjustToTerrainSurface(Location loc, double heightOffset) {
        return findSafeNavigableSurface(loc, heightOffset);
    }

    public static Location findSafeTeleportLocation(Location origin, Location desired, double safetyBuffer) {
        if (desired == null) return origin;
        World world = desired.getWorld() != null ? desired.getWorld() : (origin != null ? origin.getWorld() : null);
        if (world == null) return origin;

        // 1. Direct surface snap at desired location
        Location directSurface = findSafeNavigableSurface(desired, 0.0);
        if (isSafeBossStandLocation(directSurface)) {
            return directSurface;
        }

        // 2. Spiral candidate search around desired location
        double[] searchRadii = {1.5, 2.5, 3.5, 5.0};
        for (double r : searchRadii) {
            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0);
                double sx = desired.getX() + r * Math.cos(angle);
                double sz = desired.getZ() + r * Math.sin(angle);
                Location probe = new Location(world, sx, desired.getY(), sz, desired.getYaw(), desired.getPitch());
                Location candidate = findSafeNavigableSurface(probe, 0.0);
                if (isSafeBossStandLocation(candidate)) {
                    return candidate;
                }
            }
        }

        // 3. Fallback to origin if no safe spot found anywhere around desired
        return origin != null ? origin : desired;
    }
}
