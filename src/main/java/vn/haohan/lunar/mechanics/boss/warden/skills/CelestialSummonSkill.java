package vn.haohan.lunar.mechanics.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.util.MathUtil;

import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenVFX;

import java.util.Random;

public final class CelestialSummonSkill {
    private CelestialSummonSkill() {}

    public static void triggerSummonSkill(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, Random random) {
        state.currentBehavior = WardenBehavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = false;
        state.summonSkillCooldown = MathUtil.secondsToTicks(30.0 + random.nextDouble() * 10.0);
        state.lockedSummonLocations.clear();

        Location bossLoc = golem.getLocation();
        Location targetLoc = target != null ? target.getLocation() : bossLoc;
        World world = bossLoc.getWorld();

        // 1. Calculate tactical retreat warp position (16m - 24m away from target)
        Vector awayDir = bossLoc.toVector().subtract(targetLoc.toVector()).setY(0);
        if (awayDir.lengthSquared() < 0.01) {
            awayDir = bossLoc.getDirection().setY(0).multiply(-1.0);
            if (awayDir.lengthSquared() < 0.01) {
                awayDir = new Vector(0, 0, -1);
            }
        }
        awayDir.normalize();

        // Add slight random diagonal angle (-30 to +30 deg)
        double angleOffset = (random.nextDouble() - 0.5) * (Math.PI / 3.0);
        double cos = Math.cos(angleOffset);
        double sin = Math.sin(angleOffset);
        Vector rotatedAway = new Vector(
                awayDir.getX() * cos - awayDir.getZ() * sin,
                0,
                awayDir.getX() * sin + awayDir.getZ() * cos
        ).normalize();

        double tpDist = 16.0 + (random.nextDouble() * 8.0);
        Location desiredWarp = targetLoc.clone().add(rotatedAway.multiply(tpDist));
        Location safeWarp = WardenLocationUtil.findSafeTeleportLocation(bossLoc, desiredWarp, 1.5);
        if (!WardenLocationUtil.isSafeBossStandLocation(safeWarp)) {
            safeWarp = WardenLocationUtil.findSafeTeleportLocation(bossLoc, targetLoc.clone().add(awayDir.multiply(14.0)), 1.4);
            if (!WardenLocationUtil.isSafeBossStandLocation(safeWarp)) {
                safeWarp = WardenLocationUtil.findSafeTeleportLocation(bossLoc, bossLoc.clone().add(awayDir.multiply(8.0)), 1.2);
            }
        }

        Vector toTarget = targetLoc.toVector().subtract(safeWarp.toVector()).setY(0).normalize();
        float faceYaw = MathUtil.getYaw(toTarget);
        safeWarp.setYaw(faceYaw);
        safeWarp.setPitch(0f);

        // Departure FX
        if (world != null) {
            Location startFx = bossLoc.clone().add(0, 1.8, 0);
            world.spawnParticle(Particle.PORTAL, startFx, 50, 0.8, 1.2, 0.8, 0.5);
            world.spawnParticle(Particle.SWEEP_ATTACK, startFx, 3, 0.4, 0.2, 0.4, 0);
            world.spawnParticle(Particle.DUST, startFx, 35, 0.6, 0.8, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.2f));
            world.playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.8f, 1.4f);
            world.playSound(bossLoc, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 1.5f);
            WardenAudio.playCustomSound(bossLoc, "haohan:boss.electric", 1.8f, 1.3f);
            WardenAudio.playCustomSound(bossLoc, "haohan:boss.sword_draw", 1.8f, 1.0f);
        }

        WardenVFX.playTeleportShrinkEffect(plugin, golem, state);

        golem.teleport(safeWarp);
        golem.setVelocity(new Vector(0, 0, 0));

        // Arrival FX
        if (world != null) {
            Location arriveFx = safeWarp.clone().add(0, 1.5, 0);
            world.spawnParticle(Particle.PORTAL, arriveFx, 50, 0.8, 1.2, 0.8, 0.5);
            world.spawnParticle(Particle.FLASH, arriveFx, 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
            world.spawnParticle(Particle.DUST, arriveFx, 45, 0.8, 0.8, 0.8, 0.0, new Particle.DustOptions(Color.fromRGB(90, 230, 255), 2.5f));
            world.playSound(safeWarp, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.6f);
            world.playSound(safeWarp, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.4f, 1.4f);
            WardenAudio.playCustomSound(safeWarp, "haohan:boss.magic", 2.0f, 1.0f);
            WardenAudio.playCustomSound(safeWarp, "haohan:boss.mortalblade_charge", 1.8f, 1.1f);
        }

        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_charge_summon", 0.30);
    }

    public static void handleSummonSpellExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, Random random) {
        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();

        float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
        float newYaw = MathUtil.normalizeAngle(currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 2.5f));
        golem.setRotation(newYaw, targetPitch);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(newYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }

        int chantDurationTicks = MathUtil.secondsToTicks(3.4);
        int barrageEndTicks = MathUtil.secondsToTicks(8.5);

        // Phase 1: Casting & Magic Circle Tracking (0 -> 3.4s)
        if (state.attackTicks <= chantDurationTicks) {
            float progress = (float) state.attackTicks / (float) chantDurationTicks;
            double currentRadius = progress * 1.6;
            double rotationAngle = state.attackTicks * (0.12 + (progress * 0.28));

            if (state.attackTicks % 2 == 0) {
                Location bossChantLoc = golemLoc.clone().add(golemLoc.getDirection().multiply(2.2)).add(0, 3.2, 0);
                golem.getWorld().spawnParticle(Particle.END_ROD, bossChantLoc, 3, 0.4, 0.4, 0.4, 0.02);
                golem.getWorld().spawnParticle(Particle.DUST, bossChantLoc, 6, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.8f));
            }

            if (state.attackTicks % 16 == 0) {
                golem.getWorld().playSound(golemLoc, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.9f + (progress * 0.8f));
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.magic", 1.8f, 0.8f + (progress * 0.6f));
            }

            for (Player p : golem.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                if (p.getLocation().distanceSquared(golemLoc) <= 45.0 * 45.0) {
                    Location pGround = WardenLocationUtil.adjustToTerrainSurface(p.getLocation(), 0.0);
                    WardenVFX.renderMagicCircle(pGround, currentRadius, rotationAngle, progress, Color.fromRGB(60, 190, 255));

                    if (state.attackTicks == 1) {
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.8f);
                    }
                }
            }

            // Target Lock Moment (at 3.4s / chant end)
            if (state.attackTicks == chantDurationTicks) {
                state.lockedSummonLocations.clear();
                for (Player p : golem.getWorld().getPlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                    if (p.getLocation().distanceSquared(golemLoc) <= 45.0 * 45.0) {
                        Location pGround = WardenLocationUtil.adjustToTerrainSurface(p.getLocation(), 0.0);
                        state.lockedSummonLocations.add(pGround.clone());

                        p.getWorld().playSound(pGround, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.8f, 1.6f);
                        p.getWorld().playSound(pGround, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.8f);
                        p.getWorld().spawnParticle(Particle.FLASH, pGround.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
                    }
                }

                golem.getWorld().playSound(golemLoc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
                golem.getWorld().playSound(golemLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.5f);
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.judgementcut", 2.0f, 1.0f);
            }
        }

        // Phase 2: Orbital Barrage Strikes on Locked Zones & Moving Targets (3.4s -> 8.5s)
        if (state.attackTicks > chantDurationTicks && state.attackTicks <= barrageEndTicks) {
            if (state.attackTicks % 3 == 0) {
                Location skySwordTip = golemLoc.clone().add(0, 6.8, 0);
                golem.getWorld().spawnParticle(Particle.END_ROD, skySwordTip, 6, 0.3, 0.6, 0.3, 0.05);
                golem.getWorld().spawnParticle(Particle.FLASH, skySwordTip, 1, 0.1, 0.1, 0.1, 0.0, Color.fromRGB(160, 230, 255));
            }

            int barrageElapsed = state.attackTicks - chantDurationTicks;

            // Trigger targeted strikes rhythmically every 7 ticks
            if (barrageElapsed % 7 == 0) {
                // Strike at locked locations with slight cascading radius
                for (Location lockedLoc : state.lockedSummonLocations) {
                    if (lockedLoc.getWorld() != null) {
                        double offX = (random.nextDouble() - 0.5) * 3.5;
                        double offZ = (random.nextDouble() - 0.5) * 3.5;
                        Location strikePoint = lockedLoc.clone().add(offX, 0, offZ);
                        new TargetedLightStrikeTask(golem, strikePoint).runTaskTimer(plugin, 1L, 1L);
                    }
                }

                // Also strike current player positions for active dodging
                for (Player p : golem.getWorld().getPlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                    if (p.getLocation().distanceSquared(golemLoc) <= 45.0 * 45.0) {
                        new TargetedLightStrikeTask(golem, p.getLocation().clone()).runTaskTimer(plugin, 1L, 1L);
                    }
                }
            }
        }

        // Phase 3: Blade Sheathing & Finish (8.5s -> 10.8s)
        if (state.attackTicks > barrageEndTicks && state.attackTicks <= state.attackTotalTicks) {
            if (state.attackTicks == MathUtil.secondsToTicks(9.8)) {
                golemLoc.getWorld().playSound(golemLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 1.0f);
                golemLoc.getWorld().playSound(golemLoc, Sound.BLOCK_CHAIN_HIT, 1.4f, 1.2f);
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.sword_sheath", 1.8f, 1.0f);
                Location sheathLoc = golemLoc.clone().add(golemLoc.getDirection().multiply(1.2)).add(0, 1.5, 0);
                golemLoc.getWorld().spawnParticle(Particle.DUST, sheathLoc, 20, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(120, 210, 255), 1.5f));
            }
        }

        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(1.1 + random.nextDouble() * 0.75);
            state.attackCooldown = MathUtil.secondsToTicks(1.75 + random.nextDouble() * 0.75);
            state.currentAttack = "";
            state.impaledTargetUUID = null;
            state.flingExecuted = false;
            state.lockedSummonLocations.clear();
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.35, 0.35, 1.0, true);
        }
    }
}
