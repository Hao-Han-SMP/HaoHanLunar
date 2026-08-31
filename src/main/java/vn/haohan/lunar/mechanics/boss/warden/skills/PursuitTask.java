package vn.haohan.lunar.mechanics.boss.warden.skills;

import vn.haohan.lunar.util.MathUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenBehavior;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.combat.WardenCombatHandler;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenVFX;

import java.util.Random;

public class PursuitTask extends BukkitRunnable {
    private final HaoHanLunarPlugin plugin;
    private final IronGolem golem;
    private final WardenState state;
    private final Player target;
    private int step = 0;
    private final int maxSteps;
    private int lateralSign;
    private final Random random = new Random();

    public PursuitTask(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, int totalSteps) {
        this.plugin = plugin;
        this.golem = golem;
        this.state = state;
        this.target = target;
        this.maxSteps = totalSteps;
        this.lateralSign = random.nextBoolean() ? 1 : -1;
    }

    public static void executeZigZagPhantomPursuit(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state, Player target, Random random) {
        state.isExecutingZigZagPursuit = true;
        state.zigZagPursuitCooldown = MathUtil.secondsToTicks(6.0 + random.nextDouble() * 3.0);

        double initialDist = golem.getLocation().distance(target.getLocation());
        int calculatedSteps = MathUtil.clamp((int)Math.ceil(initialDist / 3.0), 4, 10);

        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.8f, 1.6f);
        golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 1.8f);
        WardenAudio.playCustomSound(golem.getLocation(), "haohan:boss.electric", 1.8f, 1.4f);

        long startDelayTicks = MathUtil.secondsToTicks(0.10);
        long stepIntervalTicks = MathUtil.secondsToTicks(0.20);
        new PursuitTask(plugin, golem, state, target, calculatedSteps).runTaskTimer(plugin, startDelayTicks, stepIntervalTicks);
    }

    @Override
    public void run() {
        if (golem.isDead() || target == null || !target.isValid() || target.isDead() || golem.getWorld() != target.getWorld()) {
            state.isExecutingZigZagPursuit = false;
            cancel();
            return;
        }

        step++;
        Location currentLoc = golem.getLocation();
        Location targetLoc = target.getLocation();
        double dist = currentLoc.distance(targetLoc);

        Vector dirToTarget = targetLoc.toVector().subtract(currentLoc.toVector()).setY(0);
        if (dirToTarget.lengthSquared() < 0.01) {
            dirToTarget = currentLoc.getDirection().setY(0);
        }
        dirToTarget.normalize();

        Vector rightVec = new Vector(-dirToTarget.getZ(), 0, dirToTarget.getX()).normalize();
        this.lateralSign = -this.lateralSign;

        double forwardStep = Math.min(dist * 0.45, 4.0);
        double lateralStep = 2.8 + (random.nextDouble() * 1.8);

        Vector leapVector = dirToTarget.clone().multiply(forwardStep)
                .add(rightVec.clone().multiply(lateralStep * lateralSign));

        Location rawNextLeapLoc = currentLoc.clone().add(leapVector);
        Location nextLeapLoc = WardenLocationUtil.findSafeTeleportLocation(currentLoc, rawNextLeapLoc, 1.0);
        if (!WardenLocationUtil.isSafeBossStandLocation(nextLeapLoc)) {
            nextLeapLoc = WardenLocationUtil.findSafeTeleportLocation(currentLoc, currentLoc.clone().add(dirToTarget.clone().multiply(forwardStep)), 1.0);
        }

        Vector faceDir = targetLoc.toVector().subtract(nextLeapLoc.toVector()).setY(0).normalize();
        float faceYaw = MathUtil.getYaw(faceDir);
        nextLeapLoc.setYaw(faceYaw);
        nextLeapLoc.setPitch(0f);

        Location midFx = currentLoc.clone().add(leapVector.clone().multiply(0.5)).add(0, 1.2, 0);
        currentLoc.getWorld().spawnParticle(Particle.PORTAL, midFx, 35, 0.6, 0.8, 0.6, 0.4);
        currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, midFx, 2, 0.4, 0.1, 0.4, 0);
        currentLoc.getWorld().spawnParticle(Particle.DUST, midFx, 30, 0.5, 0.5, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 210, 255), 2.0f));
        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 1.7f);
        currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.5f);
        WardenAudio.playCustomSound(currentLoc, "haohan:boss.slash_light", 1.4f, 1.6f);

        WardenVFX.playTeleportShrinkEffect(plugin, golem, state);

        golem.teleport(nextLeapLoc);

        double newDist = nextLeapLoc.distance(target.getLocation());

        if (newDist <= 5.8 || step >= maxSteps) {
            state.isExecutingZigZagPursuit = false;

            if (random.nextInt(100) < 88) {
                int roll = random.nextInt(100);
                String surpriseAttack;
                if (roll < 35) {
                    surpriseAttack = "attack_slash_left";
                } else if (roll < 70) {
                    surpriseAttack = "attack_sweep_right";
                } else if (state.groundSlamCooldown <= 0 && roll < 88) {
                    surpriseAttack = "attack_slash_straight";
                    state.groundSlamCooldown = MathUtil.secondsToTicks(8.0 + random.nextDouble() * 4.0);
                } else if (state.thrustCooldown <= 0) {
                    surpriseAttack = "attack_thrust_fling";
                    state.thrustCooldown = MathUtil.secondsToTicks(10.0 + random.nextDouble() * 4.0);
                } else {
                    surpriseAttack = random.nextBoolean() ? "attack_slash_left" : "attack_sweep_right";
                }
                WardenCombatHandler.triggerSurpriseAttack(golem, state, surpriseAttack, target);
            } else {
                state.currentBehavior = WardenBehavior.ADVANCE;
                state.attackCooldown = MathUtil.secondsToTicks(0.4);
            }

            cancel();
        }
    }
}
