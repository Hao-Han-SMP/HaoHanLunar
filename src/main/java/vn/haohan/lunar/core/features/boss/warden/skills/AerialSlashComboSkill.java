package vn.haohan.lunar.core.features.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.*;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.api.system.util.MathUtil;
import vn.haohan.lunar.core.features.boss.warden.WardenBehavior;
import vn.haohan.lunar.core.features.boss.warden.WardenState;
import vn.haohan.lunar.core.features.boss.warden.ai.WardenFootworkController;
import vn.haohan.lunar.core.features.boss.warden.combat.AerialSlashProjectileTask;
import vn.haohan.lunar.core.features.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.core.features.boss.warden.util.WardenBladeCalculator;
import vn.haohan.lunar.core.features.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.core.features.boss.warden.visual.WardenAudio;

import java.util.Random;

/**
 * 4-Slash Aerial Combo Skill ("skill_aerial_slash_combo").
 * Boss unleashes a rapid 4-hit slash combo.
 * Each slash forms in-place cycling states 1 -> 5 along the blade path, then launches forward at high speed (states 6 -> 7).
 */
public final class AerialSlashComboSkill {
    private AerialSlashComboSkill() {}

    // Keyframe tick timings for each of the 4 consecutive slashes
    public static final int SLASH_1_TICK = MathUtil.secondsToTicks(0.50); // Tick 10
    public static final int SLASH_2_TICK = MathUtil.secondsToTicks(1.10); // Tick 22
    public static final int SLASH_3_TICK = MathUtil.secondsToTicks(1.70); // Tick 34
    public static final int SLASH_4_TICK = MathUtil.secondsToTicks(2.30); // Tick 46
    public static final int TOTAL_TICKS = MathUtil.secondsToTicks(3.60);  // Tick 72

    public static void triggerSkill(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, Random random) {
        state.currentBehavior = WardenBehavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = true;
        state.aerialSlashComboCooldown = MathUtil.secondsToTicks(18.0 + random.nextDouble() * 6.0);
        state.shieldSwordSlamCooldown = MathUtil.secondsToTicks(16.0 + random.nextDouble() * 4.0);

        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_aerial_slash_combo", 0.15);
    }

    public static void handleExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target,
                                       float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;

        float currentYaw = golemLoc.getYaw();

        // 1. DYNAMIC ROTATION & TRACKING TOWARDS TARGET
        float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
        float turnSpeed = (state.attackTicks % 12 <= 5) ? 24.0f : 12.0f; // Snappy pivot right before each strike
        float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), turnSpeed);
        newYaw = MathUtil.normalizeAngle(newYaw);
        golem.setRotation(newYaw, targetPitch);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(newYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }

        // 2. FORWARD STEP ASSIST DURING SLASHES
        if (distXZ > 2.2) {
            boolean isNearStrike = (state.attackTicks == SLASH_1_TICK - 3 || state.attackTicks == SLASH_2_TICK - 3
                    || state.attackTicks == SLASH_3_TICK - 3 || state.attackTicks == SLASH_4_TICK - 3);
            if (isNearStrike) {
                Vector lunge = MathUtil.yawToDirection(newYaw).multiply(0.40).setY(0.08);
                Vector vel = golem.getVelocity().add(lunge);
                WardenFootworkController.apply3BlockStepAssist(golem, lunge, vel);
                golem.setVelocity(vel);
            }
        }

        // 3. EXECUTE THE 4 CONSECUTIVE SLASH WAVES
        if (state.attackTicks == SLASH_1_TICK) {
            executeSingleSlashWave(plugin, golem, state, target, 0, 14.5);
        } else if (state.attackTicks == SLASH_2_TICK) {
            executeSingleSlashWave(plugin, golem, state, target, 1, 14.5);
        } else if (state.attackTicks == SLASH_3_TICK) {
            executeSingleSlashWave(plugin, golem, state, target, 2, 14.5);
        } else if (state.attackTicks == SLASH_4_TICK) {
            // Finisher slash: heavier damage and wider wave
            executeSingleSlashWave(plugin, golem, state, target, 3, 18.5);
        }

        // 4. FINISH ATTACK COMBO & RECOVERY
        if (state.attackTicks >= TOTAL_TICKS) {
            state.prevBladeTip = null;
            state.prevBladeBase = null;

            if (state.queuedComboAttack != null && !state.queuedComboAttack.isEmpty()) {
                String nextCombo = state.queuedComboAttack;
                state.queuedComboAttack = "";
                WardenCombatHandler.executeSingleAttackPhase(golem, state, nextCombo, 0.10);
                return;
            }

            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.6 + random.nextDouble() * 0.5);
            state.attackCooldown = MathUtil.secondsToTicks(1.0 + random.nextDouble() * 0.6);
            state.currentAttack = "";
            state.isMovingAttack = false;
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    private static void executeSingleSlashWave(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state,
                                               Player target, int comboIndex, double waveDamage) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;

        // 1. Calculate the exact real-time 3D blade segment (Base hilt & Tip)
        WardenBladeCalculator.BladeSegment blade = WardenBladeCalculator.calculateBladeSegment(golem);

        Location spawnOrigin = (blade.mid != null && blade.mid.getWorld() != null)
                ? blade.mid.clone()
                : golemLoc.clone().add(0, 2.5, 0).add(golemLoc.getDirection().multiply(1.5));

        // 2. Calculate flight direction vector towards target
        Vector flightDir;
        if (target != null && target.isValid() && target.getGameMode() != GameMode.SPECTATOR && !target.isDead()) {
            Location aimLoc = target.getEyeLocation().clone().subtract(0, 0.35, 0);
            flightDir = aimLoc.toVector().subtract(spawnOrigin.toVector()).normalize();
        } else {
            flightDir = golemLoc.getDirection().clone().normalize();
        }

        // 3. Raw blade span vector from base to tip
        Vector rawBladeSpan;
        if (blade.base != null && blade.tip != null && blade.base.getWorld() != null) {
            rawBladeSpan = blade.tip.toVector().subtract(blade.base.toVector());
        } else {
            // Default angled slash span fallback (6.2 blocks span)
            float tilt = (comboIndex % 2 == 0) ? 0.75f : -0.75f;
            rawBladeSpan = new Vector(-flightDir.getZ(), tilt, flightDir.getX()).normalize().multiply(WardenBladeCalculator.BLADE_LENGTH);
        }

        // 4. Launch 3D Sword Aura Projectile ("Kiếm Khí")
        new AerialSlashProjectileTask(golem, spawnOrigin, flightDir, rawBladeSpan, comboIndex, waveDamage)
                .runTaskTimer(plugin, 1L, 1L);

        // 5. Strike audio & particle feedback at boss position
        world.playSound(spawnOrigin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.2f + (comboIndex * 0.1f));
        world.playSound(spawnOrigin, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, 1.4f);
        WardenAudio.playCustomSound(spawnOrigin, "haohan:boss.arcslash", 1.8f, 1.1f + (comboIndex * 0.1f));

        world.spawnParticle(Particle.SWEEP_ATTACK, spawnOrigin, 2, 0.4, 0.2, 0.4, 0);

        // 6. Close-range melee hit check (if target is within greatsword swing reach <= 6.2m)
        double meleeRadiusSq = 6.2 * 6.2;
        for (Player victim : world.getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || victim.getGameMode() == GameMode.CREATIVE
                    || !victim.isValid() || victim.isDead()) {
                continue;
            }

            Location vLoc = victim.getLocation();
            if (vLoc.distanceSquared(golemLoc) <= meleeRadiusSq) {
                Vector toVictim = vLoc.toVector().subtract(golemLoc.toVector()).normalize();
                if (golemLoc.getDirection().dot(toVictim) > 0.15) {
                    WardenCombatHandler.applyCombatDamage(victim, 12.0, golem);
                    Vector kb = toVictim.clone().multiply(0.42).setY(0.20);
                    victim.setVelocity(victim.getVelocity().add(kb));
                    victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 1.0f);
                }
            }
        }
    }
}
