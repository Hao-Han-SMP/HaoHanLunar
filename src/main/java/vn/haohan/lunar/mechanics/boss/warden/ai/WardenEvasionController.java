package vn.haohan.lunar.mechanics.boss.warden.ai;

import vn.haohan.lunar.util.MathUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenVFX;

import java.util.Random;

public final class WardenEvasionController {
    private WardenEvasionController() {}

    /**
     * Anti-Kiting Phantom Warp Strike
     */
    public static void executeAntiKitePhantomWarpStrike(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, Random random) {
        state.chaseStallTimer = 0;
        Location currentLoc = golem.getLocation();
        Location targetLoc = target.getLocation();

        Vector targetFacing = target.getLocation().getDirection().setY(0).normalize();
        if (targetFacing.lengthSquared() < 0.01) {
            targetFacing = targetLoc.toVector().subtract(currentLoc.toVector()).setY(0).normalize();
        }

        Location rawWarpLoc = targetLoc.clone().add(targetFacing.clone().multiply(random.nextBoolean() ? 2.2 : -2.0));
        Location warpLoc = WardenLocationUtil.findSafeTeleportLocation(currentLoc, rawWarpLoc, 1.2);
        if (!WardenLocationUtil.isSafeBossStandLocation(warpLoc)) {
            warpLoc = WardenLocationUtil.findSafeTeleportLocation(currentLoc, targetLoc.clone(), 1.4);
        }

        Location startFx = currentLoc.clone().add(0, 1.8, 0);
        currentLoc.getWorld().spawnParticle(Particle.PORTAL, startFx, 45, 0.8, 1.2, 0.8, 0.6);
        currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, startFx, 3, 0.4, 0.2, 0.4, 0);
        currentLoc.getWorld().spawnParticle(Particle.DUST, startFx, 40, 0.5, 0.8, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.2f));
        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.8f, 1.5f);
        currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.5f, 1.4f);
        WardenAudio.playCustomSound(currentLoc, "haohan:boss.electric", 1.8f, 1.6f);

        Vector dirToTarget = targetLoc.toVector().subtract(warpLoc.toVector()).setY(0).normalize();
        float targetYaw = MathUtil.getYaw(dirToTarget);
        warpLoc.setYaw(targetYaw);
        warpLoc.setPitch(0f);

        WardenVFX.playTeleportShrinkEffect(plugin, golem, state);

        golem.teleport(warpLoc);

        Location arriveFx = warpLoc.clone().add(0, 1.5, 0);
        warpLoc.getWorld().spawnParticle(Particle.PORTAL, arriveFx, 40, 0.7, 1.0, 0.7, 0.5);
        warpLoc.getWorld().spawnParticle(Particle.DUST, arriveFx, 40, 0.6, 0.7, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(90, 240, 255), 2.0f));
        warpLoc.getWorld().playSound(warpLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 1.8f);
        WardenAudio.playCustomSound(warpLoc, "haohan:boss.slash_light", 1.6f, 1.4f);

        int roll = random.nextInt(100);
        String ambushAnim;
        if (roll < 45) {
            ambushAnim = "attack_slash_left";
        } else if (roll < 75 || state.groundSlamCooldown > 0) {
            ambushAnim = "attack_sweep_right";
        } else {
            ambushAnim = "attack_slash_straight";
            state.groundSlamCooldown = MathUtil.secondsToTicks(8.0 + random.nextDouble() * 4.0);
        }
        WardenCombatHandler.triggerSurpriseAttack(golem, state, ambushAnim, target);
    }

    public static void performEvasiveShadowHop(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Location threatLoc, Random random) {
        state.dashCooldown = MathUtil.secondsToTicks(3.75 + random.nextDouble() * 1.75);
        Location bossLoc = golem.getLocation();

        Vector evadeDir = bossLoc.toVector().subtract(threatLoc.toVector()).setY(0);
        if (evadeDir.lengthSquared() < 0.01) {
            evadeDir = bossLoc.getDirection().setY(0).multiply(-1.0);
        }
        evadeDir.normalize();

        if (random.nextInt(100) < 45) {
            double angleOffset = (random.nextBoolean() ? 1 : -1) * (Math.PI / 4.0);
            evadeDir = MathUtil.rotateAroundY(evadeDir, Math.toDegrees(angleOffset)).normalize();
        }

        double jumpDistance = 7.5 + (random.nextDouble() * 4.0);
        Location rawTargetLand = bossLoc.clone().add(evadeDir.clone().multiply(jumpDistance));
        Location targetLand = WardenLocationUtil.findSafeTeleportLocation(bossLoc, rawTargetLand, 1.4);
        if (!WardenLocationUtil.isSafeBossStandLocation(targetLand)) {
            targetLand = WardenLocationUtil.findSafeTeleportLocation(bossLoc, bossLoc.clone().add(evadeDir.clone().multiply(5.0)), 1.4);
            if (!WardenLocationUtil.isSafeBossStandLocation(targetLand)) {
                targetLand = WardenLocationUtil.findSafeTeleportLocation(bossLoc, bossLoc.clone().add(evadeDir.clone().multiply(5.0)), 1.2);
            }
        }

        Vector faceBackToThreat = threatLoc.toVector().subtract(targetLand.toVector()).setY(0).normalize();
        float faceYaw = MathUtil.getYaw(faceBackToThreat);
        targetLand.setYaw(faceYaw);
        targetLand.setPitch(0f);

        Location startFx = bossLoc.clone().add(0, 1.6, 0);
        bossLoc.getWorld().spawnParticle(Particle.PORTAL, startFx, 55, 0.8, 1.2, 0.8, 0.6);
        bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, startFx, 4, 0.6, 0.2, 0.6, 0);
        bossLoc.getWorld().spawnParticle(Particle.DUST, startFx, 40, 0.6, 0.8, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(60, 200, 255), 2.2f));
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.6f, 1.7f);
        bossLoc.getWorld().playSound(bossLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 1.5f);
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.8f);
        WardenAudio.playCustomSound(bossLoc, "haohan:boss.electric", 1.8f, 1.5f);

        WardenVFX.playTeleportShrinkEffect(plugin, golem, state);

        golem.teleport(targetLand);

        Location arriveFx = targetLand.clone().add(0, 1.4, 0);
        targetLand.getWorld().spawnParticle(Particle.PORTAL, arriveFx, 45, 0.7, 1.0, 0.7, 0.5);
        targetLand.getWorld().spawnParticle(Particle.SWEEP_ATTACK, arriveFx, 3, 0.5, 0.2, 0.5, 0);
        targetLand.getWorld().spawnParticle(Particle.DUST, arriveFx, 40, 0.5, 0.6, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.0f));
        targetLand.getWorld().playSound(targetLand, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 1.8f);
        targetLand.getWorld().playSound(targetLand, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.4f, 1.6f);
        WardenAudio.playCustomSound(targetLand, "haohan:boss.slash_light", 1.5f, 1.5f);

        WardenAnimationController.playModelAnimation(golem, state, "walk_backward", 0.08, 0.15, 1.2, true);
    }

    public static void performAgileDash(IronGolem golem, WardenState state, float targetYaw, double distXZ, Random random) {
        state.dashCooldown = MathUtil.secondsToTicks(3.25 + random.nextDouble() * 1.75);
        Location bossLoc = golem.getLocation();

        boolean dashLeft = random.nextBoolean();
        Vector rightVec = MathUtil.yawToRightVector(bossLoc.getYaw());
        Vector forwardVec = MathUtil.yawToDirection(bossLoc.getYaw());

        Vector dashDir = (dashLeft ? rightVec.multiply(-1.0) : rightVec).multiply(0.72)
                .add(forwardVec.multiply(distXZ > 4.5 ? 0.35 : -0.15)).normalize();

        Vector dashVel = dashDir.multiply(0.68).setY(0.24);
        Vector vel = golem.getVelocity();
        vel.setX(dashVel.getX());
        vel.setZ(dashVel.getZ());
        vel.setY(dashVel.getY());
        WardenFootworkController.apply3BlockStepAssist(golem, dashDir, vel);
        golem.setVelocity(vel);

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 1.4f);
        bossLoc.getWorld().playSound(bossLoc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.3f, 1.6f);
        bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, bossLoc.clone().add(0, 1.2, 0), 2, 0.4, 0.1, 0.4, 0);
        bossLoc.getWorld().spawnParticle(Particle.DUST, bossLoc.clone().add(0, 0.8, 0), 20, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.8f));
        WardenAudio.playCustomSound(bossLoc, "haohan:boss.slash_light", 1.4f, 1.4f);

        WardenAnimationController.playModelAnimation(golem, state, dashLeft ? "walk_left" : "walk_right", 0.08, 0.15, 1.3, true);
    }
}
