package vn.haohan.lunar.core.features.boss.warden.combat;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.SimpleManualAnimator;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.lunar.api.system.util.MathUtil;
import vn.haohan.lunar.core.features.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.core.features.boss.warden.visual.WardenAudio;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 3D Volumetric Sword Aura ("Kiếm Khí") for Lunar Warden Aerial Slashes.
 * <p>
 * Lifecycle:
 * 1. Formation Phase (Ticks 0..4): Sword aura forms in-place along the swing plane, smoothly
 *    cycling through states 1 -> 5 (slash_0 -> slash_4) while the greatsword swings.
 * 2. Release & Flight Phase (Ticks 5..25): As the swing completes, transitions to state 6 -> 7
 *    (slash_5 -> slash_6) and launches forward at high speed as a thick flying blade wave.
 */
public class AerialSlashProjectileTask extends BukkitRunnable {

    private final IronGolem golem;
    private final Location current;
    private final Vector flightDir;
    private final Vector bladeSpanOrtho;
    private final Vector slashNormal;
    private final int comboIndex; // 0, 1, 2, 3 (which slash in the combo)
    private final double damage;

    private Entity projectileEntity;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;
    private Quaternionf slashRotation;

    private int step = 0;
    private static final int FORMATION_TICKS = 5; // Ticks 0..4: Stand still and form states 1 -> 5
    private static final int MAX_STEPS = 26; // Total lifetime
    private static final double SPEED = 1.85; // Flight speed

    private final Set<UUID> hitTargets = new HashSet<>();

    // Glowing lunar energy dust colors
    private static final Particle.DustOptions LUNAR_CYAN = new Particle.DustOptions(Color.fromRGB(60, 215, 255), 2.2f);
    private static final Particle.DustOptions LUNAR_WHITE = new Particle.DustOptions(Color.fromRGB(245, 250, 255), 1.8f);
    private static final Particle.DustOptions LUNAR_DEEP_BLUE = new Particle.DustOptions(Color.fromRGB(25, 90, 230), 2.0f);

    public AerialSlashProjectileTask(IronGolem golem, Location origin, Vector flightDirection, Vector rawBladeSpan, int comboIndex, double damage) {
        this.golem = golem;
        this.current = origin.clone();
        this.flightDir = flightDirection.clone().normalize();
        this.comboIndex = comboIndex;
        this.damage = damage;

        // Calculate 3D orthonormal basis matching the greatsword swing plane
        Vector spanNorm = rawBladeSpan.clone().normalize();
        if (spanNorm.lengthSquared() < 0.001 || Math.abs(spanNorm.dot(this.flightDir)) > 0.98) {
            spanNorm = new Vector(-this.flightDir.getZ(), 0, this.flightDir.getX()).normalize();
            if (spanNorm.lengthSquared() < 0.001) {
                spanNorm = new Vector(1, 0, 0);
            }
        }

        Vector normal = this.flightDir.clone().crossProduct(spanNorm).normalize();
        if (normal.lengthSquared() < 0.001) {
            normal = new Vector(0, 1, 0);
        }
        this.slashNormal = normal;
        this.bladeSpanOrtho = this.slashNormal.clone().crossProduct(this.flightDir).normalize();

        spawnVisualEntity();
    }

    private void spawnVisualEntity() {
        World world = current.getWorld();
        if (world == null) return;

        float yaw = MathUtil.getYaw(flightDir);
        float pitch = (float) Math.toDegrees(Math.asin(-Math.max(-1.0, Math.min(1.0, flightDir.getY()))));

        current.setYaw(yaw);
        current.setPitch(pitch);

        // 3D rotation matrix aligning the volumetric plane with the sword slash
        Matrix3f rotMatrix = new Matrix3f(
                (float) bladeSpanOrtho.getX(), (float) bladeSpanOrtho.getY(), (float) bladeSpanOrtho.getZ(),
                (float) slashNormal.getX(), (float) slashNormal.getY(), (float) slashNormal.getZ(),
                (float) flightDir.getX(), (float) flightDir.getY(), (float) flightDir.getZ()
        );
        this.slashRotation = new Quaternionf().setFromNormalized(rotMatrix);

        try {
            ArmorStand as = world.spawn(current, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setPersistent(false);
            });
            this.projectileEntity = as;
            WardenEntityManager.registerTempEntity(as);

            modeledEntity = ModelEngineAPI.createModeledEntity(as);
            if (modeledEntity != null) {
                activeModel = ModelEngineAPI.createActiveModel("lunar_aerial_slash");
                if (activeModel != null) {
                    modeledEntity.addModel(activeModel, true);
                    updateActiveSlashBone(0); // Start with State 1 (slash_0)
                }
            }
        } catch (Throwable t) {
            // Fallback to ItemDisplay
            try {
                ItemStack item = null;
                try {
                    item = HaoHanItemCore.get().getItemFactory().create("haohan:lunar_claymore", 1);
                } catch (Throwable ignored) {}
                if (item == null) item = new ItemStack(Material.NETHERITE_SWORD);

                ItemStack finalItem = item;
                ItemDisplay display = world.spawn(current, ItemDisplay.class, ent -> {
                    ent.setItemStack(finalItem);
                    ent.setTransformation(new Transformation(
                            new Vector3f(0f, 0f, 0f),
                            slashRotation,
                            new Vector3f(6.5f, 6.5f, 6.5f),
                            new Quaternionf()
                    ));
                    ent.setBillboard(Display.Billboard.FIXED);
                    ent.setPersistent(false);
                });
                this.projectileEntity = display;
                WardenEntityManager.registerTempEntity(display);
            } catch (Throwable ignored) {}
        }

        // Formation audio
        world.playSound(current, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.25f);
        WardenAudio.playCustomSound(current, "haohan:boss.arcslash", 1.8f, 1.15f + ((comboIndex % 4) * 0.08f));
    }

    /**
     * Dynamically switches the active visible bone among the 7 states (slash_0 to slash_6).
     */
    private void updateActiveSlashBone(int activeStateIndex) {
        if (activeModel == null) return;

        // Sized to match the 6.2m greatsword (scale 4.35 to 5.2 for finisher)
        float visualScale = (comboIndex == 3) ? 5.2f : 4.45f;

        for (int i = 0; i < 7; i++) {
            String bName = "slash_" + i;
            Optional<ModelBone> boneOpt = activeModel.getBone(bName);
            if (boneOpt.isPresent()) {
                SimpleManualAnimator anim = new SimpleManualAnimator();
                anim.getPosition().set(0, 0, 0);
                Object rotObj = anim.getRotation();
                if (rotObj instanceof Quaternionf) {
                    ((Quaternionf) rotObj).set(slashRotation);
                }
                if (i == activeStateIndex) {
                    anim.getScale().set(visualScale, visualScale, visualScale);
                } else {
                    anim.getScale().set(0.0f, 0.0f, 0.0f);
                }
                boneOpt.get().setManualAnimator(anim);
            }
        }
    }

    @Override
    public void run() {
        if (golem.isDead() || current.getWorld() == null || step >= MAX_STEPS) {
            cleanup();
            cancel();
            return;
        }

        World world = current.getWorld();

        // ==========================================
        // PHASE 1: FORMATION & SWING EXPANSION (Ticks 0..4)
        // Kiếm khí đứng im theo đường chém, chuyển mượt từ State 1 -> 5 (slash_0 -> slash_4)
        // ==========================================
        if (step < FORMATION_TICKS) {
            int stateIndex = Math.min(4, step); // 0 -> 1 -> 2 -> 3 -> 4
            updateActiveSlashBone(stateIndex);

            // Dense charging particles along the swing arc
            double progressRatio = (double) (step + 1) / (double) FORMATION_TICKS;
            double currentSpan = 6.4 * progressRatio;
            int points = 11;
            for (int i = 0; i < points; i++) {
                double t = (double) i / (double) (points - 1);
                double lat = (t - 0.5) * currentSpan;
                double curve = -(Math.sin(t * Math.PI) * (0.8 * progressRatio));

                Location pt = current.clone()
                        .add(bladeSpanOrtho.clone().multiply(lat))
                        .add(flightDir.clone().multiply(curve));

                world.spawnParticle(Particle.DUST, pt, 1, 0.04, 0.04, 0.04, 0.0,
                        (i % 2 == 0) ? LUNAR_WHITE : LUNAR_CYAN);
            }

            world.spawnParticle(Particle.ELECTRIC_SPARK, current, 2, 0.2, 0.2, 0.2, 0.05);

            step++;
            return;
        }

        // ==========================================
        // PHASE 2: LAUNCH & FORWARD FLIGHT (Ticks 5..25)
        // Vung đao xong -> Chuyển State 6 (slash_5) rồi State 7 (slash_6) và bay đi!
        // ==========================================
        int flightTick = step - FORMATION_TICKS;
        if (flightTick == 0) {
            // Moment of launch!
            updateActiveSlashBone(5); // State 6 (slash_5: Speed wave release)
            world.playSound(current, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.8f, 1.5f);
            world.playSound(current, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.7f);
            if (comboIndex == 3) {
                WardenAudio.playCustomSound(current, "haohan:boss.murasama", 1.8f, 1.0f);
            }
        } else if (flightTick == 3) {
            updateActiveSlashBone(6); // State 7 (slash_6: Flying projectile aura)
        }

        step++;
        current.add(flightDir.clone().multiply(SPEED));

        if (projectileEntity != null && projectileEntity.isValid()) {
            projectileEntity.teleport(current);
        }

        // Volumetric Particle Aura (Dense layered crescent with full 6.4m span)
        double arcSpan = (comboIndex == 3) ? 7.4 : 6.4;
        int arcPoints = 15;
        for (int i = 0; i < arcPoints; i++) {
            double t = (double) i / (double) (arcPoints - 1);
            double lateralOffset = (t - 0.5) * arcSpan;
            double curveOffset = -(Math.sin(t * Math.PI) * 1.15);

            // Layer 1: Core center
            Location ptCore = current.clone()
                    .add(bladeSpanOrtho.clone().multiply(lateralOffset))
                    .add(flightDir.clone().multiply(curveOffset));
            world.spawnParticle(Particle.DUST, ptCore, 1, 0.05, 0.05, 0.05, 0.0,
                    (i % 2 == 0) ? LUNAR_WHITE : LUNAR_CYAN);

            // Layer 2 & 3: Volumetric vertical shell (+0.25m and -0.25m along slashNormal)
            Location ptUp = ptCore.clone().add(slashNormal.clone().multiply(0.25));
            Location ptDown = ptCore.clone().subtract(slashNormal.clone().multiply(0.25));
            world.spawnParticle(Particle.DUST, ptUp, 1, 0.03, 0.03, 0.03, 0.0, LUNAR_CYAN);
            world.spawnParticle(Particle.DUST, ptDown, 1, 0.03, 0.03, 0.03, 0.0, LUNAR_DEEP_BLUE);

            if (i == 0 || i == arcPoints - 1 || i == arcPoints / 2) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, ptCore, 1, 0.05, 0.05, 0.05, 0.05);
            }
        }

        world.spawnParticle(Particle.SWEEP_ATTACK, current, 1, 0.2, 0.2, 0.2, 0);

        // Terrain collision check
        Block centerBlock = current.getBlock();
        if (centerBlock.getType().isSolid() && !centerBlock.isPassable()) {
            world.playSound(current, Sound.BLOCK_ANVIL_LAND, 1.5f, 1.2f);
            world.playSound(current, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.9f);
            world.spawnParticle(Particle.FLASH, current, 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
            world.spawnParticle(Particle.DUST, current, 25, 0.6, 0.6, 0.6, 0.0, LUNAR_CYAN);
            world.spawnParticle(Particle.CRIT, current, 15, 0.4, 0.4, 0.4, 0.2);
            cleanup();
            cancel();
            return;
        }

        // Player target hit detection
        double hitRadius = (comboIndex == 3) ? 4.2 : 3.6;
        double hitRadiusSq = hitRadius * hitRadius;
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE
                    || !player.isValid() || player.isDead()) {
                continue;
            }

            if (hitTargets.contains(player.getUniqueId())) continue;

            Location pLoc = player.getLocation().add(0, 1.0, 0);
            if (pLoc.distanceSquared(current) <= hitRadiusSq) {
                hitTargets.add(player.getUniqueId());

                WardenCombatHandler.applyCombatDamage(player, damage, golem);

                Vector kb = flightDir.clone().multiply(0.55).setY(0.24);
                player.setVelocity(player.getVelocity().add(kb));

                player.playSound(pLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.9f);
                WardenAudio.playCustomSound(pLoc, "haohan:boss.slash_heavy", 1.6f, 1.1f);

                world.spawnParticle(Particle.SWEEP_ATTACK, pLoc, 2, 0.3, 0.2, 0.3, 0);
                world.spawnParticle(Particle.CRIT, pLoc, 12, 0.3, 0.3, 0.3, 0.15);
                world.spawnParticle(Particle.DUST, pLoc, 20, 0.4, 0.5, 0.4, 0.0, LUNAR_CYAN);
                world.spawnParticle(Particle.FLASH, pLoc, 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
            }
        }
    }

    private void cleanup() {
        if (modeledEntity != null) {
            try {
                modeledEntity.destroy();
            } catch (Throwable ignored) {}
        }
        if (projectileEntity != null) {
            WardenEntityManager.removeTempEntity(projectileEntity);
            projectileEntity = null;
        }
    }
}
