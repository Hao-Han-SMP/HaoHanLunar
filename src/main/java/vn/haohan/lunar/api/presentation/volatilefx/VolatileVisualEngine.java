package vn.haohan.lunar.api.presentation.volatilefx;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile Visual FX Engine:
 * Implements non-destructive client-side fake block cracks and visceral camera shake effects
 * without altering world terrain blocks or causing permanent destruction.
 */
public final class VolatileVisualEngine {

    private static final Map<CrackedBlockKey, Long> ACTIVE_CRACKS = new ConcurrentHashMap<>();

    private record CrackedBlockKey(UUID worldId, int x, int y, int z) {
        static CrackedBlockKey of(Location loc) {
            UUID wid = loc.getWorld() != null ? loc.getWorld().getUID() : new UUID(0L, 0L);
            return new CrackedBlockKey(wid, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        }

        Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }

    private VolatileVisualEngine() {}

    /**
     * Plays a non-destructive ground crack effect around the center location.
     * Block damage packets (stages 0.1 to 0.9) are sent to nearby players and
     * automatically cleared after durationTicks.
     */
    public static void playGroundCrack(Location center, double radius, int durationTicks, float maxStage, Collection<? extends Player> audience) {
        if (center == null || center.getWorld() == null || radius <= 0) return;
        World world = center.getWorld();

        Collection<? extends Player> recipients = (audience != null && !audience.isEmpty())
                ? audience
                : findAudience(center, radius + 24.0);

        if (recipients.isEmpty()) return;

        int intRadius = (int) Math.ceil(radius);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        double rSq = radius * radius;
        long expireTick = getEstimatedCurrentTick() + Math.max(1, durationTicks);

        for (int x = -intRadius; x <= intRadius; x++) {
            for (int z = -intRadius; z <= intRadius; z++) {
                double distSq = x * x + z * z;
                if (distSq > rSq) continue;

                // Find top solid block near cy
                for (int y = 2; y >= -3; y--) {
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    if (isSolidBlock(block)) {
                        Location blockLoc = block.getLocation();
                        float normalizedDist = (float) (Math.sqrt(distSq) / radius);
                        float stage = Math.max(0.1f, Math.min(1.0f, maxStage * (1.0f - (normalizedDist * 0.6f))));

                        sendBlockDamagePacket(recipients, blockLoc, stage);
                        ACTIVE_CRACKS.put(CrackedBlockKey.of(blockLoc), expireTick);
                        break;
                    }
                }
            }
        }

        // Visual crumbling dust and crumble sound (safe in headless tests)
        try {
            Particle p = Particle.valueOf("BLOCK_CRUMBLE");
            world.spawnParticle(p, center.clone().add(0, 0.2, 0),
                    (int) (radius * 8), radius * 0.5, 0.1, radius * 0.5,
                    world.getBlockAt(center).getBlockData());
        } catch (Throwable ignored) {}

        try {
            Sound s = Sound.valueOf("BLOCK_GRAVEL_BREAK");
            world.playSound(center, s, SoundCategory.BLOCKS, 1.5f, 0.6f);
        } catch (Throwable ignored) {}
    }

    /**
     * Shakes the camera and applies visceral kinetic impulse to players within radius.
     */
    public static void playCameraShake(Location center, double radius, float intensity, Collection<? extends Player> audience) {
        if (center == null || radius <= 0) return;

        Collection<? extends Player> recipients = (audience != null && !audience.isEmpty())
                ? audience
                : findAudience(center, radius);

        double rSq = radius * radius;

        for (Player player : recipients) {
            if (player == null || !player.isOnline()) continue;
            Location pLoc = player.getLocation();
            if (center.getWorld() != null && !Objects.equals(center.getWorld(), pLoc.getWorld())) continue;

            double distSq = center.distanceSquared(pLoc);
            if (distSq > rSq) continue;

            double dist = Math.sqrt(distSq);
            float falloff = (float) Math.max(0.0, 1.0 - (dist / radius));
            float shakePower = intensity * falloff;

            if (shakePower <= 0.05f) continue;

            // 1. Native hurt tilt animation for visceral camera shake without dealing damage
            try {
                float randomAngle = (float) (Math.random() * 60.0 - 30.0);
                player.sendHurtAnimation(randomAngle);
            } catch (Throwable ignored) {}

            // 2. Micro kinetic recoil impulse
            try {
                Vector currentVel = player.getVelocity();
                double upward = Math.min(0.35, 0.12 * shakePower);
                player.setVelocity(currentVel.add(new Vector(0, upward, 0)));
            } catch (Throwable ignored) {}

            // 3. Heavy ground rumble sound (safe in headless tests)
            try {
                Sound s = Sound.valueOf("ENTITY_WARDEN_SONIC_BOOM");
                player.playSound(pLoc, s, SoundCategory.HOSTILE, 0.8f * shakePower, 0.5f);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Combined Ground Slam FX: executes synchronized fake block cracks and camera shake.
     */
    public static void playGroundSlam(Location center, double radius, int durationTicks, float intensity) {
        Collection<? extends Player> audience = findAudience(center, Math.max(radius + 24.0, 32.0));
        playGroundCrack(center, radius, durationTicks, 0.85f, audience);
        playCameraShake(center, radius * 1.5, intensity, audience);
    }

    /**
     * Ticks active cracked blocks and restores blocks whose duration has expired.
     */
    public static void tick(long currentTick) {
        if (ACTIVE_CRACKS.isEmpty()) return;

        ACTIVE_CRACKS.entrySet().removeIf(entry -> {
            if (currentTick >= entry.getValue()) {
                CrackedBlockKey key = entry.getKey();
                clearBlockDamage(key);
                return true;
            }
            return false;
        });
    }

    /**
     * Clears all active cracks immediately (e.g. on plugin disable).
     */
    public static void clearAll() {
        for (CrackedBlockKey key : ACTIVE_CRACKS.keySet()) {
            clearBlockDamage(key);
        }
        ACTIVE_CRACKS.clear();
    }

    public static int activeCrackCount() {
        return ACTIVE_CRACKS.size();
    }

    private static void clearBlockDamage(CrackedBlockKey key) {
        try {
            World world = org.bukkit.Bukkit.getWorld(key.worldId());
            if (world != null) {
                Location loc = key.toLocation(world);
                Collection<? extends Player> players = world.getPlayers();
                sendBlockDamagePacket(players, loc, 0.0f);
            }
        } catch (Throwable ignored) {}
    }

    private static void sendBlockDamagePacket(Collection<? extends Player> recipients, Location loc, float stage) {
        for (Player p : recipients) {
            if (p != null && p.isOnline()) {
                try {
                    p.sendBlockDamage(loc, stage);
                } catch (Throwable ignored) {}
            }
        }
    }

    private static Collection<? extends Player> findAudience(Location center, double radius) {
        if (center == null || center.getWorld() == null) return List.of();
        double rSq = radius * radius;
        List<Player> players = new ArrayList<>();
        try {
            for (Player p : center.getWorld().getPlayers()) {
                if (p.isOnline() && p.getLocation().distanceSquared(center) <= rSq) {
                    players.add(p);
                }
            }
        } catch (Throwable ignored) {}
        return players;
    }

    private static long getEstimatedCurrentTick() {
        try {
            return org.bukkit.Bukkit.getCurrentTick();
        } catch (Throwable t) {
            return System.currentTimeMillis() / 50L;
        }
    }
    private static boolean isSolidBlock(Block block) {
        if (block == null) return false;
        try {
            Material mat = block.getType();
            if (mat == null) return false;
            try {
                return mat.isSolid();
            } catch (Throwable ignored) {
                return !mat.name().endsWith("_AIR") && mat != Material.AIR;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }
}
