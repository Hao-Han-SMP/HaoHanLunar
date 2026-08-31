package vn.haohan.lunar.mechanics.boss.warden.ai;

import com.ticxo.modelengine.api.entity.BaseEntity;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.util.MathUtil;

import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenVFX;

import java.util.Random;

public final class WardenFootworkController {
    private WardenFootworkController() {}

    /**
     * 3-Block Step-Height Navigation Assist
     */
    public static void apply3BlockStepAssist(IronGolem golem, Vector moveVelocity, Vector entityVelocity) {
        if (moveVelocity.lengthSquared() < 0.0001) return;
        Location currentLoc = golem.getLocation();
        World world = currentLoc.getWorld();
        if (world == null) return;

        Vector dir = moveVelocity.clone().setY(0).normalize();
        Location aheadLoc = currentLoc.clone().add(dir.multiply(0.95));

        int feetY = currentLoc.getBlockY();
        int aheadX = aheadLoc.getBlockX();
        int aheadZ = aheadLoc.getBlockZ();

        int highestObstacleY = -1;
        for (int checkY = feetY + 3; checkY >= feetY; checkY--) {
            Block b = world.getBlockAt(aheadX, checkY, aheadZ);
            if (!b.isPassable() && b.getType().isSolid()) {
                highestObstacleY = checkY;
                break;
            }
        }

        if (highestObstacleY != -1) {
            boolean clearHeadroom = true;
            for (int clearY = highestObstacleY + 1; clearY <= highestObstacleY + 3; clearY++) {
                Block headBlock = world.getBlockAt(aheadX, clearY, aheadZ);
                if (!headBlock.isPassable() && headBlock.getType().isSolid()) {
                    clearHeadroom = false;
                    break;
                }
            }

            if (clearHeadroom) {
                double targetStepY = highestObstacleY + 1.0;
                double heightDiff = targetStepY - currentLoc.getY();

                if (heightDiff > 0.15 && heightDiff <= 3.25) {
                    if (heightDiff <= 1.1) {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.42));
                    } else if (heightDiff <= 2.1) {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.58));
                    } else {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.76));
                    }

                    if (currentLoc.getY() + 0.45 >= targetStepY - 0.2) {
                        Location stepUpLoc = currentLoc.clone();
                        stepUpLoc.setY(targetStepY);
                        golem.teleport(stepUpLoc);
                    }
                }
            }
        }
    }

    public static void handleAntiWallStuckAndClip(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target) {
        Location golemLoc = golem.getLocation();
        World world = golem.getWorld();
        if (world == null) return;

        Block feetBlock = golemLoc.getBlock();
        Block chestBlock = golemLoc.clone().add(0, 1.1, 0).getBlock();
        Block headBlock = golemLoc.clone().add(0, 2.1, 0).getBlock();

        boolean inWall = (!feetBlock.isPassable() && feetBlock.getType().isSolid()) ||
                         (!chestBlock.isPassable() && chestBlock.getType().isSolid()) ||
                         (!headBlock.isPassable() && headBlock.getType().isSolid());

        if (inWall) {
            state.insideWallTicks++;
            if (state.insideWallTicks >= 4) {
                Location safeOut = null;
                if (target != null && target.isValid()) {
                    Vector toTarget = target.getLocation().toVector().subtract(golemLoc.toVector()).setY(0);
                    if (toTarget.lengthSquared() > 0.01) {
                        Location probe = golemLoc.clone().add(toTarget.normalize().multiply(2.2));
                        probe = WardenLocationUtil.findSafeNavigableSurface(probe, 0.0);
                        if (WardenLocationUtil.isSafeBossStandLocation(probe) && probe.distanceSquared(golemLoc) >= 0.64) {
                            safeOut = probe;
                        }
                    }
                }
                if (safeOut == null) {
                    for (double r = 1.5; r <= 4.0; r += 1.0) {
                        for (int i = 0; i < 8; i++) {
                            double angle = i * (Math.PI / 4.0);
                            Location probe = new Location(world, golemLoc.getX() + r * Math.cos(angle), golemLoc.getY(), golemLoc.getZ() + r * Math.sin(angle));
                            probe = WardenLocationUtil.findSafeNavigableSurface(probe, 0.0);
                            if (WardenLocationUtil.isSafeBossStandLocation(probe) && probe.distanceSquared(golemLoc) >= 0.64) {
                                safeOut = probe;
                                break;
                            }
                        }
                        if (safeOut != null) break;
                    }
                }

                if (safeOut != null && safeOut.distanceSquared(golemLoc) >= 0.64) {
                    Location fxLoc = golemLoc.clone().add(0, 1.2, 0);
                    world.spawnParticle(Particle.PORTAL, fxLoc, 30, 0.5, 0.8, 0.5, 0.3);
                    world.playSound(golemLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.5f);
                    WardenAudio.playCustomSound(golemLoc, "haohan:boss.electric", 1.4f, 1.6f);
                    WardenVFX.playTeleportShrinkEffect(plugin, golem, state);
                    golem.teleport(safeOut);
                    golem.setVelocity(new Vector(0, 0, 0));
                }
                state.insideWallTicks = 0;
            }
        } else {
            state.insideWallTicks = 0;
        }

        if (state.lastTrackedLoc != null && state.lastTrackedLoc.getWorld() == world) {
            double movedDistSq = golemLoc.distanceSquared(state.lastTrackedLoc);
            boolean tryingToMove = (state.currentBehavior == WardenBehavior.ADVANCE || state.isMovingAttack);

            if (tryingToMove && movedDistSq < 0.04) {
                state.cornerStuckTicks++;
                if (state.cornerStuckTicks >= 50) {
                    if (target != null && target.isValid()) {
                        Location targetLanded = WardenLocationUtil.findSafeNavigableSurface(target.getLocation(), 0.0);
                        Vector toTarget = targetLanded.toVector().subtract(golemLoc.toVector()).setY(0);
                        if (toTarget.lengthSquared() > 0.01) {
                            Location unstuckSpot = targetLanded.clone().subtract(toTarget.normalize().multiply(2.0));
                            unstuckSpot = WardenLocationUtil.findSafeTeleportLocation(golemLoc, unstuckSpot, 1.0);
                            if (WardenLocationUtil.isSafeBossStandLocation(unstuckSpot) && unstuckSpot.distanceSquared(golemLoc) >= 1.5) {
                                world.spawnParticle(Particle.PORTAL, golemLoc.clone().add(0, 1.2, 0), 25, 0.4, 0.6, 0.4, 0.2);
                                world.playSound(golemLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.6f);
                                WardenAudio.playCustomSound(golemLoc, "haohan:boss.electric", 1.3f, 1.7f);
                                WardenVFX.playTeleportShrinkEffect(plugin, golem, state);
                                golem.teleport(unstuckSpot);
                                golem.setVelocity(new Vector(0, 0, 0));
                            }
                        }
                    }
                    state.cornerStuckTicks = 0;
                }
            } else {
                state.cornerStuckTicks = 0;
            }
        }
        state.lastTrackedLoc = golemLoc.clone();
    }

    public static void handleTacticalFootwork(IronGolem golem, WardenState state, Player target, float targetYaw, float targetPitch, double distXZ, ModeledEntity modeledEntity, Location targetEye, Random random) {
        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();
        float yawDiff = MathUtil.normalizeAngle(targetYaw - currentYaw);

        float normalizedDiff = Math.min(1.0f, Math.abs(yawDiff) / 120.0f);
        float currentMaxTurnSpeed = 3.0f + (7.0f * normalizedDiff);

        float newYaw;
        if (Math.abs(yawDiff) <= currentMaxTurnSpeed) {
            newYaw = targetYaw;
        } else {
            newYaw = currentYaw + Math.signum(yawDiff) * currentMaxTurnSpeed;
        }
        newYaw = MathUtil.normalizeAngle(newYaw);

        golem.setRotation(newYaw, state.headPitch);

        float rawDesiredLocalYaw = MathUtil.normalizeAngle(targetYaw - newYaw);
        float desiredLocalYaw = Math.max(-75f, Math.min(75f, rawDesiredLocalYaw));
        float desiredPitch = Math.max(-60f, Math.min(60f, targetPitch));

        state.headYawLocal = MathUtil.lerpAngle(state.headYawLocal, desiredLocalYaw, 0.30f);
        state.headPitch = MathUtil.lerpAngle(state.headPitch, desiredPitch, 0.25f);

        if (modeledEntity != null) {
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
            modeledEntity.setYBodyRot(newYaw);

            BaseEntity<?> base = modeledEntity.getBase();
            if (base != null && base.getLookController() != null) {
                base.getLookController().lookAt(targetEye.getX(), targetEye.getY(), targetEye.getZ());
            }
        }

        // Behavior pacing timer (Tactical Spacing State Machine)
        state.behaviorTimer--;
        if (state.behaviorTimer <= 0) {
            if (distXZ > 8.5) {
                // Long range: Always advance forward
                state.currentBehavior = WardenBehavior.ADVANCE;
                state.behaviorTimer = MathUtil.secondsToTicks(1.25 + random.nextDouble() * 1.25);
            } else if (distXZ < 2.5) {
                // Too close / Hugging: 65% Tactical Retreat (walk backward), 35% Strafe Flank
                if (random.nextInt(100) < 65) {
                    state.currentBehavior = WardenBehavior.RETREAT;
                    state.behaviorTimer = MathUtil.secondsToTicks(1.1 + random.nextDouble() * 0.9);
                } else {
                    state.currentBehavior = random.nextBoolean() ? WardenBehavior.STRAFE_LEFT : WardenBehavior.STRAFE_RIGHT;
                    state.behaviorTimer = MathUtil.secondsToTicks(1.25 + random.nextDouble() * 1.0);
                }
            } else {
                // Mid-Range Spacing Zone (2.5m - 8.5m): Rhythmic Souls-like footwork
                int roll = random.nextInt(100);
                if (roll < 35) {
                    // 35% Advance forward cautiously
                    state.currentBehavior = WardenBehavior.ADVANCE;
                    state.behaviorTimer = MathUtil.secondsToTicks(1.0 + random.nextDouble() * 1.0);
                } else if (roll < 75) {
                    // 40% Circle/Strafe left or right around target
                    state.currentBehavior = random.nextBoolean() ? WardenBehavior.STRAFE_LEFT : WardenBehavior.STRAFE_RIGHT;
                    state.behaviorTimer = MathUtil.secondsToTicks(1.4 + random.nextDouble() * 1.25);
                } else if (roll < 92) {
                    // 17% Back-pedal retreat to bait player
                    state.currentBehavior = WardenBehavior.RETREAT;
                    state.behaviorTimer = MathUtil.secondsToTicks(1.0 + random.nextDouble() * 1.0);
                } else {
                    // 8% Menacing stare
                    state.currentBehavior = WardenBehavior.IDLE_STARE;
                    state.behaviorTimer = MathUtil.secondsToTicks(0.6 + random.nextDouble() * 0.6);
                }
            }
        }

        double desiredForward = 0.0;
        double desiredStrafe = 0.0;

        switch (state.currentBehavior) {
            case ADVANCE -> desiredForward = distXZ > 10.0 ? WardenConstants.WALK_CHASE_SPEED : WardenConstants.WALK_FORWARD_SPEED;
            case RETREAT -> desiredForward = -WardenConstants.WALK_BACKWARD_SPEED;
            case STRAFE_LEFT -> {
                desiredStrafe = -WardenConstants.WALK_STRAFE_SPEED;
                // Mild forward curve to circle target naturally
                if (distXZ > 5.5) desiredForward = WardenConstants.WALK_FORWARD_SPEED * 0.4;
                else if (distXZ < 2.8) desiredForward = -WardenConstants.WALK_BACKWARD_SPEED * 0.3;
            }
            case STRAFE_RIGHT -> {
                desiredStrafe = WardenConstants.WALK_STRAFE_SPEED;
                // Mild forward curve to circle target naturally
                if (distXZ > 5.5) desiredForward = WardenConstants.WALK_FORWARD_SPEED * 0.4;
                else if (distXZ < 2.8) desiredForward = -WardenConstants.WALK_BACKWARD_SPEED * 0.3;
            }
            case IDLE_STARE -> {
                desiredForward = 0.0;
                desiredStrafe = 0.0;
            }
        }

        state.currentForwardSpeed = MathUtil.lerp(state.currentForwardSpeed, desiredForward, 0.30);
        state.currentStrafeSpeed = MathUtil.lerp(state.currentStrafeSpeed, desiredStrafe, 0.30);

        Vector forwardVec = MathUtil.yawToDirection(newYaw);
        Vector rightVec = MathUtil.yawToRightVector(newYaw);

        Vector moveVelocity = forwardVec.multiply(state.currentForwardSpeed)
                .add(rightVec.multiply(state.currentStrafeSpeed));

        Vector entityVelocity = golem.getVelocity();
        entityVelocity.setX(moveVelocity.getX());
        entityVelocity.setZ(moveVelocity.getZ());

        apply3BlockStepAssist(golem, moveVelocity, entityVelocity);
        golem.setVelocity(entityVelocity);

        // Locomotion animation selection
        String nextAnim = "idle";
        double fw = state.currentForwardSpeed;
        double st = state.currentStrafeSpeed;

        if (Math.abs(fw) > 0.012 || Math.abs(st) > 0.012) {
            if (Math.abs(fw) >= Math.abs(st)) {
                nextAnim = fw > 0 ? "walk_forward" : "walk_backward";
            } else {
                nextAnim = st > 0 ? "walk_right" : "walk_left";
            }
        }

        WardenAnimationController.playModelAnimation(golem, state, nextAnim, 0.08, 0.12, 1.0, true);
    }
}
