package vn.haohan.lunar.mechanics.boss.warden.combat;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.skills.CelestialSummonSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.GroundSlamSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldBlockPushSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldBlockSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldChargeSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ShieldSwordSlamSkill;
import vn.haohan.lunar.mechanics.boss.warden.skills.ThrustFlingSkill;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.util.MathUtil;
import vn.haohan.lunar.mechanics.boss.warden.ai.WardenFootworkController;

import java.util.Random;

public final class WardenCombatHandler {
    private WardenCombatHandler() {}

    public static void handleAttackExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();

        state.attackTicks++;

        float currentYaw = golemLoc.getYaw();

        float rawDesiredLocalYaw = MathUtil.normalizeAngle(targetYaw - currentYaw);
        float desiredLocalYaw = Math.max(-75f, Math.min(75f, rawDesiredLocalYaw));
        float desiredPitch = Math.max(-60f, Math.min(60f, targetPitch));
        state.headYawLocal = MathUtil.lerpAngle(state.headYawLocal, desiredLocalYaw, 0.35f);
        state.headPitch = MathUtil.lerpAngle(state.headPitch, desiredPitch, 0.25f);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
            modeledEntity.setYBodyRot(currentYaw);
        }

        if (state.currentAttack.equals("skill_shield_sword_slam")) {
            ShieldSwordSlamSkill.handleShieldSwordSlamExecution(plugin, golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        if (state.currentAttack.equals("skill_charge_summon")) {
            CelestialSummonSkill.handleSummonSpellExecution(plugin, golem, state, target, targetYaw, targetPitch, random);
            return;
        }

        if (state.currentAttack.equals("skill_shield_charge")) {
            ShieldChargeSkill.handleShieldChargeExecution(golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        if (state.currentAttack.equals("skill_shield_block_push")) {
            ShieldBlockPushSkill.handleShieldBlockPushExecution(plugin, golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        if (state.currentAttack.equals("skill_shield_block")) {
            ShieldBlockSkill.handleShieldBlockExecution(plugin, golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        if (state.currentAttack.equals("attack_thrust_fling")) {
            ThrustFlingSkill.handleThrustFlingExecution(plugin, golem, state, target, targetYaw, targetPitch, distXZ, random);
            return;
        }

        if (state.attackTicks < state.attackHitTick) {
            float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 14.0f);
            newYaw = MathUtil.normalizeAngle(newYaw);
            golem.setRotation(newYaw, targetPitch);

            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
            }

            if (distXZ > 2.0) {
                double stepIntensity = (state.attackTicks >= state.attackHitTick - 4) ? 0.32 : 0.16;
                Vector moveVec = MathUtil.yawToDirection(newYaw).multiply(stepIntensity);
                Vector vel = golem.getVelocity();
                vel.setX(moveVec.getX());
                vel.setZ(moveVec.getZ());
                WardenFootworkController.apply3BlockStepAssist(golem, moveVec, vel);
                golem.setVelocity(vel);
            }

            if (state.currentAttack.equals("attack_slash_straight") && state.attackTicks == state.attackHitTick - 4) {
                Vector lunge = MathUtil.yawToDirection(newYaw).multiply(0.52).setY(0.14);
                Vector vel = golem.getVelocity().add(lunge);
                WardenFootworkController.apply3BlockStepAssist(golem, lunge, vel);
                golem.setVelocity(vel);
            }
        }

        if (state.currentAttack.equals("attack_slash_straight")) {
            if (state.attackTicks >= state.attackHitTick && !state.attackHitDone) {
                state.attackHitDone = true;
                GroundSlamSkill.executeGroundSlamAOE(plugin, golem, random);
            }
        } else {
            int startStrikeTick = Math.max(1, state.attackHitTick - 1);
            int endStrikeTick = state.attackHitTick + 4;
            if (state.attackTicks >= startStrikeTick && state.attackTicks <= endStrikeTick) {
                executeMultiTickSlashStrike(plugin, golem, state, target);
            }
        }

        if (state.attackTicks >= state.attackTotalTicks) {
            state.prevBladeTip = null;
            state.prevBladeBase = null;

            if (state.queuedComboAttack != null && !state.queuedComboAttack.isEmpty()) {
                String nextCombo = state.queuedComboAttack;
                state.queuedComboAttack = "";
                executeSingleAttackPhase(golem, state, nextCombo, 0.10);
                return;
            }

            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.7 + random.nextDouble() * 0.6);
            state.attackCooldown = MathUtil.secondsToTicks(0.9 + random.nextDouble() * 0.8);
            state.currentAttack = "";
            state.isMovingAttack = false;
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    public static void executeSingleAttackPhase(IronGolem golem, WardenState state, String attack, double crossFadeTime) {
        state.currentBehavior = WardenBehavior.ATTACKING;
        state.currentAttack = attack;
        state.attackTicks = 0;
        state.attackHitDone = false;
        state.hitVictimsThisAttack.clear();
        state.multiHitTickMap.clear();
        state.sideGrazeHitTickMap.clear();
        state.currentAttackTotalDamage = 0.0;
        state.isMovingAttack = true;
        state.prevBladeTip = null;
        state.prevBladeBase = null;

        switch (attack) {
            case "attack_slash_left":
                state.attackTotalTicks = MathUtil.secondsToTicks(1.2);
                state.attackHitTick = MathUtil.secondsToTicks(0.5);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.15, 1.15, true);
                break;
            case "attack_sweep_right":
                state.attackTotalTicks = MathUtil.secondsToTicks(1.4);
                state.attackHitTick = MathUtil.secondsToTicks(0.7);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.20, 1.0, true);
                break;
            case "attack_slash_straight":
                state.attackTotalTicks = MathUtil.secondsToTicks(1.6);
                state.attackHitTick = MathUtil.secondsToTicks(0.83);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.25, 0.9, true);
                break;
            case "attack_thrust_fling":
                state.attackTotalTicks = MathUtil.secondsToTicks(3.25);
                state.attackHitTick = MathUtil.secondsToTicks(1.1);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.20, 1.05, true);
                break;
            case "skill_shield_charge":
                state.attackTotalTicks = MathUtil.secondsToTicks(2.65);
                state.attackHitTick = MathUtil.secondsToTicks(0.70);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.15, 1.0, true);
                break;
            case "skill_shield_block_push":
                state.attackTotalTicks = MathUtil.secondsToTicks(1.35);
                state.attackHitTick = MathUtil.secondsToTicks(0.45);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.12, 1.15, true);
                break;
            case "skill_shield_block":
                state.attackTotalTicks = MathUtil.secondsToTicks(1.80);
                state.attackHitTick = MathUtil.secondsToTicks(0.0);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.15, 1.0, true);
                break;
            case "skill_shield_sword_slam":
                state.attackTotalTicks = MathUtil.secondsToTicks(5.70);
                state.attackHitTick = MathUtil.secondsToTicks(4.4);
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.20, 1.0, true);
                break;
            case "skill_charge_summon":
                state.currentBehavior = WardenBehavior.ATTACKING;
                state.isMovingAttack = false;
                state.attackTotalTicks = MathUtil.secondsToTicks(10.8);
                state.attackHitTick = MathUtil.secondsToTicks(3.4);
                state.lockedSummonLocations.clear();
                WardenAnimationController.playModelAnimation(golem, state, attack, crossFadeTime, 0.25, 1.0, true);
                break;
            default:
                state.attackTotalTicks = MathUtil.secondsToTicks(1.0);
                state.attackHitTick = MathUtil.secondsToTicks(0.5);
                WardenAnimationController.playModelAnimation(golem, state, "idle", crossFadeTime, crossFadeTime, 1.0, true);
                break;
        }
    }

    public static void executeMultiTickSlashStrike(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target) {
        Location bossLoc = golem.getLocation();
        boolean isPrimaryHitTick = (state.attackTicks == state.attackHitTick);

        if (isPrimaryHitTick) {
            state.attackHitDone = true;
            Location strikeCenter = bossLoc.clone().add(bossLoc.getDirection().multiply(3.8)).add(0, 1.8, 0);

            bossLoc.getWorld().playSound(strikeCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.6f, 0.8f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.6f, 0.65f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.3f, 0.85f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.8f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);

            WardenAudio.playCustomSound(strikeCenter, "haohan:boss.slash_heavy", 1.8f, 0.85f);
            WardenAudio.playCustomSound(strikeCenter, "haohan:boss.arcslash", 1.6f, 1.0f);
            if (state.currentAttack.equals("attack_sweep_right")) {
                WardenAudio.playCustomSound(strikeCenter, "haohan:boss.murasama", 1.6f, 1.0f);
            }

            Vector forwardBurst = bossLoc.getDirection().multiply(0.28).setY(0);
            golem.setVelocity(golem.getVelocity().add(forwardBurst));

            Location waveStart = bossLoc.clone().add(bossLoc.getDirection().clone().setY(0).normalize().multiply(2.2));
            waveStart.setY(bossLoc.getY() + 0.6);
            new CrescentBladeWaveTask(golem, waveStart, bossLoc.getDirection(), state.currentAttack.equals("attack_sweep_right")).runTaskTimer(plugin, 1L, 1L);
        }

        double hitRadiusSq = 7.6 * 7.6;
        // Reduced base damage by 20% (Sweep: 22.0 -> 17.6, Slash: 18.5 -> 14.8)
        double damageAmount = state.currentAttack.equals("attack_sweep_right") ? 17.6 : 14.8;

        for (Player victim : bossLoc.getWorld().getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;
            if (state.hitVictimsThisAttack.contains(victim.getUniqueId())) continue;

            Location vLoc = victim.getLocation();
            if (vLoc.distanceSquared(bossLoc) <= hitRadiusSq) {
                Vector toVictim = vLoc.toVector().subtract(bossLoc.toVector()).normalize();
                double dot = bossLoc.getDirection().dot(toVictim);

                if (dot > -0.25) {
                    state.hitVictimsThisAttack.add(victim.getUniqueId());
                    applyCombatDamage(victim, damageAmount, golem);

                    Vector kb = toVictim.clone().multiply(0.60).setY(0.28);
                    victim.setVelocity(victim.getVelocity().add(kb));
                    victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                    victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.15);
                }
            }
        }
    }

    public static void applyCombatDamage(Player victim, double damage, IronGolem golem) {
        if (victim == null || !victim.isValid() || victim.isDead() || victim.getGameMode() == GameMode.SPECTATOR || victim.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        victim.setNoDamageTicks(0);
        victim.damage(damage, golem);

        double totalComboDmg = damage;
        HaoHanLunarPlugin plugin = HaoHanLunarPlugin.getInstance();
        if (plugin != null && plugin.getLunarWardenMechanic() != null && golem != null) {
            WardenState state = plugin.getLunarWardenMechanic().getBossStates().get(golem.getUniqueId());
            if (state != null) {
                state.currentAttackTotalDamage += damage;
                totalComboDmg = state.currentAttackTotalDamage;
            }
        }

        Component dmgMsg = Component.text("⚔ -", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text(String.format("%.1f", damage), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ❤", NamedTextColor.RED))
                .append(Component.text(" [Tổng: -", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(String.format("%.1f", totalComboDmg), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" ❤]", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" [Lunar Warden]", NamedTextColor.GRAY));
        victim.sendActionBar(dmgMsg);
    }

    public static void triggerSurpriseAttack(IronGolem golem, WardenState state, String attackPattern, Player target) {
        if ("attack_slash_straight".equals(attackPattern)) {
            state.groundSlamCooldown = MathUtil.secondsToTicks(8.0 + new Random().nextDouble() * 4.0);
        } else if ("attack_thrust_fling".equals(attackPattern)) {
            state.thrustCooldown = MathUtil.secondsToTicks(8.0 + new Random().nextDouble() * 3.0);
        }
        state.queuedComboAttack = attackPattern;
        state.attackTicks = state.attackTotalTicks; 
        state.behaviorTimer = 0;
    }

    public static void triggerAttack(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, double distXZ, double targetHeightDiff, Random random) {
        String attack;
        if (state.groundSlamCooldown <= 0 && distXZ <= 6.5 && random.nextInt(100) < 30) { 
            attack = "attack_slash_straight";
            state.groundSlamCooldown = MathUtil.secondsToTicks(8.0 + random.nextDouble() * 4.0); 
        } else if (state.thrustCooldown <= 0 && (targetHeightDiff > 1.8 || (distXZ <= 6.0 && random.nextInt(100) < 35))) {
            attack = "attack_thrust_fling";
            state.thrustCooldown = MathUtil.secondsToTicks(8.0 + random.nextDouble() * 3.0);
        } else {
            attack = random.nextBoolean() ? "attack_slash_left" : "attack_sweep_right"; 
        }

        executeSingleAttackPhase(golem, state, attack, 0.15);
    }
}
