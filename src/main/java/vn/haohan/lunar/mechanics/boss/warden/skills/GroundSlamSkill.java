package vn.haohan.lunar.mechanics.boss.warden.skills;

import vn.haohan.lunar.mechanics.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.util.MathUtil;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class GroundSlamSkill {
    private GroundSlamSkill() {}

    public static void executeGroundSlamAOE(HaoHanLunarPlugin plugin, IronGolem golem, Random random) {
        Location bossLoc = golem.getLocation();
        Location slamCenter = bossLoc.clone().add(bossLoc.getDirection().multiply(3.4));
        slamCenter.setY(bossLoc.getY());
        World world = slamCenter.getWorld();
        if (world == null) return;

        Block groundBlock = slamCenter.clone().subtract(0, 0.2, 0).getBlock();
        if (groundBlock.isPassable()) {
            groundBlock = slamCenter.clone().subtract(0, 1.0, 0).getBlock();
        }
        BlockData particleBlockData = !groundBlock.isPassable() && groundBlock.getType().isSolid()
                ? groundBlock.getBlockData()
                : Material.STONE.createBlockData();

        slamCenter.getWorld().playSound(slamCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.7f);
        slamCenter.getWorld().playSound(slamCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        slamCenter.getWorld().playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
        slamCenter.getWorld().playSound(slamCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
        slamCenter.getWorld().playSound(slamCenter, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.8f, 0.6f);
        WardenAudio.playCustomSound(slamCenter, "haohan:boss.mortalblade_whoosh", 1.8f, 0.8f);
        WardenAudio.playCustomSound(slamCenter, "haohan:boss.slash_heavy", 1.8f, 0.7f);

        slamCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, slamCenter.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
        slamCenter.getWorld().spawnParticle(Particle.FLASH, slamCenter.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
        slamCenter.getWorld().spawnParticle(Particle.BLOCK, slamCenter.clone().add(0, 0.5, 0), 40, 0.8, 0.5, 0.8, 0.20, particleBlockData);

        double maxRadius = 9.2;
        int centerBX = slamCenter.getBlockX();
        int centerBZ = slamCenter.getBlockZ();
        int radiusInt = (int) Math.ceil(maxRadius);

        Map<Integer, List<int[]>> waveBuckets = new HashMap<>();

        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= maxRadius * maxRadius) {
                    double dist = Math.sqrt(distSq);
                    if (dist > maxRadius - 0.6 && (Math.abs(dx * dz) % 3 != 0)) {
                        continue;
                    }
                    int delay = (int) Math.round(dist * 1.45);
                    waveBuckets.computeIfAbsent(delay, k -> new ArrayList<>()).add(new int[]{centerBX + dx, centerBZ + dz, dx, dz});
                }
            }
        }

        for (Map.Entry<Integer, List<int[]>> entry : waveBuckets.entrySet()) {
            int delay = entry.getKey();
            List<int[]> blockCoords = entry.getValue();
            new GroundSlamRippleWaveTask(plugin, golem, slamCenter, blockCoords, delay, maxRadius, particleBlockData, random).runTaskLater(plugin, delay);
        }

        double centerBurstRadius = 3.6;
        // Epicenter damage reduced by 20% (44.0 -> 35.2, 20.0 -> 16.0)
        double maxEpicenterDmg = 35.2;
        double minEpicenterDmg = 16.0;

        for (Player victim : slamCenter.getWorld().getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

            Location vLoc = victim.getLocation();
            double dist = vLoc.distance(slamCenter);

            if (dist <= centerBurstRadius) {
                double heightDiff = Math.abs(vLoc.getY() - slamCenter.getY());
                if (heightDiff > 4.0) continue;

                double centerFalloff = 1.0 - (dist / centerBurstRadius);
                double actualDamage = minEpicenterDmg + (centerFalloff * (maxEpicenterDmg - minEpicenterDmg));

                WardenCombatHandler.applyCombatDamage(victim, actualDamage, golem);

                double upwardVelocity = 0.32 + (centerFalloff * 0.20);
                Vector outwardDir = vLoc.toVector().subtract(slamCenter.toVector()).setY(0);
                if (outwardDir.lengthSquared() > 0.001) {
                    outwardDir.normalize().multiply(0.45 + (centerFalloff * 0.40));
                } else {
                    outwardDir = new Vector(0, 0, 0);
                }
                outwardDir.setY(upwardVelocity);
                victim.setVelocity(outwardDir);

                victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.3f, 0.6f);
                victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 16, 0.3, 0.3, 0.3, 0.15);
            }
        }
    }

    private static class GroundSlamRippleWaveTask extends BukkitRunnable {
        private final HaoHanLunarPlugin plugin;
        private final IronGolem golem;
        private final Location slamCenter;
        private final List<int[]> blockCoords;
        private final int delayTicks;
        private final double maxRadius;
        private final BlockData fallbackBlockData;
        private final Random random;

        public GroundSlamRippleWaveTask(HaoHanLunarPlugin plugin, IronGolem golem, Location slamCenter,
                                        List<int[]> blockCoords, int delayTicks, double maxRadius, BlockData fallbackBlockData, Random random) {
            this.plugin = plugin;
            this.golem = golem;
            this.slamCenter = slamCenter;
            this.blockCoords = blockCoords;
            this.delayTicks = delayTicks;
            this.maxRadius = maxRadius;
            this.fallbackBlockData = fallbackBlockData;
            this.random = random;
        }

        @Override
        public void run() {
            World world = slamCenter.getWorld();
            if (golem.isDead() || world == null) return;

            if (delayTicks % 3 == 0) {
                float soundPitch = 0.65f + ((float) delayTicks / 16.0f);
                world.playSound(slamCenter, Sound.BLOCK_ROOTED_DIRT_BREAK, 0.9f, soundPitch);
            }

            double avgWaveDist = 0.0;
            for (int[] coord : blockCoords) {
                int dx = coord[2];
                int dz = coord[3];
                avgWaveDist += Math.sqrt(dx * dx + dz * dz);
            }
            if (!blockCoords.isEmpty()) {
                avgWaveDist /= blockCoords.size();
            }

            for (int[] coord : blockCoords) {
                int bx = coord[0];
                int bz = coord[1];
                int dx = coord[2];
                int dz = coord[3];
                double dist = Math.sqrt(dx * dx + dz * dz);

                Location checkLoc = new Location(world, bx + 0.5, slamCenter.getY(), bz + 0.5);
                Location groundPt = WardenLocationUtil.adjustToTerrainSurface(checkLoc, 0.0);

                Block blockBelow = groundPt.clone().subtract(0, 0.2, 0).getBlock();
                if (blockBelow.isPassable() || !blockBelow.getType().isSolid()) continue;
                if (blockBelow.getType() == Material.BEDROCK || blockBelow.getType() == Material.BARRIER) continue;

                BlockData actualBlockData = blockBelow.getBlockData();

                Location pParticle = groundPt.clone().add(0, 0.12, 0);
                world.spawnParticle(Particle.BLOCK, pParticle, 1, 0.12, 0.12, 0.12, 0.05, actualBlockData);

                double distRatio = MathUtil.clamp01(dist / maxRadius);
                double amplitude = Math.max(0.0, Math.pow(1.0 - distRatio, 1.35));

                if (amplitude > 0.04) {
                    Location spawnLoc = groundPt.clone().add(0, 0.05, 0);
                    try {
                        float roll = (float) Math.toRadians((random.nextDouble() - 0.5) * 32.0);
                        float pitch = (float) Math.toRadians((random.nextDouble() - 0.5) * 32.0);
                        float yaw = (float) Math.toRadians(random.nextDouble() * 360.0);
                        Quaternionf leftRot = new Quaternionf().rotateXYZ(pitch, yaw, roll);

                        BlockDisplay bd = world.spawn(spawnLoc, BlockDisplay.class, entity -> {
                            entity.setBlock(actualBlockData);
                            entity.setTransformation(new Transformation(
                                    new Vector3f(-0.5f, 0f, -0.5f),
                                    leftRot,
                                    new Vector3f(1.0f, 1.0f, 1.0f),
                                    new Quaternionf()
                            ));
                        });
                        WardenEntityManager.registerTempEntity(bd);

                        double peakY = 0.72 * amplitude;
                        final Location baseLoc = spawnLoc.clone();

                        new BukkitRunnable() {
                            int tick = 0;
                            final int totalTicks = MathUtil.secondsToTicks(0.30);

                            @Override
                            public void run() {
                                if (!bd.isValid() || tick >= totalTicks) {
                                    WardenEntityManager.removeTempEntity(bd);
                                    cancel();
                                    return;
                                }
                                double progress = (double) tick / (double) totalTicks;
                                double curYOffset = Math.sin(progress * Math.PI) * peakY;
                                Location newLoc = baseLoc.clone().add(0, curYOffset, 0);
                                bd.teleport(newLoc);
                                tick++;
                            }
                        }.runTaskTimer(plugin, 1L, 1L);

                    } catch (Throwable ignored) {}
                }
            }

            double waveBandTolerance = 1.45;
            for (Player victim : world.getPlayers()) {
                if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                Location vLoc = victim.getLocation();
                double dist = vLoc.distance(slamCenter);
                double heightDiff = Math.abs(vLoc.getY() - slamCenter.getY());

                if (heightDiff <= 2.8 && Math.abs(dist - avgWaveDist) <= waveBandTolerance) {
                    double waveDamage = 2.5 + Math.max(0.0, (1.0 - (dist / maxRadius)) * 1.8);
                    WardenCombatHandler.applyCombatDamage(victim, waveDamage, golem);

                    Vector outwardPush = vLoc.toVector().subtract(slamCenter.toVector()).setY(0);
                    if (outwardPush.lengthSquared() < 0.001) {
                        outwardPush = slamCenter.getDirection().setY(0);
                    }
                    outwardPush.normalize().multiply(0.68).setY(0.12);
                    victim.setVelocity(outwardPush);

                    victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 0.85f, 1.3f);
                    world.spawnParticle(Particle.BLOCK, vLoc.clone().add(0, 0.15, 0), 6, 0.2, 0.1, 0.2, 0.08, fallbackBlockData);
                }
            }
        }
    }
}
