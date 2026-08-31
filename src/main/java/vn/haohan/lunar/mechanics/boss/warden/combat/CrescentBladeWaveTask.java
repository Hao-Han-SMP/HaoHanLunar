package vn.haohan.lunar.mechanics.boss.warden.combat;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Semicircular crescent blade wave expanding outward with glowing lunar slash particles.
 */
public class CrescentBladeWaveTask extends BukkitRunnable {
    private final IronGolem golem;
    private final Location current;
    private final Vector dir;
    private final Vector cross;
    private final boolean isSoulWave;
    private final Set<UUID> waveHitTargets = new HashSet<>();
    private int step = 0;
    private static final int MAX_STEPS = 14;

    // Semicircular arc radius (~10 blocks span)
    private static final double ARC_RADIUS = 4.8;
    // Sweep angle: -80 deg to +80 deg (~160 deg arc)
    private static final double MAX_ANGLE = Math.toRadians(80.0);
    private static final int ARC_POINTS = 17;

    private static final Particle.DustOptions LUNAR_CYAN = new Particle.DustOptions(Color.fromRGB(90, 225, 255), 1.8f);
    private static final Particle.DustOptions LUNAR_WHITE = new Particle.DustOptions(Color.fromRGB(240, 250, 255), 1.5f);

    public CrescentBladeWaveTask(IronGolem golem, Location origin, Vector direction, boolean isSoulWave) {
        this.golem = golem;
        this.current = origin.clone();
        this.dir = direction.clone().setY(0).normalize();
        this.cross = new Vector(-this.dir.getZ(), 0, this.dir.getX()).normalize();
        this.isSoulWave = isSoulWave;
    }

    @Override
    public void run() {
        if (golem.isDead() || current.getWorld() == null || step >= MAX_STEPS) {
            cancel();
            return;
        }

        step++;
        current.add(dir.clone().multiply(1.35));

        Location leadingCenter = WardenLocationUtil.adjustToTerrainSurface(current, 0.45);
        Location circleCenter = leadingCenter.clone().subtract(dir.clone().multiply(ARC_RADIUS));

        List<Location> arcPoints = new ArrayList<>(ARC_POINTS);

        // Render crescent blade wave along the arc
        for (int i = 0; i < ARC_POINTS; i++) {
            double progress = (double) i / (double) (ARC_POINTS - 1);
            double angle = -MAX_ANGLE + (progress * 2.0 * MAX_ANGLE);

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            Vector offset = dir.clone().multiply(ARC_RADIUS * cos)
                    .add(cross.clone().multiply(ARC_RADIUS * sin));

            Location pt = circleCenter.clone().add(offset);
            Location surfacePt = WardenLocationUtil.adjustToTerrainSurface(pt, 0.35);
            arcPoints.add(surfacePt);

            // 1. Sharp crescent slash particles
            if (i % 2 == 0) {
                surfacePt.getWorld().spawnParticle(Particle.SWEEP_ATTACK, surfacePt, 1, 0, 0, 0, 0);
            }

            // 2. Glowing lunar crescent energy dust
            surfacePt.getWorld().spawnParticle(Particle.DUST, surfacePt, 1, 0.05, 0.1, 0.05, 0.0,
                    (i % 3 == 0) ? LUNAR_WHITE : LUNAR_CYAN);

            // 3. Ethereal spark at the tip and crest
            if (i == 0 || i == ARC_POINTS - 1 || i == ARC_POINTS / 2) {
                surfacePt.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, surfacePt, 1, 0.05, 0.05, 0.05, 0.05);
            }
        }

        // Blade wave sound effects
        if (step % 3 == 0) {
            leadingCenter.getWorld().playSound(leadingCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
            WardenAudio.playCustomSound(leadingCenter, "haohan:boss.arcslash", 1.1f, 1.3f);
        }

        // Deal damage to targets (Reduced 20%: 16.0 -> 12.8)
        for (Player player : leadingCenter.getWorld().getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || !player.isValid() || player.isDead()) continue;
            if (waveHitTargets.contains(player.getUniqueId())) continue;

            Location pLoc = player.getLocation();
            boolean isHit = false;

            for (Location pt : arcPoints) {
                if (pLoc.distanceSquared(pt) <= 2.4 * 2.4) {
                    isHit = true;
                    break;
                }
            }

            if (isHit) {
                waveHitTargets.add(player.getUniqueId());
                WardenCombatHandler.applyCombatDamage(player, 12.8, golem);

                Vector knockback = dir.clone().multiply(0.48).setY(0.24);
                player.setVelocity(player.getVelocity().add(knockback));
                player.playSound(pLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 1.0f);
            }
        }
    }
}
