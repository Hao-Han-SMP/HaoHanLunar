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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.util.MathUtil;

import java.util.Random;

public final class ShieldBlockPushSkill {
    private ShieldBlockPushSkill() {}

    public static void triggerShieldBlockPushSkill(IronGolem golem, WardenState state, Random random) {
        state.shieldBlockPushCooldown = MathUtil.secondsToTicks(8.5 + random.nextDouble() * 3.5);
        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_shield_block_push", 0.12);
    }

    public static void handleShieldBlockPushExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;
        float currentYaw = golemLoc.getYaw();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);

        // Phase 1: Rapid Facing Alignment & Defensive Shield Bracing (0 -> 0.45s)
        if (state.attackTicks <= state.attackHitTick) {
            float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 16.0f);
            newYaw = MathUtil.normalizeAngle(newYaw);
            golem.setRotation(newYaw, state.headPitch);

            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
            }

            if (state.attackTicks == 1) {
                world.playSound(golemLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 0.9f);
                world.playSound(golemLoc, Sound.ITEM_SHIELD_BLOCK, 1.4f, 1.1f);
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.shield_thud", 1.6f, 0.9f);
            }

            if (state.attackTicks % 2 == 0) {
                Location shieldFront = golemLoc.clone().add(golemLoc.getDirection().multiply(1.5)).add(0, 1.8, 0);
                world.spawnParticle(Particle.DUST, shieldFront, 8, 0.3, 0.5, 0.3, 0.0,
                        new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.6f));
            }
        }

        // Phase 2: Explosive Shield Push & Heavy Repelling Shockwave (Hit Tick: 0.45s)
        if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
            state.attackHitDone = true;

            Location impactCenter = golemLoc.clone().add(golemLoc.getDirection().multiply(2.2)).add(0, 1.6, 0);

            world.playSound(impactCenter, Sound.ITEM_SHIELD_BLOCK, 1.8f, 0.65f);
            world.playSound(impactCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.7f, 0.9f);
            world.playSound(impactCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.75f);
            world.playSound(impactCenter, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.5f, 1.3f);
            world.playSound(impactCenter, Sound.BLOCK_ANVIL_LAND, 1.4f, 0.7f);
            WardenAudio.playCustomSound(impactCenter, "haohan:boss.shield_thud", 1.8f, 0.75f);
            WardenAudio.playCustomSound(impactCenter, "haohan:boss.parry", 1.8f, 0.85f);
            WardenAudio.playCustomSound(impactCenter, "haohan:boss.slash_heavy", 1.5f, 0.8f);

            world.spawnParticle(Particle.FLASH, impactCenter, 2, 0.15, 0.15, 0.15, 0.0, Color.WHITE);
            world.spawnParticle(Particle.SWEEP_ATTACK, impactCenter, 4, 0.6, 0.2, 0.6, 0);
            world.spawnParticle(Particle.EXPLOSION, impactCenter, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.DUST, impactCenter, 40, 0.8, 0.5, 0.8, 0.0,
                    new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.4f));
            world.spawnParticle(Particle.DUST, impactCenter, 25, 0.6, 0.4, 0.6, 0.0,
                    new Particle.DustOptions(Color.WHITE, 1.8f));

            Vector fwdBurst = golemLoc.getDirection().multiply(0.24).setY(0);
            golem.setVelocity(golem.getVelocity().add(fwdBurst));

            double pushRadiusSq = 4.8 * 4.8;
            Vector bossFacing = golemLoc.getDirection().setY(0).normalize();

            for (Player victim : world.getPlayers()) {
                if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                Location vLoc = victim.getLocation();
                if (vLoc.distanceSquared(golemLoc) <= pushRadiusSq) {
                    Vector toVictim = vLoc.toVector().subtract(golemLoc.toVector()).setY(0);
                    double dist = toVictim.length();
                    Vector pushDir;

                    if (dist > 0.001) {
                        pushDir = toVictim.clone().normalize();
                    } else {
                        pushDir = bossFacing.clone();
                    }

                    double dot = bossFacing.dot(pushDir);
                    // Hit victims in front 180 degrees or within close radius (<= 2.8m)
                    if (dot > -0.25 || dist <= 2.8) {
                        state.hitVictimsThisAttack.add(victim.getUniqueId());

                        victim.setNoDamageTicks(0);
                        // Reduced shield push damage by 20% (18.0 -> 14.4)
                        WardenCombatHandler.applyCombatDamage(victim, 14.4, golem);

                        // Powerful directional push back
                        Vector outwardVel = pushDir.clone().multiply(1.35).setY(0.38);
                        victim.setVelocity(outwardVel);

                        // Disorienting brief slowness to prevent immediate re-approach
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MathUtil.secondsToTicks(2.5), 2, false, false, true));

                        victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                        victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 16, 0.3, 0.3, 0.3, 0.2);
                        victim.getWorld().spawnParticle(Particle.DUST, vLoc.clone().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.0,
                                new Particle.DustOptions(Color.fromRGB(80, 210, 255), 1.8f));
                    }
                }
            }
        }

        // Phase 3: Recovery & Transition Back to IDLE
        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.6 + random.nextDouble() * 0.4);
            state.attackCooldown = MathUtil.secondsToTicks(0.8 + random.nextDouble() * 0.6);
            state.currentAttack = "";
            state.isMovingAttack = false;
            state.hitVictimsThisAttack.clear();
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }
}
