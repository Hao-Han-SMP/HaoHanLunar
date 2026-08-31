package vn.haohan.lunar.mechanics.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.ai.WardenFootworkController;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenBladeCalculator;
import vn.haohan.lunar.util.MathUtil;

import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

import java.util.Random;

public final class ThrustFlingSkill {
    private ThrustFlingSkill() {}

    public static void handleThrustFlingExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();

        // Phase 1: Rapid forward aim & aggressive lunge tracking (0 -> 1.1s)
        if (state.attackTicks <= MathUtil.secondsToTicks(1.10)) {
            float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 16.0f);
            newYaw = MathUtil.normalizeAngle(newYaw);
            golem.setRotation(newYaw, targetPitch);

            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
            if (modeledEntity != null) {
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
                modeledEntity.setYBodyRot(newYaw);
            }

            double lungeSpeed;
            if (distXZ < 2.0) {
                lungeSpeed = 0.08;
            } else if (distXZ < 4.5) {
                lungeSpeed = 0.32;
            } else {
                lungeSpeed = 0.52;
            }

            Vector lungeVec = MathUtil.yawToDirection(newYaw).multiply(lungeSpeed);
            Vector vel = golem.getVelocity();
            vel.setX(lungeVec.getX());
            vel.setZ(lungeVec.getZ());
            WardenFootworkController.apply3BlockStepAssist(golem, lungeVec, vel);
            golem.setVelocity(vel);

            if (state.attackTicks % 3 == 0) {
                Location trail = golemLoc.clone().add(golemLoc.getDirection().multiply(1.5)).add(0, 1.5, 0);
                golem.getWorld().spawnParticle(Particle.CRIT, trail, 3, 0.2, 0.2, 0.2, 0.05);
            }
        }

        // Phase 2: Active Piercing Hit Window (0.50s -> 1.45s) - Wide forward piercing cone
        if (state.attackTicks >= MathUtil.secondsToTicks(0.50) && state.attackTicks <= MathUtil.secondsToTicks(1.45) && state.impaledTargetUUID == null && !state.attackHitDone) {
            Player caughtPlayer = null;
            double closestDist = Double.MAX_VALUE;

            for (Player p : golem.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                Location pLoc = p.getLocation();
                double dist = pLoc.distance(golemLoc);

                if (dist <= 8.5) {
                    Vector toP = pLoc.toVector().subtract(golemLoc.toVector()).normalize();
                    double dot = golemLoc.getDirection().dot(toP);
                    double heightDiff = Math.abs(pLoc.getY() - golemLoc.getY());

                    if (heightDiff < 4.5) {
                        boolean hitAngle = (dist <= 3.5) ? (dot > -0.40) : (dot > 0.15);

                        if (hitAngle && dist < closestDist) {
                            closestDist = dist;
                            caughtPlayer = p;
                        }
                    }
                }
            }

            if (caughtPlayer != null) {
                state.attackHitDone = true;
                state.impaledTargetUUID = caughtPlayer.getUniqueId();
                golem.setVelocity(new Vector(0, 0, 0));

                Location strikeEffectPoint = golemLoc.clone().add(golemLoc.getDirection().multiply(3.8)).add(0, 2.2, 0);

                // Low HP Execute Check (If health <= 12.0 HP / 6 hearts, or < 35% HP -> Lethal Execution!)
                boolean isExecute = caughtPlayer.getHealth() <= 12.0 || (caughtPlayer.getHealth() / caughtPlayer.getMaxHealth()) <= 0.35;
                double initialDamage = isExecute ? 120.0 : 25.0;

                caughtPlayer.setNoDamageTicks(0);
                WardenCombatHandler.applyCombatDamage(caughtPlayer, initialDamage, golem);

                golem.getWorld().playSound(strikeEffectPoint, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.8f, 0.5f);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.5f);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.4f, 1.6f);
                WardenAudio.playCustomSound(strikeEffectPoint, "haohan:boss.murasama", 1.8f, 1.2f);
                WardenAudio.playCustomSound(strikeEffectPoint, "haohan:boss.slash_heavy", 1.8f, 0.8f);

                golem.getWorld().spawnParticle(Particle.CRIT, strikeEffectPoint, 45, 0.5, 0.5, 0.5, 0.25);
                golem.getWorld().spawnParticle(Particle.DUST, strikeEffectPoint, 50, 0.5, 0.5, 0.5, 0.0, new Particle.DustOptions(Color.RED, 2.5f));
                golem.getWorld().spawnParticle(Particle.FLASH, strikeEffectPoint, 2, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
            } else if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
                Location strikeEffectPoint = golemLoc.clone().add(golemLoc.getDirection().multiply(4.0)).add(0, 2.0, 0);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ITEM_TRIDENT_THROW, 1.5f, 0.7f);
                WardenAudio.playCustomSound(strikeEffectPoint, "haohan:boss.slash_light", 1.5f, 1.1f);
            }
        }

        int flingTick = MathUtil.secondsToTicks(2.40);

        // Phase 3: Continuous Impalement Hold & Bleed (1.45s -> 2.4s)
        if (state.attackTicks > state.attackHitTick && state.attackTicks < flingTick) {
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p && p.isValid() && !p.isDead()) {
                    WardenBladeCalculator.BladeSegment blade = WardenBladeCalculator.calculateBladeSegment(golem);

                    Location swordImpalePoint;
                    if (blade != null && blade.base != null && blade.tip != null) {
                        swordImpalePoint = blade.getPointAt(0.88);
                    } else {
                        swordImpalePoint = golemLoc.clone().add(golemLoc.getDirection().multiply(4.2)).add(0, 2.4, 0);
                    }

                    Location pinLoc = swordImpalePoint.clone().subtract(0, 0.95, 0);
                    p.teleport(pinLoc);
                    p.setVelocity(new Vector(0, 0.0, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MathUtil.secondsToTicks(0.50), 4, false, false, false));

                    if (state.attackTicks % MathUtil.secondsToTicks(0.20) == 0) {
                        p.setNoDamageTicks(0);
                        WardenCombatHandler.applyCombatDamage(p, 4.0, golem);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                        WardenAudio.playCustomSound(p.getLocation(), "haohan:boss.slash_light", 1.2f, 1.4f);
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.9, 0), 15, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(Color.MAROON, 1.8f));
                    }
                } else {
                    state.impaledTargetUUID = null;
                }
            }
        }

        // Phase 4: Fling Upward & Mortal Blade Burst (2.4s)
        if (state.attackTicks >= flingTick && !state.flingExecuted) {
            state.flingExecuted = true;
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p && p.isValid() && !p.isDead()) {
                    Location flingLoc = p.getLocation();
                    p.setNoDamageTicks(0);
                    WardenCombatHandler.applyCombatDamage(p, 30.0, golem);

                    Vector flingDir = golemLoc.getDirection().multiply(2.2).setY(1.15);
                    p.setVelocity(flingDir);

                    p.getWorld().playSound(flingLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);
                    p.getWorld().playSound(flingLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.5f);
                    WardenAudio.playCustomSound(flingLoc, "haohan:boss.mortalblade_whoosh", 1.8f, 0.8f);
                    WardenAudio.playCustomSound(flingLoc, "haohan:boss.judgementcut", 1.6f, 1.1f);
                    p.getWorld().spawnParticle(Particle.EXPLOSION, flingLoc, 1, 0, 0, 0, 0);
                    p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, flingLoc, 5, 0.5, 0.5, 0.5, 0);
                    p.getWorld().spawnParticle(Particle.DUST, flingLoc, 40, 0.6, 0.6, 0.6, 0.0, new Particle.DustOptions(Color.RED, 2.0f));
                }
                state.impaledTargetUUID = null;
            }
        }

        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.9 + random.nextDouble() * 0.6);
            state.attackCooldown = MathUtil.secondsToTicks(1.75 + random.nextDouble() * 1.0);
            state.currentAttack = "";
            state.impaledTargetUUID = null;
            state.flingExecuted = false;
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }
}
