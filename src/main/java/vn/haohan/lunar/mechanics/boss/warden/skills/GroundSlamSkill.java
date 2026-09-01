package vn.haohan.lunar.mechanics.boss.warden.skills;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.visual.BlockWaveRenderer;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

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

        // 1. Spawns organic fractured debris burst ring and smooth concentric expanding ripple wave
        // Center erupts immediately (delay 0) and settles down first, propagating sequentially outward
        BlockWaveRenderer.spawnBurstRing(plugin, slamCenter, 3.5, 12, 0.95, 13, 0.24, random);
        BlockWaveRenderer.spawnConcentricWave(plugin, slamCenter, maxRadius, 1.40, 0.90, 13, 0.24, random);

        // 2. Epicenter direct impact damage
        double centerBurstRadius = 3.6;
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

        // 3. Expanding ripple wave damage check synchronized with block wave travel
        new BukkitRunnable() {
            private int tick = 1;
            private final int maxTravelTicks = (int) Math.round(maxRadius * 1.40);

            @Override
            public void run() {
                if (golem.isDead() || tick > maxTravelTicks) {
                    cancel();
                    return;
                }

                double curWaveDist = (double) tick / 1.40;
                double waveBandTolerance = 1.40;

                for (Player victim : world.getPlayers()) {
                    if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                    Location vLoc = victim.getLocation();
                    double dist = vLoc.distance(slamCenter);
                    double heightDiff = Math.abs(vLoc.getY() - slamCenter.getY());

                    if (heightDiff <= 2.8 && Math.abs(dist - curWaveDist) <= waveBandTolerance && dist > centerBurstRadius) {
                        double waveDamage = 2.5 + Math.max(0.0, (1.0 - (dist / maxRadius)) * 1.8);
                        WardenCombatHandler.applyCombatDamage(victim, waveDamage, golem);

                        Vector outwardPush = vLoc.toVector().subtract(slamCenter.toVector()).setY(0);
                        if (outwardPush.lengthSquared() < 0.001) {
                            outwardPush = slamCenter.getDirection().setY(0);
                        }
                        outwardPush.normalize().multiply(0.68).setY(0.12);
                        victim.setVelocity(outwardPush);

                        victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 0.85f, 1.3f);
                        world.spawnParticle(Particle.BLOCK, vLoc.clone().add(0, 0.15, 0), 6, 0.2, 0.1, 0.2, 0.08, particleBlockData);
                    }
                }

                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
