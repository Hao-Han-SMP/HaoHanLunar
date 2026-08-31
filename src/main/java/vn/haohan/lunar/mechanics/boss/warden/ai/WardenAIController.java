package vn.haohan.lunar.mechanics.boss.warden.ai;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.data.IEntityData;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.skills.CelestialSummonSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldBlockPushSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldBlockSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldChargeSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldSwordSlamSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.PursuitTask;
import vn.haohan.lunar.mechanics.boss.warden.ui.WardenBossBar;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.util.MathUtil;

import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;

import java.util.Random;

public final class WardenAIController {
    private WardenAIController() {}

    public static void handleBossAI(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Random random) {
        WardenBossBar.updateBossBar(golem, state);
        Location golemLoc = golem.getLocation();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            if (modeledEntity.getAnimationLodHandler() != null && !Boolean.FALSE.equals(modeledEntity.getAnimationLodHandler().getEnabled())) {
                modeledEntity.getAnimationLodHandler().setEnabled(false);
            }
            if (modeledEntity.getBase() != null) {
                IEntityData data = modeledEntity.getBase().getData();
                if (data != null) {
                    if (Boolean.TRUE.equals(data.getBackCull())) data.setBackCull(false);
                    if (Boolean.TRUE.equals(data.getBlockedCull())) data.setBlockedCull(false);
                    if (Boolean.TRUE.equals(data.getVerticalCull())) data.setVerticalCull(false);
                }
            }
            modeledEntity.getModel(WardenConstants.MODEL_ID).ifPresent(m -> {
                if (!m.isInvisUpdate()) m.setInvisUpdate(true);
            });
        }

        if (state.summonSkillCooldown > 0) state.summonSkillCooldown--;
        if (state.shieldChargeCooldown > 0) state.shieldChargeCooldown--;
        if (state.shieldSwordSlamCooldown > 0) state.shieldSwordSlamCooldown--;
        if (state.groundSlamCooldown > 0) state.groundSlamCooldown--;
        if (state.thrustCooldown > 0) state.thrustCooldown--;
        if (state.shieldBlockPushCooldown > 0) state.shieldBlockPushCooldown--;
        if (state.shieldBlockCooldown > 0) state.shieldBlockCooldown--;
        if (state.dashCooldown > 0) state.dashCooldown--;
        if (state.zigZagPursuitCooldown > 0) state.zigZagPursuitCooldown--;

        Player target = WardenTargeting.selectBestTarget(golem, state);

        WardenFootworkController.handleAntiWallStuckAndClip(plugin, golem, state, target);

        if (target == null) {
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p) {
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                }
                state.impaledTargetUUID = null;
            }

            state.chaseStallTimer = 0;
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.currentAttack = "";
            state.queuedComboAttack = "";
            state.flingExecuted = false;
            state.isMovingAttack = false;

            Vector vel = golem.getVelocity();
            vel.setX(vel.getX() * 0.5);
            vel.setZ(vel.getZ() * 0.5);
            golem.setVelocity(vel);
            state.headPitch = MathUtil.lerpAngle(state.headPitch, 0f, 0.1f);
            state.headYawLocal = MathUtil.lerpAngle(state.headYawLocal, 0f, 0.1f);

            if (modeledEntity != null) {
                modeledEntity.setYHeadRot(golemLoc.getYaw());
                modeledEntity.setXHeadRot(0f);
                modeledEntity.setYBodyRot(golemLoc.getYaw());
            }

            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
            return;
        }

        if (state.isExecutingZigZagPursuit) {
            state.chaseStallTimer = 0;
            return;
        }

        Location targetEye = target.getEyeLocation();
        Location bossEye = golemLoc.clone().add(0, WardenConstants.BOSS_HEAD_HEIGHT, 0);

        Vector dirToTarget = targetEye.toVector().subtract(bossEye.toVector());
        double dx = dirToTarget.getX();
        double dy = dirToTarget.getY();
        double dz = dirToTarget.getZ();
        double distXZ = MathUtil.distance(0, 0, dx, dz);
        double targetHeightDiff = target.getLocation().getY() - golemLoc.getY();

        float targetYaw = MathUtil.getYaw(new Vector(dx, 0, dz));
        float targetPitch = MathUtil.getPitch(dirToTarget);

        if (state.attackCooldown > 0) {
            state.attackCooldown--;
        }

        // 1. ATTACK / SKILL STATE EXECUTION HAS HIGHEST PRIORITY
        // If boss is already executing a skill/attack, it must finish the skill completely and CANNOT be interrupted by block
        if (state.currentBehavior == WardenBehavior.ATTACKING || (state.currentAttack != null && !state.currentAttack.isEmpty())) {
            state.chaseStallTimer = 0;
            WardenCombatHandler.handleAttackExecution(plugin, golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        // 2. PREDICTIVE ARROW & PROJECTILE DEFENSE (Only triggers when boss is NOT performing an attack)
        if (state.shieldBlockCooldown <= 0) {
            Location bossCenter = golemLoc.clone().add(0, 4.0, 0);
            for (Entity nearby : golem.getWorld().getNearbyEntities(bossCenter, 30.0, 14.0, 30.0)) {
                if (nearby instanceof Projectile projectile && projectile.isValid()) {
                    if (projectile.getShooter() != null && !projectile.getShooter().equals(golem)) {
                        Vector pVel = projectile.getVelocity();
                        if (pVel.lengthSquared() > 0.15) {
                            Location pLoc = projectile.getLocation();
                            Vector toBoss = bossCenter.toVector().subtract(pLoc.toVector());
                            double dist = toBoss.length();
                            if (dist >= 1.0 && dist <= 30.0) {
                                Vector dir = pVel.clone().normalize();
                                double projAlong = toBoss.dot(dir);
                                if (projAlong > 0) {
                                    Vector closestPoint = pLoc.toVector().add(dir.clone().multiply(projAlong));
                                    double missDist = closestPoint.distance(bossCenter.toVector());
                                    if (missDist <= 4.2) {
                                        ShieldBlockSkill.triggerShieldBlockSkill(golem, state, random);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. LOW HP EXECUTE CHECK (Only triggers when thrust off cooldown and target is low HP)
        if (state.attackCooldown <= 0 && state.thrustCooldown <= 0 && distXZ <= 7.0) {
            boolean targetLowHp = target.getHealth() <= 12.0 || (target.getHealth() / target.getMaxHealth()) <= 0.35;
            if (targetLowHp) {
                state.thrustCooldown = MathUtil.secondsToTicks(8.0 + random.nextDouble() * 3.0);
                WardenCombatHandler.executeSingleAttackPhase(golem, state, "attack_thrust_fling", 0.15);
                return;
            }
        }

        // 4. CLOSE-RANGE PUNISHMENT: SHIELD BLOCK PUSH (When player approaches close: distXZ <= 3.8m)
        if (state.attackCooldown <= 0 && state.shieldBlockPushCooldown <= 0 && distXZ <= 3.8) {
            boolean playerRushing = target.isSprinting() || distXZ <= 2.8 || random.nextInt(100) < 85;
            if (playerRushing) {
                ShieldBlockPushSkill.triggerShieldBlockPushSkill(golem, state, random);
                return;
            } else {
                state.shieldBlockPushCooldown = MathUtil.secondsToTicks(1.5 + random.nextDouble() * 1.0);
            }
        }

        // 5. DEFENSIVE SHIELD BLOCK STANCE (Tactical guard & parry: distXZ <= 5.5m)
        if (state.attackCooldown <= 0 && state.shieldBlockCooldown <= 0 && distXZ <= 5.5) {
            boolean playerAttacking = target.isSprinting() || random.nextInt(100) < 30;
            if (playerAttacking) {
                ShieldBlockSkill.triggerShieldBlockSkill(golem, state, random);
                return;
            }
        }

        // 6. LONG-RANGE PURSUIT: DYNAMIC DISTANCE-SCALED ZIG-ZAG PHANTOM DASH (Distance >= 9.0m)
        if (distXZ >= 9.0 && distXZ <= 45.0 && state.zigZagPursuitCooldown <= 0 && !state.isExecutingZigZagPursuit) {
            state.chaseStallTimer = 0;
            PursuitTask.executeZigZagPhantomPursuit(plugin, golem, state, target, random);
            return;
        }

        // 7. ANTI-KITING / ANTI-STALL WARP AMBUSH (Distance 4.5m -> 9.0m)
        if (distXZ > 4.5 && distXZ < 9.0) {
            state.chaseStallTimer++;
            if (state.chaseStallTimer >= 65) {
                WardenEvasionController.executeAntiKitePhantomWarpStrike(plugin, golem, state, target, random);
                return;
            }
        } else {
            state.chaseStallTimer = 0;
        }

        // 8. RETREAT-FEINT COUNTER ATTACK
        if (state.currentBehavior == WardenBehavior.RETREAT && distXZ <= 3.2 && (target.isSprinting() || random.nextInt(100) < 30)) {
            if (state.attackCooldown <= 0) {
                WardenCombatHandler.triggerSurpriseAttack(golem, state, random.nextBoolean() ? "attack_sweep_right" : "attack_slash_straight", target);
                return;
            }
        }

        // Close-Range Evasive Shadow Hop (14 - 21.5 blocks leap)
        if (state.dashCooldown <= 0 && distXZ <= 2.0) {
            if (random.nextInt(100) < 25) {
                WardenEvasionController.performEvasiveShadowHop(plugin, golem, state, target.getLocation(), random);
                return;
            }
        }

        // Medium-Range Agile Flank Dash
        if (state.dashCooldown <= 0 && distXZ >= 3.5 && distXZ <= 7.5) {
            boolean playerSprintingIn = target.isSprinting() && distXZ < 5.5;
            boolean flanking = Math.abs(MathUtil.normalizeAngle(targetYaw - golemLoc.getYaw())) > 60.0f;
            boolean randomEvadeRoll = random.nextInt(100) < 16;

            if (playerSprintingIn || flanking || randomEvadeRoll) {
                WardenEvasionController.performAgileDash(golem, state, targetYaw, distXZ, random);
                return;
            }
        }

        // 9. CHECK ATTACK & SKILL TRIGGERS
        // Aerial Dual Weapon Throw & Meteor Slam: skill_shield_sword_slam (Distance 5.0m -> 28.0m, Reduced Chance ~32%)
        if (state.attackCooldown <= 0 && state.shieldSwordSlamCooldown <= 0 && distXZ >= 5.0 && distXZ <= 28.0) {
            if (random.nextInt(100) < 32) {
                ShieldSwordSlamSkill.triggerShieldSwordSlamSkill(plugin, golem, state, target, random);
                return;
            } else {
                state.shieldSwordSlamCooldown = MathUtil.secondsToTicks(3.5 + random.nextDouble() * 2.5);
            }
        }

        // Signature Ultimate Spell: skill_charge_summon (Cooldown ~30-40s)
        if (state.attackCooldown <= 0 && state.summonSkillCooldown <= 0 && distXZ >= 4.0 && distXZ <= 35.0) {
            if (random.nextInt(100) < 35) {
                CelestialSummonSkill.triggerSummonSkill(plugin, golem, state, target, random);
                return;
            } else {
                state.summonSkillCooldown = MathUtil.secondsToTicks(5.0 + random.nextDouble() * 3.0);
            }
        }

        // Safe Mid-to-Long Distance Shield Bulldozer Rush: skill_shield_charge (Distance 6.5m -> 22.0m, Cooldown ~12-16s)
        if (state.attackCooldown <= 0 && state.shieldChargeCooldown <= 0 && distXZ >= 6.5 && distXZ <= 22.0) {
            if (random.nextInt(100) < 45) {
                ShieldChargeSkill.triggerShieldChargeSkill(golem, state, random);
                return;
            } else {
                state.shieldChargeCooldown = MathUtil.secondsToTicks(2.5 + random.nextDouble() * 2.0);
            }
        }

        // Standard Attack Reach (Effective strike distance <= 4.8m or ground slam <= 6.5m or thrust <= 6.0m)
        if (state.attackCooldown <= 0 && (distXZ <= 4.8 || (distXZ <= 6.5 && (state.groundSlamCooldown <= 0 || state.thrustCooldown <= 0)))) {
            WardenCombatHandler.triggerAttack(plugin, golem, state, distXZ, targetHeightDiff, random);
            return;
        }

        // 10. TACTICAL SPACING & DYNAMIC FOOTWORK
        WardenFootworkController.handleTacticalFootwork(golem, state, target, targetYaw, targetPitch, distXZ, modeledEntity, targetEye, random);
    }
}
