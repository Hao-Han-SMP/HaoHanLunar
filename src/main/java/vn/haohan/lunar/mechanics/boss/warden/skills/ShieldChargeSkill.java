package vn.haohan.lunar.mechanics.boss.warden.skills;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.ai.WardenFootworkController;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.util.MathUtil;

import java.util.Random;

public final class ShieldChargeSkill {

    private ShieldChargeSkill() {}

    public static void triggerShieldChargeSkill(IronGolem golem, WardenState state, Random random) {
        state.shieldChargeCooldown = MathUtil.secondsToTicks(12.0 + random.nextDouble() * 5.0);
        WardenCombatHandler.executeSingleAttackPhase(golem, state, "skill_shield_charge", 0.15);
    }

    public static void handleShieldChargeExecution(IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, Random random) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;
        float currentYaw = golemLoc.getYaw();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);

        // Phase 1: Bracing Wind-up (0 -> 0.70s)
        if (state.attackTicks <= MathUtil.secondsToTicks(0.70)) {
            float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 10.0f);
            newYaw = MathUtil.normalizeAngle(newYaw);
            golem.setRotation(newYaw, state.headPitch);

            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
            }

            if (state.attackTicks % 2 == 0) {
                Location shieldFront = golemLoc.clone().add(golemLoc.getDirection().multiply(1.8)).add(0, 1.8, 0);
                world.spawnParticle(Particle.DUST, shieldFront, 12, 0.4, 0.6, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(60, 200, 255), 1.8f));
                world.spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 1, 0.2, 0.2, 0.2, 0);
            }
            if (state.attackTicks == 1) {
                WardenAudio.playCustomSound(golemLoc, "haohan:boss.shield_thud", 1.8f, 0.7f);
            }
        }

        // Phase 2: High-Speed Bulldozer Rush & Supersonic Wind Slipstream (0.70s -> 2.0s)
        if (state.attackTicks > MathUtil.secondsToTicks(0.70) && state.attackTicks <= MathUtil.secondsToTicks(2.0)) {
            float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 4.5f);
            newYaw = MathUtil.normalizeAngle(newYaw);
            golem.setRotation(newYaw, 0f);

            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
            }

            Vector forwardDir = MathUtil.yawToDirection(newYaw);
            Vector rightDir = MathUtil.yawToRightVector(newYaw);

            Vector rushVec = forwardDir.clone().multiply(0.68);
            Vector vel = golem.getVelocity();
            vel.setX(rushVec.getX());
            vel.setZ(rushVec.getZ());
            WardenFootworkController.apply3BlockStepAssist(golem, rushVec, vel);
            golem.setVelocity(vel);

            Location shieldFront = golemLoc.clone().add(forwardDir.clone().multiply(1.8)).add(0, 1.6, 0);

            // 1. Spawning Supersonic Radial Wind Streak Display Entities (Speed Lines / Anime Air Drag)
            if (state.attackTicks % 2 == 0) {
                spawnSupersonicWindDragStreaks(golem, shieldFront, forwardDir, rightDir, random);
            }

            // 2. High-speed wind particle shock cone
            world.spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 3, 0.8, 0.4, 0.8, 0);
            world.spawnParticle(Particle.DUST, shieldFront, 18, 0.6, 0.8, 0.6, 0.0,
                    new Particle.DustOptions(Color.fromRGB(180, 235, 255), 2.2f));
            world.spawnParticle(Particle.ELECTRIC_SPARK, shieldFront, 8, 0.8, 0.8, 0.8, 0.15);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, golemLoc.clone().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.02);

            if (state.attackTicks % 3 == 0) {
                world.playSound(golemLoc, Sound.ENTITY_IRON_GOLEM_STEP, 1.6f, 0.7f);
                world.playSound(golemLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.4f, 1.4f);
            }

            for (Player victim : world.getPlayers()) {
                if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                Location vLoc = victim.getLocation();
                double heightDiff = Math.abs(vLoc.getY() - golemLoc.getY());
                if (heightDiff > 3.5) continue;

                Vector toVictim = vLoc.toVector().subtract(golemLoc.toVector());
                double fwdProj = toVictim.dot(forwardDir);
                double lateralProj = Math.abs(toVictim.dot(rightDir));

                // Total Bulldozer Corridor: Forward -0.2m -> 4.5m, Lateral width <= 3.8m
                if (fwdProj >= -0.2 && fwdProj <= 4.8 && lateralProj <= 3.8) {
                    if (lateralProj <= 2.2) {
                        // 1. DIRECT FRONTAL BULLDOZER HITBOX:
                        // Continuously dragged along with the rushing shield and takes continuous damage over time
                        Vector dragVelocity = forwardDir.clone().multiply(0.70).setY(0.04);
                        victim.setVelocity(dragVelocity);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 2, false, false, false));

                        int lastHitTick = state.multiHitTickMap.getOrDefault(victim.getUniqueId(), -99);
                        // Continuous damage applied consistently every 4 ticks (0.20s)
                        if (state.attackTicks - lastHitTick >= 4) {
                            state.multiHitTickMap.put(victim.getUniqueId(), state.attackTicks);

                            double centerFactor = 1.0 - (lateralProj / 2.2);
                            double pulseDamage = 7.0 + (centerFactor * 5.0);

                            victim.setNoDamageTicks(0);
                            WardenCombatHandler.applyCombatDamage(victim, pulseDamage, golem);

                            victim.playSound(vLoc, Sound.ITEM_SHIELD_BLOCK, 1.4f, 0.8f);
                            victim.playSound(vLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.9f);
                            victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 12, 0.3, 0.4, 0.3, 0.2);
                            victim.getWorld().spawnParticle(Particle.DUST, vLoc.clone().add(0, 1.0, 0), 12, 0.3, 0.3, 0.3, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(255, 100, 50), 1.8f));
                        }
                    } else {
                        // 2. SIDE HITBOX (LEFT & RIGHT FLANKS):
                        // Light graze damage and continuous outward lateral push to the sides
                        double sideSign = Math.signum(toVictim.dot(rightDir));
                        if (sideSign == 0) sideSign = 1.0;

                        Vector sideDeflect = rightDir.clone().multiply(sideSign * 0.58).add(forwardDir.clone().multiply(0.18)).setY(0.18);
                        victim.setVelocity(sideDeflect);

                        int lastSideHitTick = state.sideGrazeHitTickMap.getOrDefault(victim.getUniqueId(), -99);
                        if (state.attackTicks - lastSideHitTick >= 6) {
                            state.sideGrazeHitTickMap.put(victim.getUniqueId(), state.attackTicks);

                            double sideGrazeDamage = 5.5;
                            victim.setNoDamageTicks(0);
                            WardenCombatHandler.applyCombatDamage(victim, sideGrazeDamage, golem);

                            victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, vLoc.clone().add(0, 1.0, 0), 1, 0.2, 0.1, 0.2, 0);
                            victim.getWorld().spawnParticle(Particle.DUST, vLoc.clone().add(0, 1.0, 0), 8, 0.2, 0.2, 0.2, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.4f));
                        }
                    }
                }
            }
        }

        // Phase 3: Shield Plant Brake & Ground Shockwave Finish (2.0s -> 2.65s)
        if (state.attackTicks > MathUtil.secondsToTicks(2.0) && state.attackTicks <= MathUtil.secondsToTicks(2.65)) {
            Vector vel = golem.getVelocity();
            vel.setX(vel.getX() * 0.35);
            vel.setZ(vel.getZ() * 0.35);
            golem.setVelocity(vel);

            if (state.attackTicks == MathUtil.secondsToTicks(2.05)) {
                Location slamCenter = golemLoc.clone().add(golemLoc.getDirection().multiply(2.0));
                slamCenter = WardenLocationUtil.adjustToTerrainSurface(slamCenter, 0.0);

                world.playSound(slamCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.8f, 0.75f);
                world.playSound(slamCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
                world.playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);

                world.spawnParticle(Particle.EXPLOSION, slamCenter.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.FLASH, slamCenter.clone().add(0, 0.4, 0), 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
                world.spawnParticle(Particle.DUST, slamCenter.clone().add(0, 0.2, 0), 40, 1.2, 0.2, 1.2, 0.0, new Particle.DustOptions(Color.fromRGB(90, 210, 255), 2.2f));

                for (Player victim : world.getPlayers()) {
                    if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;
                    Location vLoc = victim.getLocation();
                    if (vLoc.distanceSquared(slamCenter) <= 4.8 * 4.8) {
                        victim.setNoDamageTicks(0);
                        WardenCombatHandler.applyCombatDamage(victim, 18.0, golem);
                        Vector outward = vLoc.toVector().subtract(slamCenter.toVector()).normalize().multiply(0.65).setY(0.45);
                        victim.setVelocity(victim.getVelocity().add(outward));
                        victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 0.9f);
                    }
                }
            }
        }

        // 4. Recovery Phase
        if (state.attackTicks > state.attackTotalTicks) {
            state.currentBehavior = WardenBehavior.IDLE_STARE;
            state.behaviorTimer = MathUtil.secondsToTicks(0.8 + random.nextDouble() * 0.8);
            state.attackCooldown = MathUtil.secondsToTicks(0.75 + random.nextDouble() * 0.75);
            state.currentAttack = "";
            state.isMovingAttack = false;
            state.multiHitTickMap.clear();
            state.sideGrazeHitTickMap.clear();
            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    private static void spawnSupersonicWindDragStreaks(IronGolem golem, Location shieldFront, Vector forwardDir, Vector rightDir, Random random) {
        World world = shieldFront.getWorld();
        if (world == null) return;

        Vector upDir = new Vector(0, 1, 0);
        int streakCount = 6;

        for (int i = 0; i < streakCount; i++) {
            double angle = (2 * Math.PI / streakCount) * i + (random.nextDouble() * 0.35);
            double radius = 1.2 + random.nextDouble() * 0.8;

            // Offset on the perpendicular plane of charge
            Vector offset = rightDir.clone().multiply(Math.cos(angle) * radius)
                    .add(upDir.clone().multiply(Math.sin(angle) * radius));

            Location streakSpawn = shieldFront.clone().add(offset);

            // Wind streak direction: Streams backward and slightly outward in a supersonic cone
            Vector streamDir = forwardDir.clone().multiply(-1.0)
                    .add(offset.clone().normalize().multiply(0.35))
                    .normalize();

            float streakYaw = MathUtil.getYaw(streamDir);
            float streakPitch = (float) Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, streamDir.getY()))));
            streakSpawn.setYaw(streakYaw);
            streakSpawn.setPitch(streakPitch);

            try {
                ItemDisplay streak = world.spawn(streakSpawn, ItemDisplay.class, entity -> {
                    entity.setItemStack(new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
                    entity.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            new Quaternionf(),
                            new Vector3f(0.06f, 0.06f, 2.4f), // Elongated razor-sharp wind needle
                            new Quaternionf()
                    ));
                    entity.setBillboard(Display.Billboard.FIXED);
                });
                WardenEntityManager.registerTempEntity(streak);

                // Quick streaming fade (3 ticks lifetime)
                new BukkitRunnable() {
                    int life = 0;
                    final Location cur = streakSpawn.clone();

                    @Override
                    public void run() {
                        life++;
                        if (!streak.isValid() || life > 3 || golem.isDead()) {
                            WardenEntityManager.removeTempEntity(streak);
                            cancel();
                            return;
                        }
                        cur.add(streamDir.clone().multiply(0.65));
                        streak.teleport(cur);
                    }
                }.runTaskTimer(HaoHanLunarPlugin.getInstance(), 1L, 1L);

            } catch (Throwable ignored) {}
        }
    }
}
