package vn.haohan.lunar.mechanics.boss.warden.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Vector3f;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WardenVFX {
    private WardenVFX() {}

    private static final Map<UUID, BukkitTask> activeSlowHaloTasks = new ConcurrentHashMap<>();

    /**
     * Applies Boss Slowness effect and starts a rotating grey halo ring above the player's head.
     */
    public static void applyBossSlowness(HaoHanLunarPlugin plugin, LivingEntity target, int durationTicks, int amplifier) {
        if (target == null || !target.isValid() || target.isDead()) return;

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks, amplifier, false, false, false));

        if (target instanceof Player player) {
            UUID uuid = player.getUniqueId();
            BukkitTask oldTask = activeSlowHaloTasks.remove(uuid);
            if (oldTask != null) {
                try {
                    oldTask.cancel();
                } catch (Throwable ignored) {}
            }

            HaoHanLunarPlugin mainPlugin = (plugin != null) ? plugin : HaoHanLunarPlugin.getInstance();
            if (mainPlugin == null) return;

            BukkitTask task = new BukkitRunnable() {
                private int currentTick = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead() || !player.isValid() || !player.hasPotionEffect(PotionEffectType.SLOWNESS) || currentTick >= durationTicks) {
                        activeSlowHaloTasks.remove(uuid);
                        cancel();
                        return;
                    }

                    renderSlowHalo(player, currentTick * 0.15);
                    currentTick++;
                }
            }.runTaskTimer(mainPlugin, 1L, 1L);

            activeSlowHaloTasks.put(uuid, task);
        }
    }

    public static void applyBossSlowness(LivingEntity target, int durationTicks, int amplifier) {
        applyBossSlowness(HaoHanLunarPlugin.getInstance(), target, durationTicks, amplifier);
    }

    /**
     * Removes Boss Slowness effect and cancels any active halo task for the given entity.
     */
    public static void removeBossSlowness(LivingEntity target) {
        if (target == null) return;
        try {
            target.removePotionEffect(PotionEffectType.SLOWNESS);
        } catch (Throwable ignored) {}

        if (target instanceof Player player) {
            BukkitTask task = activeSlowHaloTasks.remove(player.getUniqueId());
            if (task != null) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Clears and cancels all active halo particle tasks.
     */
    public static void clearAllSlowHalos() {
        for (BukkitTask task : activeSlowHaloTasks.values()) {
            if (task != null) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {}
            }
        }
        activeSlowHaloTasks.clear();
    }

    /**
     * Renders a rotating grey circular particle ring halo above the player's head.
     */
    public static void renderSlowHalo(Player player, double angleOffset) {
        if (player == null || !player.isValid()) return;
        Location headLoc = player.getEyeLocation().add(0, 0.40, 0);
        World world = headLoc.getWorld();
        if (world == null) return;

        double radius = 0.42;
        int points = 16;
        Particle.DustOptions greyDust = new Particle.DustOptions(Color.fromRGB(155, 155, 155), 1.25f);
        Particle.DustOptions darkGreyDust = new Particle.DustOptions(Color.fromRGB(105, 105, 105), 1.05f);

        for (int i = 0; i < points; i++) {
            double angle = angleOffset + (2.0 * Math.PI * i / points);
            double x = headLoc.getX() + radius * Math.cos(angle);
            double z = headLoc.getZ() + radius * Math.sin(angle);
            Location pLoc = new Location(world, x, headLoc.getY(), z);

            Particle.DustOptions dust = (i % 2 == 0) ? greyDust : darkGreyDust;
            world.spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0.0, dust);
        }
    }

    public static void playTeleportShrinkEffect(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state) {
        if (state == null || golem == null || golem.isDead()) return;

        if (state.activeWarpShrinkTask != null) {
            state.activeWarpShrinkTask.cancel();
            state.activeWarpShrinkTask = null;
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity == null) return;
        ActiveModel activeModel = modeledEntity.getModel(WardenConstants.MODEL_ID).orElse(null);
        if (activeModel == null) {
            for (ActiveModel m : modeledEntity.getModels().values()) {
                activeModel = m;
                break;
            }
        }
        if (activeModel == null) return;

        final ActiveModel targetModel = activeModel;

        targetModel.setScale(new Vector3f(0.35f, 3.65f, 0.35f));

        Location loc = golem.getLocation().add(0, 1.8, 0);
        World world = loc.getWorld();
        if (world != null) {
            world.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.7f, 1.85f);
            world.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.75f);
            world.playSound(loc, Sound.ITEM_MACE_SMASH_AIR, 1.4f, 1.9f);
            WardenAudio.playCustomSound(loc, "haohan:boss.electric", 1.8f, 1.6f);
            world.spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.5, 0.1, 0.5, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 12, 0.4, 0.8, 0.4, 0.3);
            world.spawnParticle(Particle.DUST, loc, 25, 0.4, 1.0, 0.4, 0.0,
                    new Particle.DustOptions(Color.fromRGB(100, 230, 255), 2.0f));
        }

        BukkitRunnable task = new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (golem.isDead() || !golem.isValid()) {
                    cancel();
                    return;
                }

                step++;
                Location currentLoc = golem.getLocation().add(0, 1.8, 0);
                World currentWorld = currentLoc.getWorld();

                switch (step) {
                    case 1 -> {
                        targetModel.setScale(new Vector3f(0.70f, 3.45f, 0.70f));
                        if (currentWorld != null) {
                            currentWorld.playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, 1.9f);
                            currentWorld.spawnParticle(Particle.SWEEP_ATTACK, currentLoc, 2, 0.4, 0.1, 0.4, 0);
                        }
                    }
                    case 2 -> {
                        targetModel.setScale(new Vector3f(3.45f, 2.85f, 3.45f));
                        if (currentWorld != null) {
                            currentWorld.playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.3f, 1.8f);
                            currentWorld.spawnParticle(Particle.DUST, currentLoc, 15, 0.6, 0.6, 0.6, 0.0,
                                    new Particle.DustOptions(Color.fromRGB(80, 210, 255), 1.8f));
                        }
                    }
                    case 3 -> {
                        targetModel.setScale(new Vector3f(2.90f, 3.05f, 2.90f));
                    }
                    default -> {
                        targetModel.setScale(new Vector3f(3.0f, 3.0f, 3.0f));
                        state.activeWarpShrinkTask = null;
                        cancel();
                    }
                }
            }
        };

        state.activeWarpShrinkTask = task;
        task.runTaskTimer(plugin, 1L, 1L);
    }

    public static void renderMagicCircle(Location center, double radius, double rotationAngle, float progress, Color color) {
        if (center.getWorld() == null || radius < 0.05) return;
        Location base = center.clone();
        base.setY(center.getY() + 0.08);

        int circlePoints = 18;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);

        for (int i = 0; i < circlePoints; i++) {
            double angle = rotationAngle + (2.0 * Math.PI * i / circlePoints);
            double px = base.getX() + radius * Math.cos(angle);
            double pz = base.getZ() + radius * Math.sin(angle);
            Location pLoc = new Location(base.getWorld(), px, base.getY(), pz);
            base.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0.0, dust);
        }

        if (radius > 0.5) {
            double innerRadius = radius * 0.55;
            int innerPoints = 10;
            for (int i = 0; i < innerPoints; i++) {
                double angle = -rotationAngle * 1.5 + (2.0 * Math.PI * i / innerPoints);
                double px = base.getX() + innerRadius * Math.cos(angle);
                double pz = base.getZ() + innerRadius * Math.sin(angle);
                Location pLoc = new Location(base.getWorld(), px, base.getY(), pz);
                base.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0.0, dust);
            }
        }

        if (radius > 0.8) {
            for (int i = 0; i < 4; i++) {
                double nodeAngle = rotationAngle + (i * Math.PI / 2.0);
                double nx = base.getX() + radius * Math.cos(nodeAngle);
                double nz = base.getZ() + radius * Math.sin(nodeAngle);
                Location nodeLoc = new Location(base.getWorld(), nx, base.getY(), nz);
                base.getWorld().spawnParticle(Particle.END_ROD, nodeLoc, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    public static void renderSustainedPillarOfLight(Location targetLoc, double beamRadius, int height, int beamTick) {
        if (targetLoc.getWorld() == null) return;
        Location impact = targetLoc.clone();

        for (double y = 0; y <= height; y += 0.9) {
            Location rayPt = impact.clone().add(0, y, 0);
            impact.getWorld().spawnParticle(Particle.END_ROD, rayPt, 1, 0.15, 0.35, 0.15, 0.01);
            impact.getWorld().spawnParticle(Particle.DUST, rayPt, 2, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(180, 240, 255), 2.2f));
            if (beamTick % 2 == 0) {
                impact.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, rayPt, 1, 0.1, 0.3, 0.1, 0.01);
            }
        }

        int spiralSteps = 24;
        double rotationOffset = beamTick * 0.45;
        for (int i = 0; i < spiralSteps; i++) {
            double prog = (double) i / spiralSteps;
            double y = prog * height;
            double angle = (prog * Math.PI * 6.0) + rotationOffset;
            double sx = impact.getX() + beamRadius * Math.cos(angle);
            double sz = impact.getZ() + beamRadius * Math.sin(angle);
            Location spiralPt = new Location(impact.getWorld(), sx, impact.getY() + y, sz);
            impact.getWorld().spawnParticle(Particle.DUST, spiralPt, 1, 0, 0, 0, 0.0, new Particle.DustOptions(Color.WHITE, 1.8f));
        }

        impact.getWorld().spawnParticle(Particle.SWEEP_ATTACK, impact.clone().add(0, 0.3, 0), 1, 0.4, 0.1, 0.4, 0);
        impact.getWorld().spawnParticle(Particle.DUST, impact.clone().add(0, 0.2, 0), 15, 0.8, 0.1, 0.8, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f));
    }
}
