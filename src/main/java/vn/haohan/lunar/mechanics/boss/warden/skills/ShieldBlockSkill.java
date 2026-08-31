package vn.haohan.lunar.mechanics.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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

public final class ShieldBlockSkill {
    private ShieldBlockSkill() {}

    public static void triggerShieldBlockSkill(IronGolem golem, WardenState state, Random random) {
        // Shorter, agile cooldown (1.8s - 2.8s) so boss can block consecutive projectile volleys
        state.shieldBlockCooldown = MathUtil.secondsToTicks(1.8 + random.nextDouble() * 1.0);
        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_shield_block", 0.12);

        Location loc = golem.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.8f, 0.9f);
            world.playSound(loc, Sound.ITEM_SHIELD_BLOCK, 1.6f, 1.0f);
            world.playSound(loc, Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.8f);
            WardenAudio.playCustomSound(loc, "haohan:boss.shield_thud", 1.8f, 1.0f);
        }
    }

    public static void handleShieldBlockExecution(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;
        float currentYaw = golemLoc.getYaw();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);

        // Continuous facing alignment to keep shield aimed directly towards the player
        float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
        float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 18.0f);
        newYaw = MathUtil.normalizeAngle(newYaw);
        golem.setRotation(newYaw, state.headPitch);

        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(newYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }

        // Defensive bracing footwork (slight backward stabilization if too close)
        if (distXZ < 2.2) {
            Vector backStep = MathUtil.yawToDirection(newYaw).multiply(-0.06).setY(0);
            golem.setVelocity(golem.getVelocity().add(backStep));
        } else {
            Vector slowDown = golem.getVelocity().multiply(0.5);
            golem.setVelocity(slowDown);
        }

        // Guard duration finished (~1.80s) -> lower shield smoothly back to IDLE
        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.4 + random.nextDouble() * 0.3);
            state.attackCooldown = MathUtil.secondsToTicks(0.5 + random.nextDouble() * 0.3);
            state.currentAttack = "";
            state.isMovingAttack = false;
            state.hitVictimsThisAttack.clear();

            world.playSound(golemLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.4f, 1.2f);
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    public static void handleParryCounter(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Entity damager, Location hitLoc) {
        World world = golem.getWorld();
        if (world == null) return;

        Location guardLoc = hitLoc != null ? hitLoc : golem.getLocation().add(golem.getLocation().getDirection().multiply(1.5)).add(0, 2.0, 0);

        // Heavy metallic parry sounds
        world.playSound(guardLoc, Sound.ITEM_SHIELD_BLOCK, 2.0f, 0.9f);
        world.playSound(guardLoc, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.8f);
        world.playSound(guardLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 1.2f);
        WardenAudio.playCustomSound(guardLoc, "haohan:boss.parry", 2.0f, 1.0f);
        WardenAudio.playCustomSound(guardLoc, "haohan:boss.shield_thud", 1.8f, 0.9f);

        // Flashy spark & deflect VFX
        world.spawnParticle(Particle.FLASH, guardLoc, 2, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
        world.spawnParticle(Particle.SWEEP_ATTACK, guardLoc, 3, 0.4, 0.2, 0.4, 0);
        world.spawnParticle(Particle.CRIT, guardLoc, 20, 0.4, 0.4, 0.4, 0.2);
        world.spawnParticle(Particle.DUST, guardLoc, 30, 0.6, 0.6, 0.6, 0.0,
                new Particle.DustOptions(Color.fromRGB(90, 220, 255), 2.2f));

        // Parry counter reaction against attacking player
        if (damager instanceof Player attacker) {
            if (attacker.getGameMode() != GameMode.SPECTATOR && attacker.isValid() && !attacker.isDead()) {
                attacker.setNoDamageTicks(0);
                // Reduced parry damage by 20% (10.0 -> 8.0)
                WardenCombatHandler.applyCombatDamage(attacker, 8.0, golem);

                // Repel attacker violently outward
                Vector outward = attacker.getLocation().toVector().subtract(golem.getLocation().toVector()).setY(0);
                if (outward.lengthSquared() > 0.001) {
                    outward.normalize().multiply(1.25).setY(0.36);
                } else {
                    outward = golem.getLocation().getDirection().setY(0).normalize().multiply(1.25).setY(0.36);
                }
                attacker.setVelocity(outward);

                // Apply Stagger & Slowness
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MathUtil.secondsToTicks(2.0), 2, false, false, true));
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, MathUtil.secondsToTicks(2.0), 1, false, false, true));

                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.2f, 0.85f);
                attacker.playSound(attacker.getLocation(), Sound.BLOCK_CHAIN_FALL, 1.4f, 0.8f);
            }
        }
    }
}
