package vn.haohan.lunar.mechanics.boss.warden.skills;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenVFX;
import vn.haohan.lunar.util.MathUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TargetedLightStrikeTask extends BukkitRunnable {
    private final IronGolem golem;
    private final Location targetCenter;
    private final int warningDurationTicks = MathUtil.secondsToTicks(0.55);
    private final int beamDurationTicks = MathUtil.secondsToTicks(0.45);
    private final Set<UUID> beamHitPlayers = new HashSet<>();
    private int tick = 0;

    public TargetedLightStrikeTask(IronGolem golem, Location groundLoc) {
        this.golem = golem;
        this.targetCenter = WardenLocationUtil.adjustToTerrainSurface(groundLoc, 0.0);
    }

    @Override
    public void run() {
        if (golem == null || golem.isDead() || targetCenter.getWorld() == null) {
            cancel();
            return;
        }

        tick++;

        if (tick <= warningDurationTicks) {
            float warnProgress = (float) tick / warningDurationTicks;
            double spin = tick * 0.45;
            WardenVFX.renderMagicCircle(targetCenter, 1.8, spin, warnProgress, Color.fromRGB(255, 75, 75));
            targetCenter.getWorld().spawnParticle(Particle.DUST, targetCenter.clone().add(0, 0.15, 0), 5, 0.4, 0.05, 0.4, 0.0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 80), 1.5f));

            if (tick == 1) {
                targetCenter.getWorld().playSound(targetCenter, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.8f);
            }
        }

        if (tick > warningDurationTicks && tick <= warningDurationTicks + beamDurationTicks) {
            int beamTick = tick - warningDurationTicks;
            WardenVFX.renderSustainedPillarOfLight(targetCenter, 1.8, 30, beamTick);

            if (beamTick == 1) {
                targetCenter.getWorld().playSound(targetCenter, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.8f, 1.2f);
                targetCenter.getWorld().playSound(targetCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.6f);
                targetCenter.getWorld().playSound(targetCenter, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.6f);
                WardenAudio.playCustomSound(targetCenter, "haohan:boss.judgementcut", 1.8f, 1.3f);
                targetCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetCenter.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                targetCenter.getWorld().spawnParticle(Particle.FLASH, targetCenter.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
            }

            double impactRadius = 2.4;
            double beamDamage = 22.0;

            for (Player victim : targetCenter.getWorld().getPlayers()) {
                if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                Location vLoc = victim.getLocation();
                if (vLoc.distanceSquared(targetCenter) <= impactRadius * impactRadius) {
                    if (beamHitPlayers.add(victim.getUniqueId())) {
                        victim.setNoDamageTicks(0);
                        WardenCombatHandler.applyCombatDamage(victim, beamDamage, golem);
                        // Reduced knockback (minimal stagger, no high air launch)
                        Vector launch = new Vector(0, 0.08, 0);
                        victim.setVelocity(victim.getVelocity().multiply(0.5).add(launch));
                        victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                    }
                }
            }
        }

        if (tick > warningDurationTicks + beamDurationTicks) {
            cancel();
        }
    }
}
