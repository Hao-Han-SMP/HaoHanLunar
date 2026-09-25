package vn.haohan.lunar.robot;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.manager.MountManager;
import com.ticxo.modelengine.api.mount.controller.MountControllerTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.Input;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.*;

public class LunarRobotEntity {
    private final Plugin plugin;
    private final LivingEntity entity;
    private final LunarRobotData data;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;

    private int scanCooldown = 0;
    private int energyTickCooldown = 0;
    private int combatCooldown = 0;
    private long lastThrustTime = 0;
    private int thrustFlightTicksRemaining = 70;
    private int maxThrustFlightTicks = 70;
    private boolean isThrustFlying = false;
    private long lastThrustSoundTime = 0;
    private long lastSpeedWarningTime = 0;
    private int speedBoostTicksRemaining = 100;
    private int maxSpeedBoostTicks = 100;
    private boolean isSpeedBoosting = false;
    private int speedRecoveryCooldown = 0;
    private int speedRecoveryDelayTicks = 0;
    private long lastBoostSoundTime = 0;
    private long lastSpeedStepSoundTime = 0;
    private boolean lastRechargeSoundPlayed = false;
    private boolean wasBoostingPreviousTick = false;
    private String currentAnimation = "idle";
    private LivingEntity combatTarget = null;
    private boolean customAttacking = false;
    private boolean frozen = false;
    private ArmorStand fallbackSeat = null;
    private BossBar statusBossBar = null;
    private String notificationTitle = null;
    private BarColor notificationColor = null;
    private int notificationTicksRemaining = 0;

    private final Set<ItemDisplay> activeHolograms = new HashSet<>();

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
        if (entity instanceof Mob mob) {
            mob.setAware(!frozen);
            if (frozen) {
                mob.getPathfinder().stopPathfinding();
                mob.setVelocity(new Vector(0, mob.getVelocity().getY() < 0 ? mob.getVelocity().getY() : 0, 0));
            }
        }
        if (frozen) {
            playAnimation("idle", 0.2, 0.2, 1.0, true);
        }
    }

    public LunarRobotEntity(Plugin plugin, LivingEntity entity, LunarRobotData data) {
        this.plugin = plugin;
        this.entity = entity;
        this.data = data;
        updateMaxThrustFlightTicks();
        this.thrustFlightTicksRemaining = this.maxThrustFlightTicks;
        updateMaxSpeedBoostTicks();
        this.speedBoostTicksRemaining = this.maxSpeedBoostTicks;
        setupEntityAttributes();
        setupModelEngine();
    }

    private void setupEntityAttributes() {
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCustomNameVisible(true);
        updateCustomName();

        try {
            if (entity.getAttribute(Attribute.MAX_HEALTH) != null) {
                entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(data.getMaxHealth());
                if (entity.isValid() && !entity.isDead() && data.getHealth() > 0) {
                    entity.setHealth(Math.min(data.getHealth(), data.getMaxHealth()));
                }
            }
            if (entity.getAttribute(Attribute.STEP_HEIGHT) != null) {
                entity.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(3.0);
            }
            if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
            }
        } catch (Throwable ignored) {}

        if (entity instanceof Wolf wolf) {
            wolf.setSitting(data.isSitting());
            if (data.getOwnerUuid() != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(data.getOwnerUuid());
                wolf.setOwner(op);
                wolf.setTamed(true);
            } else {
                wolf.setTamed(false);
                wolf.setOwner(null);
            }
        }
    }

    private void setupModelEngine() {
        try {
            activeModel = ModelEngineAPI.createActiveModel("lunar_robot");
            if (activeModel != null) {
                modeledEntity = ModelEngineAPI.createModeledEntity(entity);
                modeledEntity.addModel(activeModel, true);
                modeledEntity.setBaseEntityVisible(false);
                modeledEntity.setModelRotationLocked(false);
                activeModel.setInvisUpdate(true);
                activeModel.setScale(1.0f);
                activeModel.setHitboxScale(1.0f);
                playAnimation("idle", 0.2, 0.2, 1.0, true);

                Optional<?> optMm = activeModel.getMountManager();
                if (optMm.isPresent() && optMm.get() instanceof MountManager mm) {
                    mm.setCanDrive(true);
                    mm.setCanRide(true);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("ModelEngine lunar_robot model failed to load: " + t.getMessage());
        }
    }

    public void playAnimation(String anim, double lerpIn, double lerpOut, double speed, boolean loop) {
        if (activeModel == null) return;
        AnimationHandler handler = activeModel.getAnimationHandler();
        if (handler == null) return;
        if (anim.equals(currentAnimation) && handler.isPlayingAnimation(anim)) return;

        handler.playAnimation(anim, lerpIn, lerpOut, speed, loop);
        this.currentAnimation = anim;
    }

    public void stopAnimation(String anim) {
        if (activeModel == null) return;
        AnimationHandler handler = activeModel.getAnimationHandler();
        if (handler != null) {
            handler.stopAnimation(anim);
        }
    }

    public boolean isAnimationPlaying(String anim) {
        if (activeModel == null) return false;
        AnimationHandler handler = activeModel.getAnimationHandler();
        return handler != null && handler.isPlayingAnimation(anim);
    }

    public void updateCustomName() {
        int integrity = (int) Math.round(data.getIntegrityPercentage());
        String integrityColor = integrity > 50 ? "§a" : (integrity > 25 ? "§e" : "§c");
        String integrityStr = " §8[" + integrityColor + integrity + "%§8]";

        if (data.isTamed()) {
            String status = data.getEnergy() <= 0 ? "§c[CẠN PIN]" : data.getActiveTask().getFormattedName();
            String owner = data.getOwnerName() != null ? " §8(§7" + data.getOwnerName() + "§8)" : "";
            entity.setCustomName("§b§l[ROBOT 4 CHÂN] §f" + data.getName() + integrityStr + " " + status + owner);
        } else {
            entity.setCustomName("§e§l[ROBOT HOANG DÃ]" + integrityStr + " §7Chưa kết nối (Cần Tablet)");
        }
    }

    public void tick() {
        if (!entity.isValid() || entity.isDead()) {
            cleanupStatusBossBar();
            cleanupHolograms();
            return;
        }

        // Sync real-time health to data
        data.setHealth(entity.getHealth());

        // 0. Frozen state (e.g. while player is solving puzzle)
        if (frozen) {
            if (entity instanceof Mob mob) {
                mob.getPathfinder().stopPathfinding();
                mob.setVelocity(new Vector(0, mob.getVelocity().getY() < 0 ? mob.getVelocity().getY() : 0, 0));
            }
            if (!"idle".equals(currentAnimation) && !isAnimationPlaying("attack")) {
                playAnimation("idle", 0.2, 0.2, 1.0, true);
            }
            return;
        }

        // 1. Check Energy & Offline State
        if (data.getEnergy() <= 0) {
            if (!"offline".equals(currentAnimation)) {
                playAnimation("offline", 0.3, 0.3, 1.0, true);
            }
            if (entity instanceof Wolf wolf && !wolf.isSitting()) {
                wolf.setSitting(true);
            }
            updateCustomName();
            cleanupStatusBossBar();
            return;
        }

        // Clear combat target if out of energy or mode changed
        if (data.getActiveTask() != RobotTask.COMBAT || data.getEnergy() <= 0) {
            combatTarget = null;
        }

        // 2. Rider & Movement Handling (SPEED task)
        Player rider = getRider();
        if (rider != null) {
            handleRiderControl(rider);
        } else {
            handleAutonomousMovement();
        }

        // 3. Task Executions (Single Active Task Rule)
        switch (data.getActiveTask()) {
            case ORE_SCAN -> handleOreScanTask();
            case COMBAT -> handleCombatTask();
            case THRUST -> handleThrustTaskIdle();
            default -> {}
        }

        // 4. Update Status BossBar
        updateStatusBossBar();

        // 5. Save state periodically
        energyTickCooldown++;
        if (energyTickCooldown >= 100) { // Every 5s
            energyTickCooldown = 0;
            data.saveTo(entity, plugin);
            updateCustomName();
            if (plugin instanceof vn.haohan.lunar.HaoHanLunarPlugin lunarPlugin) {
                Player owner = getOnlineOwner();
                if (owner != null && lunarPlugin.getLunarRobotMechanic() != null) {
                    lunarPlugin.getLunarRobotMechanic().saveRobotLocationToPlayer(owner, this);
                }
            }
        }
    }

    private void handleAutonomousMovement() {
        // Combat pursuit takes priority when active
        if (data.getActiveTask() == RobotTask.COMBAT && combatTarget != null) {
            if (!isCombatTargetValid()) {
                if (combatTarget.isDead() || !combatTarget.isValid()) {
                    String name = getEntityDisplayName(combatTarget);
                    setBossBarNotification("§a§l[CHIẾN ĐẤU] §aĐã tiêu diệt: §e" + name + "§a!", BarColor.GREEN, 60);
                    Player owner = getOnlineOwner();
                    if (owner != null) {
                        owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
                    }
                }
                combatTarget = null;
                if (entity instanceof Mob mob) {
                    mob.getPathfinder().stopPathfinding();
                }
            } else {
                Player owner = getOnlineOwner();
                double distToOwnerSq = (owner != null && owner.getWorld().equals(entity.getWorld()))
                        ? entity.getLocation().distanceSquared(owner.getLocation()) : Double.MAX_VALUE;

                if (distToOwnerSq > 1600.0) { // > 40 blocks away from owner
                    setBossBarNotification("§c§l[CHIẾN ĐẤU] §eMục tiêu quá xa (>40m), hủy nhiệm vụ!", BarColor.RED, 60);
                    combatTarget = null;
                    if (entity instanceof Mob mob) {
                        mob.getPathfinder().stopPathfinding();
                    }
                } else {
                    double distToTargetSq = entity.getLocation().distanceSquared(combatTarget.getLocation());
                    if (distToTargetSq > 6.25) { // > 2.5 blocks away from target
                        if (entity instanceof Mob mob) {
                            mob.getPathfinder().moveTo(combatTarget, 1.45);
                        }
                        playAnimation("run", 0.1, 0.1, 1.3, true);
                    } else {
                        // In melee attack range
                        if (entity instanceof Mob mob) {
                            mob.getPathfinder().stopPathfinding();
                        }
                        Vector dir = combatTarget.getLocation().toVector().subtract(entity.getLocation().toVector());
                        if (dir.lengthSquared() > 0.001) {
                            Location curLoc = entity.getLocation();
                            curLoc.setDirection(dir);
                            entity.setRotation(curLoc.getYaw(), curLoc.getPitch());
                        }
                        if (!isAnimationPlaying("attack")) {
                            playAnimation("idle", 0.1, 0.1, 1.0, true);
                        }
                    }
                    return; // Handled combat movement, skip normal follow-owner movement
                }
            }
        }

        Player owner = getOnlineOwner();
        if (owner == null || !data.isFollowOwner() || data.isSitting()) {
            if (entity.getVelocity().lengthSquared() < 0.01) {
                if (!isAnimationPlaying("attack")) {
                    playAnimation("idle", 0.2, 0.2, 1.0, true);
                }
            } else {
                playAnimation("walk", 0.2, 0.2, 1.0, true);
            }
            return;
        }

        // Cross-dimension / world follow support
        if (!entity.getWorld().equals(owner.getWorld())) {
            teleportToOwner(owner);
            return;
        }

        double distSq = entity.getLocation().distanceSquared(owner.getLocation());
        if (distSq > 400.0) { // Teleport if too far (> 20 blocks)
            teleportToOwner(owner);
        } else if (distSq > 16.0) { // Move towards owner (> 4 blocks)
            if (entity instanceof Mob mob) {
                mob.getPathfinder().moveTo(owner, 1.3);
            }
            playAnimation("walk", 0.2, 0.2, 1.2, true);
        } else {
            if (!isAnimationPlaying("attack")) {
                playAnimation("idle", 0.2, 0.2, 1.0, true);
            }
        }
    }

    private void handleRiderControl(Player rider) {
        if (!data.hasModule("speed") && !data.hasModule("thrust")) return;

        // Auto-dismount if robot is out of power
        if (data.getEnergy() <= 0) {
            setBossBarNotification("§c§l[ROBOT] §cCạn pin! Tự động đẩy người lái ra.", BarColor.RED, 60);
            rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.0f);
            dismountRider();
            return;
        }

        Input input = null;
        try {
            input = rider.getCurrentInput();
        } catch (Throwable ignored) {}

        // In SPEED mode: if in fallback seat, input is handled here.
        // For ModelEngine native mount, input is passed via LunarRobotMountController,
        // but if input is available from rider, update speed boost state.
        boolean isJumpInput = input != null && input.isJump();
        if (data.getActiveTask() == RobotTask.SPEED) {
            // Note: If ModelEngine controller already called handleSpeedBoost this tick,
            // we avoid double ticking.
            // Let's ensure handleSpeedBoost is called properly
            if (fallbackSeat != null && fallbackSeat.isValid()) {
                handleSpeedBoost(isJumpInput, rider);
            }
        }

        // Apply ground movement speed ONLY if active task is SPEED
        if (data.getActiveTask() == RobotTask.SPEED && data.hasModule("speed")) {
            // Base speed is 0.39, increases to 0.65 when boosting (holding Space)
            double targetSpeed = isSpeedBoosting ? 0.65 : 0.39;
            try {
                if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(targetSpeed);
                }
            } catch (Throwable ignored) {}
        } else {
            try {
                if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
                }
            } catch (Throwable ignored) {}
        }

        // Steer robot and keep fallback seat aligned if active
        if (fallbackSeat != null && fallbackSeat.isValid()) {
            if (!fallbackSeat.getPassengers().contains(rider)) {
                cleanupFallbackSeat();
            } else {
                if (input != null && input.isSneak()) {
                    fallbackSeat.eject();
                    return;
                }
                Location seatLoc = entity.getLocation().clone().add(0, 0.65, 0);
                seatLoc.setYaw(rider.getLocation().getYaw());
                seatLoc.setPitch(0);
                fallbackSeat.teleport(seatLoc);

                if (input != null) {
                    float forward = (input.isForward() ? 1f : 0f) - (input.isBackward() ? 1f : 0f);
                    float side = (input.isLeft() ? 1f : 0f) - (input.isRight() ? 1f : 0f);
                    boolean hasMoveInput = (forward != 0 || side != 0);

                    Location rLoc = rider.getLocation();
                    entity.setRotation(rLoc.getYaw(), 0);

                    double moveSpeed = (data.getActiveTask() == RobotTask.SPEED && data.hasModule("speed"))
                            ? (isSpeedBoosting ? 0.65 : 0.39) : 0.35;
                    Vector moveVec;
                    if (hasMoveInput) {
                        Vector lookDir = rLoc.getDirection().setY(0).normalize();
                        Vector sideDir = new Vector(-lookDir.getZ(), 0, lookDir.getX());
                        moveVec = lookDir.multiply(forward).add(sideDir.multiply(side)).normalize().multiply(moveSpeed * 0.42);
                    } else {
                        moveVec = new Vector(0, 0, 0);
                    }
                    moveVec.setY(entity.getVelocity().getY()); // Preserve gravity/vertical speed

                    boolean isJump = input.isJump();
                    if (data.getActiveTask() == RobotTask.SPEED) {
                        // In SPEED mode: Space is used for sprint boost, strictly ground locomotion
                        entity.setVelocity(moveVec);
                    } else if (data.getActiveTask() == RobotTask.THRUST) {
                        // In THRUST mode: handle jumping & finite flight duration
                        handleThrustFlightInFallback(isJump, rider, moveVec);
                    } else {
                        if (isJump && (entity.isOnGround() || entity.getLocation().getBlock().isLiquid())) {
                            moveVec.setY(0.5);
                            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WOLF_STEP, 0.8f, 1.2f);
                        }
                        entity.setVelocity(moveVec);
                    }
                }
            }
        } else {
            // ModelEngine native mount active: align robot rotation with rider looking direction
            entity.setRotation(rider.getLocation().getYaw(), 0);
        }

        // Animations and energy consumption while moving / thrusting
        if (isThrustFlying) {
            playAnimation("thrust", 0.1, 0.1, 1.2, true);
        } else if (entity.getVelocity().lengthSquared() > 0.01) {
            playAnimation("run", 0.1, 0.1, 1.4, true);
            if (data.getActiveTask() == RobotTask.SPEED && data.hasModule("speed") && !isSpeedBoosting) {
                long now = System.currentTimeMillis();
                if (now - lastSpeedStepSoundTime >= 320) {
                    lastSpeedStepSoundTime = now;
                    entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 0.35f, 1.6f);
                }
            }
            if (energyTickCooldown % 20 == 0) {
                data.consumeEnergy(8);
                if (data.getActiveTask() == RobotTask.SPEED && data.hasModule("speed")) {
                    data.degradeModule("speed", 0.05);
                }
            }
        } else {
            playAnimation("idle", 0.2, 0.2, 1.0, true);
        }

        // Lunar low gravity riding feel: cancel fall damage & smooth gallop
        entity.setFallDistance(0.0f);
        rider.setFallDistance(0.0f);
    }

    private void handleOreScanTask() {
        if (!data.hasModule("ore_scan")) return;

        scanCooldown++;
        if (scanCooldown < 60) return; // Every 3s
        scanCooldown = 0;

        double eff = data.getModuleEfficiency("ore_scan");
        int radius = (int) (16 + (eff / 100.0) * 16); // 16 to 32 blocks

        Location center = entity.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        Block nearestCommonOre = null;
        Block nearestRareOre = null;
        double minCommonDist = Double.MAX_VALUE;
        double minRareDist = Double.MAX_VALUE;

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        for (int x = cx - radius; x <= cx + radius; x += 2) {
            for (int y = Math.max(world.getMinHeight(), cy - 12); y <= Math.min(world.getMaxHeight(), cy + 12); y += 2) {
                for (int z = cz - radius; z <= cz + radius; z += 2) {
                    Block block = world.getBlockAt(x, y, z);
                    Material mat = block.getType();

                    if (isRareOre(mat)) {
                        double d = center.distanceSquared(block.getLocation().add(0.5, 0.5, 0.5));
                        if (d < minRareDist) {
                            minRareDist = d;
                            nearestRareOre = block;
                        }
                    } else if (isCommonOre(mat)) {
                        double d = center.distanceSquared(block.getLocation().add(0.5, 0.5, 0.5));
                        if (d < minCommonDist) {
                            minCommonDist = d;
                            nearestCommonOre = block;
                        }
                    }
                }
            }
        }

        // Play scanner pulse animation & effects
        playAnimation("scan", 0.1, 0.2, 1.2, false);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, center.clone().add(0, 0.8, 0), 12, 1.0, 0.5, 1.0, 0.05);

        if (nearestRareOre != null) {
            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
            spawnRareOreHologram(nearestRareOre.getLocation().add(0.5, 1.2, 0.5));
            setBossBarNotification("§6§l[DÒ QUẶNG] §aPhát hiện quặng hiếm gần đây! (Chiếu hologram)", BarColor.YELLOW, 60);
        } else if (nearestCommonOre != null) {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
            world.spawnParticle(Particle.HAPPY_VILLAGER, center.clone().add(0, 1.2, 0), 6, 0.5, 0.5, 0.5);
        }

        data.consumeEnergy(10);
        data.degradeModule("ore_scan", 0.12);
    }

    private void spawnRareOreHologram(Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // Generate 2-3 randomized fading ItemDisplay markers around the ore
        Random random = new Random();
        for (int i = 0; i < 2; i++) {
            Location spawnLoc = loc.clone().add(
                    (random.nextDouble() - 0.5) * 1.5,
                    (random.nextDouble() - 0.5) * 1.0,
                    (random.nextDouble() - 0.5) * 1.5
            );

            ItemDisplay display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.DIAMOND));
                d.setBillboard(Display.Billboard.CENTER);
                Transformation trans = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 1, 0),
                        new Vector3f(0.5f, 0.5f, 0.5f),
                        new AxisAngle4f(0, 0, 1, 0)
                );
                d.setTransformation(trans);
                d.setGlowColorOverride(Color.AQUA);
                d.setGlowing(true);
            });

            activeHolograms.add(display);

            // Remove hologram after 80 ticks (4s)
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) {
                        world.spawnParticle(Particle.ELECTRIC_SPARK, display.getLocation(), 8, 0.2, 0.2, 0.2, 0.02);
                        display.remove();
                    }
                    activeHolograms.remove(display);
                }, 80L);
            }
        }
    }

    private void handleCombatTask() {
        if (!data.hasModule("combat")) return;
        if (data.getEnergy() <= 0) return;

        Player owner = getOnlineOwner();
        if (owner == null) return;

        if (!isCombatTargetValid()) {
            return;
        }

        LivingEntity target = combatTarget;
        double distSq = entity.getLocation().distanceSquared(target.getLocation());

        combatCooldown++;

        // Attack range check: <= 3.5 blocks (distSq <= 12.25)
        if (distSq <= 12.25) {
            if (entity instanceof LivingEntity le && !le.hasLineOfSight(target)) {
                return;
            }

            if (combatCooldown >= 20) { // 1s attack cycle
                combatCooldown = 0;

                // Execute melee strike
                double eff = data.getModuleEfficiency("combat");
                double damage = 8.0 + (eff / 100.0) * 12.0; // 8 to 20 damage

                playAnimation("attack", 0.05, 0.1, 1.5, false);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1.0, 0), 2, 0.2, 0.2, 0.2, 0.0);
                entity.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1.0, 0), 8, 0.25, 0.25, 0.25, 0.1);

                customAttacking = true;
                try {
                    target.damage(damage, entity);
                } finally {
                    customAttacking = false;
                }

                data.consumeEnergy(25);
                data.degradeModule("combat", 0.25);

                String targetName = getEntityDisplayName(target);
                boolean isDead = target.isDead() || target.getHealth() <= 0;

                // Thông báo nhiệm vụ kèm lượng damage gây ra
                if (isDead) {
                    setBossBarNotification(String.format("§a§l[CHIẾN ĐẤU] §aĐã hạ §e%s §a(-%.1f ST)!", targetName, damage), BarColor.GREEN, 50);
                    if (owner != null) {
                        owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                    }
                    combatTarget = null;
                } else {
                    double remainingHp = Math.max(0.0, target.getHealth());
                    setBossBarNotification(String.format("§c§l[CHIẾN ĐẤU] §fChém §e%s §c-%.1f ST §7(HP: %.1f)", targetName, damage, remainingHp), BarColor.RED, 40);
                }

                if (data.getEnergy() <= 0) {
                    setBossBarNotification("§c§l[CHIẾN ĐẤU] §cCạn pin! Ngừng nhiệm vụ.", BarColor.RED, 60);
                    combatTarget = null;
                }
            }
        }
    }

    public boolean isCustomAttacking() {
        return customAttacking;
    }

    public LivingEntity getCombatTarget() {
        return combatTarget;
    }

    public void setCombatTarget(LivingEntity target) {
        if (target == null || target.equals(this.entity)) return;
        if (!target.isValid() || target.isDead()) return;
        if (data.getEnergy() <= 0 || !data.hasModule("combat") || data.getActiveTask() != RobotTask.COMBAT) return;

        Player owner = getOnlineOwner();
        if (owner == null) return;
        if (!target.getWorld().equals(owner.getWorld()) || target.getLocation().distanceSquared(owner.getLocation()) > 1024.0) {
            return;
        }

        if (this.combatTarget != target) {
            boolean isSwitch = (this.combatTarget != null && this.combatTarget.isValid() && !this.combatTarget.isDead());
            this.combatTarget = target;
            this.combatCooldown = 15; // Set near-ready attack so first hit lands promptly upon arrival
            String targetName = getEntityDisplayName(target);
            if (isSwitch) {
                setBossBarNotification("§e§l[CHIẾN ĐẤU] §eĐổi mục tiêu: §c" + targetName, BarColor.YELLOW, 50);
            } else {
                setBossBarNotification("§e§l[CHIẾN ĐẤU] §eTấn công mục tiêu: §c" + targetName, BarColor.YELLOW, 50);
            }
            owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
        }
    }

    public boolean isCombatTargetValid() {
        if (combatTarget == null) return false;
        if (!combatTarget.isValid() || combatTarget.isDead()) return false;
        if (!combatTarget.getWorld().equals(entity.getWorld())) return false;
        Player owner = getOnlineOwner();
        if (owner == null) return false;
        if (!owner.getWorld().equals(combatTarget.getWorld())) return false;
        if (combatTarget.getLocation().distanceSquared(owner.getLocation()) > 1024.0) {
            return false;
        }
        return true;
    }

    public static String getEntityDisplayName(Entity ent) {
        if (ent == null) return "Mục tiêu";
        if (ent instanceof Player p) {
            return p.getName();
        }
        if (ent.getCustomName() != null && !ent.getCustomName().isEmpty()) {
            return ent.getCustomName();
        }
        return ent.getName();
    }

    public int getThrustFlightTicksRemaining() {
        return thrustFlightTicksRemaining;
    }

    public int getMaxThrustFlightTicks() {
        return maxThrustFlightTicks;
    }

    public boolean isThrustFlying() {
        return isThrustFlying;
    }

    public void setThrustFlightTicksRemaining(int ticks) {
        this.thrustFlightTicksRemaining = Math.max(0, Math.min(maxThrustFlightTicks, ticks));
    }

    public void updateMaxThrustFlightTicks() {
        double eff = data.hasModule("thrust") ? data.getModuleEfficiency("thrust") : 0.0;
        this.maxThrustFlightTicks = (int) Math.round(60 + (eff / 100.0) * 40); // 60 to 100 ticks (3.0s to 5.0s)
        if (thrustFlightTicksRemaining > maxThrustFlightTicks) {
            thrustFlightTicksRemaining = maxThrustFlightTicks;
        }
    }

    public int getSpeedBoostTicksRemaining() {
        return speedBoostTicksRemaining;
    }

    public int getMaxSpeedBoostTicks() {
        return maxSpeedBoostTicks;
    }

    public boolean isSpeedBoosting() {
        return isSpeedBoosting;
    }

    public void setSpeedBoostTicksRemaining(int ticks) {
        this.speedBoostTicksRemaining = Math.max(0, Math.min(maxSpeedBoostTicks, ticks));
    }

    public void updateMaxSpeedBoostTicks() {
        double eff = data.hasModule("speed") ? data.getModuleEfficiency("speed") : 0.0;
        this.maxSpeedBoostTicks = (int) Math.round(80 + (eff / 100.0) * 40); // 80 to 120 ticks (4.0s to 6.0s)
        if (speedBoostTicksRemaining > maxSpeedBoostTicks) {
            speedBoostTicksRemaining = maxSpeedBoostTicks;
        }
    }

    public void handleSpeedBoost(boolean isJump, Player rider) {
        updateMaxSpeedBoostTicks();

        if (isJump) {
            // Player is holding Space to boost speed
            if (data.getEnergy() < 2) {
                isSpeedBoosting = false;
                wasBoostingPreviousTick = false;
                long now = System.currentTimeMillis();
                if (now - lastSpeedWarningTime >= 1000) {
                    lastSpeedWarningTime = now;
                    setBossBarNotification("§c§l[TỐC HÀNH] §cKhông đủ năng lượng để bứt tốc! (Cần ≥ 2 EU)", BarColor.RED, 40);
                    if (rider != null) {
                        rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.4f);
                    }
                }
                return;
            }

            if (speedBoostTicksRemaining <= 0) {
                if (isSpeedBoosting || wasBoostingPreviousTick) {
                    // SFX when boost stamina suddenly exhausts
                    Location loc = entity.getLocation();
                    World w = entity.getWorld();
                    w.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.6f);
                    w.playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.7f, 1.3f);
                    w.playSound(loc, Sound.BLOCK_COPPER_GRATE_HIT, 0.7f, 0.9f);
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.3, 0), 8, 0.15, 0.1, 0.15, 0.03);
                }
                isSpeedBoosting = false;
                wasBoostingPreviousTick = false;
                speedRecoveryDelayTicks = 15; // Holding space prevents recovery completely
                long now = System.currentTimeMillis();
                if (now - lastSpeedWarningTime >= 1000) {
                    lastSpeedWarningTime = now;
                    setBossBarNotification("§c§l[TỐC HÀNH] §cHết thể lực tăng tốc! Hãy thả Space để hồi.", BarColor.RED, 40);
                    if (rider != null) {
                        rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.45f, 1.5f);
                    }
                }
                return;
            }

            // Trigger activation SFX on boost start
            Location loc = entity.getLocation();
            World w = entity.getWorld();
            long now = System.currentTimeMillis();
            if (!wasBoostingPreviousTick) {
                w.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 0.85f, 1.35f);
                w.playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.65f, 1.65f);
                w.playSound(loc, Sound.BLOCK_PISTON_EXTEND, 0.5f, 1.8f);
                w.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.3, 0), 1, 0.1, 0.1, 0.1, 0.0);
                lastBoostSoundTime = now;
            } else if (now - lastBoostSoundTime >= 240) { // Continuous high-speed gallop wind pulse
                lastBoostSoundTime = now;
                w.playSound(loc, Sound.ENTITY_HORSE_GALLOP, 0.7f, 1.55f);
                w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.8f);
                w.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.25f, 2.0f);
            }

            // Consume stamina tick
            isSpeedBoosting = true;
            wasBoostingPreviousTick = true;
            lastRechargeSoundPlayed = false;
            speedBoostTicksRemaining--;
            speedRecoveryCooldown = 0;
            speedRecoveryDelayTicks = 15; // Holding space resets recovery delay

            // Extra energy drain & degradation while boosting
            data.consumeEnergy(1); // 1 extra EU per tick
            data.degradeModule("speed", 0.002);

            // Particles for sprint boost
            w.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.2, 0), 2, 0.2, 0.05, 0.2, 0.02);
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.4, 0), 1, 0.15, 0.1, 0.15, 0.05);
        } else {
            // Released Space: must wait 15 ticks delay before recovery starts
            isSpeedBoosting = false;
            wasBoostingPreviousTick = false;
            if (speedRecoveryDelayTicks > 0) {
                speedRecoveryDelayTicks--;
            } else {
                // Delay ended -> Recharge stamina slower than consumption (1 tick per 2 ticks)
                speedRecoveryCooldown++;
                if (speedRecoveryCooldown >= 2) {
                    speedRecoveryCooldown = 0;
                    if (speedBoostTicksRemaining < maxSpeedBoostTicks) {
                        speedBoostTicksRemaining = Math.min(maxSpeedBoostTicks, speedBoostTicksRemaining + 1);
                        if (speedBoostTicksRemaining >= maxSpeedBoostTicks && !lastRechargeSoundPlayed) {
                            lastRechargeSoundPlayed = true;
                            Location loc = entity.getLocation();
                            World w = entity.getWorld();
                            w.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);
                            w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 2.0f);
                            w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.9f);
                        }
                    }
                }
            }
        }
    }

    public void handleSpeedBoostInMountController(boolean isJump, Player rider) {
        if (rider == null) {
            rider = getRider();
        }
        handleSpeedBoost(isJump, rider);
    }

    public void notifySpeedCannotJump(Player rider) {
        long now = System.currentTimeMillis();
        if (now - lastSpeedWarningTime < 1000) return;
        lastSpeedWarningTime = now;

        setBossBarNotification("§c§l[TỐC HÀNH] §7Không thể nhảy hoặc bay! §8(Dùng Module Đẩy Phản Lực)", BarColor.RED, 40);
        if (rider != null && rider.isOnline()) {
            rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.6f);
        }
    }

    private void playThrustFlightEffects(Location loc, Player rider) {
        World world = entity.getWorld();
        Location exhaust = loc.clone().add(0, 0.2, 0);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, exhaust, 3, 0.15, 0.05, 0.15, 0.02);
        world.spawnParticle(Particle.FLAME, exhaust, 2, 0.1, 0.05, 0.1, 0.02);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust, 1, 0.1, 0.02, 0.1, 0.01);

        long now = System.currentTimeMillis();
        if (now - lastThrustSoundTime >= 200) {
            lastThrustSoundTime = now;
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.45f, 1.35f);
        }

        updateStatusBossBar();
    }

    public void handleThrustFlightInMountController(boolean isJump, com.ticxo.modelengine.api.nms.entity.wrapper.MoveController moveController, Player rider) {
        if (rider == null) {
            rider = getRider();
        }
        updateMaxThrustFlightTicks();

        if (isJump) {
            if (data.getEnergy() < 3) {
                isThrustFlying = false;
                setBossBarNotification("§c§l[ĐẨY PHẢN LỰC] §cKhông đủ năng lượng để bay! (Cần ≥ 3 EU)", BarColor.RED, 40);
                if (rider != null) {
                    rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f);
                }
                return;
            }

            if (thrustFlightTicksRemaining <= 0) {
                if (isThrustFlying) {
                    World w = entity.getWorld();
                    Location loc = entity.getLocation();
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.3, 0), 8, 0.2, 0.1, 0.2, 0.03);
                    w.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
                }
                isThrustFlying = false;
                setBossBarNotification("§c§l[ĐẨY PHẢN LỰC] §cHết nhiên liệu bay! Tiếp đất để nạp lại.", BarColor.RED, 40);
                return;
            }

            // Execute thrust flight tick
            isThrustFlying = true;
            thrustFlightTicksRemaining--;
            data.consumeEnergy(3);
            data.degradeModule("thrust", 0.005);

            Vector curVel = moveController.getVelocity();
            double curY = curVel != null ? curVel.getY() : 0;
            double newY;
            if (moveController.isOnGround()) {
                newY = 0.52;
            } else if (curY < 0) {
                newY = Math.min(0.35, curY + 0.20);
            } else {
                newY = Math.min(0.42, curY + 0.08);
            }

            moveController.setVelocity(curVel != null ? curVel.getX() : 0, newY, curVel != null ? curVel.getZ() : 0);
            moveController.nullifyFallDistance();
            entity.setFallDistance(0.0f);
            if (rider != null) {
                rider.setFallDistance(0.0f);
            }
            playThrustFlightEffects(entity.getLocation(), rider);
        } else {
            isThrustFlying = false;
            if (moveController.isOnGround()) {
                if (thrustFlightTicksRemaining < maxThrustFlightTicks) {
                    thrustFlightTicksRemaining = Math.min(maxThrustFlightTicks, thrustFlightTicksRemaining + 2);
                }
            } else {
                moveController.nullifyFallDistance();
                entity.setFallDistance(0.0f);
                if (rider != null) {
                    rider.setFallDistance(0.0f);
                }
            }
        }
    }

    public void handleThrustFlightInFallback(boolean isJump, Player rider, Vector moveVec) {
        updateMaxThrustFlightTicks();

        if (isJump) {
            if (data.getEnergy() < 3) {
                isThrustFlying = false;
                setBossBarNotification("§c§l[ĐẨY PHẢN LỰC] §cKhông đủ năng lượng để bay! (Cần ≥ 3 EU)", BarColor.RED, 40);
                if (rider != null) {
                    rider.playSound(rider.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.5f, 1.4f);
                }
                entity.setVelocity(moveVec);
                return;
            }

            if (thrustFlightTicksRemaining <= 0) {
                if (isThrustFlying) {
                    World w = entity.getWorld();
                    Location loc = entity.getLocation();
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.3, 0), 8, 0.2, 0.1, 0.2, 0.03);
                    w.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.8f);
                }
                isThrustFlying = false;
                setBossBarNotification("§c§l[ĐẨY PHẢN LỰC] §cHết nhiên liệu bay! Tiếp đất để nạp lại.", BarColor.RED, 40);
                entity.setVelocity(moveVec);
                return;
            }

            isThrustFlying = true;
            thrustFlightTicksRemaining--;
            data.consumeEnergy(3);
            data.degradeModule("thrust", 0.005);

            double curY = entity.getVelocity().getY();
            double newY;
            if (entity.isOnGround()) {
                newY = 0.52;
            } else if (curY < 0) {
                newY = Math.min(0.35, curY + 0.20);
            } else {
                newY = Math.min(0.42, curY + 0.08);
            }

            moveVec.setY(newY);
            entity.setVelocity(moveVec);
            entity.setFallDistance(0.0f);
            if (rider != null) {
                rider.setFallDistance(0.0f);
            }
            playThrustFlightEffects(entity.getLocation(), rider);
        } else {
            isThrustFlying = false;
            if (entity.isOnGround()) {
                if (thrustFlightTicksRemaining < maxThrustFlightTicks) {
                    thrustFlightTicksRemaining = Math.min(maxThrustFlightTicks, thrustFlightTicksRemaining + 2);
                }
            } else {
                entity.setFallDistance(0.0f);
                if (rider != null) {
                    rider.setFallDistance(0.0f);
                }
            }
            entity.setVelocity(moveVec);
        }
    }

    private void handleThrustTaskIdle() {
        // Idle particles for thrusters
        if (System.currentTimeMillis() - lastThrustSoundTime < 3000) {
            entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, entity.getLocation().add(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.02);
        }
    }

    public boolean executeThrustLaunch() {
        if (!data.hasModule("thrust")) return false;
        if (System.currentTimeMillis() - lastThrustTime < 2500) return false; // 2.5s cooldown

        double eff = data.getModuleEfficiency("thrust");
        int energyCost = 500 - (int) (250 * (eff / 100.0)); // 500 to 250 EU

        if (!data.consumeEnergy(energyCost)) {
            setBossBarNotification("§c§l[ĐẨY PHẢN LỰC] §cKhông đủ EU! (" + data.getEnergy() + "/" + energyCost + " EU)", BarColor.RED, 50);
            return false;
        }

        lastThrustTime = System.currentTimeMillis();
        data.degradeModule("thrust", 0.5);

        // Play thrust animation & effects
        playAnimation("thrust", 0.05, 0.2, 1.5, false);
        World world = entity.getWorld();
        Location loc = entity.getLocation();

        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.8f);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.8f);
        world.spawnParticle(Particle.FLAME, loc, 30, 0.3, 0.2, 0.3, 0.15);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 15, 0.2, 0.2, 0.2, 0.05);

        // Apply high vertical vector impulse
        Vector v = entity.getLocation().getDirection().multiply(0.4);
        v.setY(1.4 + (eff / 100.0) * 0.6); // 1.4 to 2.0 vertical launch
        entity.setVelocity(v);
        if (fallbackSeat != null && fallbackSeat.isValid()) {
            fallbackSeat.setVelocity(v);
        }

        Player rider = getRider();
        if (rider != null) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false));
        }

        return true;
    }

    private boolean isRareOre(Material mat) {
        return mat == Material.DIAMOND_ORE || mat == Material.DEEPSLATE_DIAMOND_ORE ||
                mat == Material.ANCIENT_DEBRIS || mat == Material.EMERALD_ORE ||
                mat == Material.DEEPSLATE_EMERALD_ORE;
    }

    private boolean isCommonOre(Material mat) {
        return mat == Material.IRON_ORE || mat == Material.DEEPSLATE_IRON_ORE ||
                mat == Material.COPPER_ORE || mat == Material.DEEPSLATE_COPPER_ORE ||
                mat == Material.GOLD_ORE || mat == Material.DEEPSLATE_GOLD_ORE ||
                mat == Material.COAL_ORE || mat == Material.DEEPSLATE_COAL_ORE ||
                mat == Material.NOTE_BLOCK; // Lunar custom ores
    }

    public Player getOnlineOwner() {
        if (data.getOwnerUuid() == null) return null;
        return Bukkit.getPlayer(data.getOwnerUuid());
    }

    public Player getRider() {
        // 1. Check ModelEngine driver
        if (activeModel != null) {
            try {
                Optional<?> optMm = activeModel.getMountManager();
                if (optMm.isPresent() && optMm.get() instanceof MountManager mm) {
                    Entity driver = mm.getDriver();
                    if (driver instanceof Player p && p.isOnline()) {
                        return p;
                    }
                }
            } catch (Throwable ignored) {}
        }
        // 2. Check fallback seat
        if (fallbackSeat != null && fallbackSeat.isValid()) {
            for (Entity pass : fallbackSeat.getPassengers()) {
                if (pass instanceof Player p && p.isOnline()) {
                    return p;
                }
            }
        }
        // 3. Check base entity passengers
        for (Entity pass : entity.getPassengers()) {
            if (pass instanceof Player p && p.isOnline()) {
                return p;
            }
        }
        return null;
    }

    public boolean isRider(Player player) {
        if (player == null) return false;
        Player current = getRider();
        return current != null && current.getUniqueId().equals(player.getUniqueId());
    }

    public boolean mountRider(Player player) {
        if (player == null || !player.isOnline()) return false;

        if (data.getEnergy() <= 0) {
            setBossBarNotification("§c§l[ROBOT] §cRobot đã cạn pin, không thể cưỡi!", BarColor.RED, 50);
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8f, 1.2f);
            return false;
        }

        Player currentRider = getRider();
        if (currentRider != null) {
            if (currentRider.getUniqueId().equals(player.getUniqueId())) {
                return true;
            }
            setBossBarNotification("§c§l[ROBOT] §cRobot hiện đang có người cưỡi!", BarColor.RED, 40);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }

        // Unsit robot if it was sitting
        if (data.isSitting()) {
            data.setSitting(false);
            if (entity instanceof Wolf wolf) {
                wolf.setSitting(false);
            }
            updateCustomName();
        }

        boolean mounted = false;

        // 1. Try ModelEngine MountManager first
        if (activeModel != null) {
            try {
                Optional<?> optMm = activeModel.getMountManager();
                if (optMm.isPresent() && optMm.get() instanceof MountManager mm) {
                    mm.setCanDrive(true);
                    mm.setCanRide(true);
                    mounted = mm.mountDriver(player, (e, m) -> new LunarRobotMountController(e, m, this));
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("ModelEngine mountDriver attempt failed, falling back to virtual seat: " + t.getMessage());
            }
        }

        // 2. Fallback to virtual invisible seat entity if ModelEngine mount is unavailable/failed
        if (!mounted) {
            spawnFallbackSeat(player);
        }

        if (data.getActiveTask() == RobotTask.SPEED) {
            setBossBarNotification("§b§l[TỐC HÀNH] §a[W/A/S/D] Di chuyển | Giữ [Space] Bứt Tốc | [Shift] Xuống", BarColor.BLUE, 80);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.8f, 1.2f);
            player.playSound(player.getLocation(), Sound.ENTITY_HORSE_GALLOP, 0.6f, 1.4f);
        } else if (data.getActiveTask() == RobotTask.THRUST) {
            setBossBarNotification("§e§l[ĐẨY PHẢN LỰC] §a[W/A/S/D] Di chuyển | Giữ [Space] Bay | [Shift] Xuống", BarColor.YELLOW, 80);
        } else {
            setBossBarNotification("§b§l[ROBOT] §a" + data.getActiveTask().getFormattedName() + " §8| §f[W/A/S/D] Chạy | [Shift] Xuống", BarColor.GREEN, 80);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_HORSE_ARMOR, 0.8f, 1.2f);
        return true;
    }

    public void dismountRider() {
        isThrustFlying = false;
        isSpeedBoosting = false;
        wasBoostingPreviousTick = false;
        cleanupStatusBossBar();
        if (activeModel != null) {
            try {
                Optional<?> optMm = activeModel.getMountManager();
                if (optMm.isPresent() && optMm.get() instanceof MountManager mm) {
                    mm.dismountDriver();
                }
            } catch (Throwable ignored) {}
        }
        cleanupFallbackSeat();
        try {
            entity.eject();
        } catch (Throwable ignored) {}
    }

    public void onRiderDismounted(Player rider) {
        try {
            if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.35);
            }
        } catch (Throwable ignored) {}
        isThrustFlying = false;
        isSpeedBoosting = false;
        wasBoostingPreviousTick = false;
        cleanupStatusBossBar();
        playAnimation("idle", 0.2, 0.2, 1.0, true);
        cleanupFallbackSeat();
    }

    public void cleanupFallbackSeat() {
        if (fallbackSeat != null) {
            try {
                fallbackSeat.eject();
                if (fallbackSeat.isValid()) {
                    fallbackSeat.remove();
                }
            } catch (Throwable ignored) {}
            fallbackSeat = null;
        }
    }

    private void spawnFallbackSeat(Player player) {
        cleanupFallbackSeat();
        Location seatLoc = entity.getLocation().clone().add(0, 0.65, 0);
        ArmorStand seat = entity.getWorld().spawn(seatLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setSmall(true);
            as.setBasePlate(false);
            as.setArms(false);
            as.setSilent(true);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setCanTick(true);
            PersistentDataContainer pdc = as.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, "is_robot_seat"), PersistentDataType.BYTE, (byte) 1);
            pdc.set(new NamespacedKey(plugin, "robot_uuid"), PersistentDataType.STRING, entity.getUniqueId().toString());
        });
        this.fallbackSeat = seat;
        seat.addPassenger(player);
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public LunarRobotData getData() {
        return data;
    }

    public ActiveModel getActiveModel() {
        return activeModel;
    }

    public ArmorStand getFallbackSeat() {
        return fallbackSeat;
    }

    public void teleportToOwner(Player owner) {
        if (owner == null || !owner.isOnline()) return;
        Location targetLoc = findSafeFollowLocation(owner);
        entity.teleport(targetLoc);
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0.0f);
        playAnimation("idle", 0.2, 0.2, 1.0, true);

        if (plugin instanceof vn.haohan.lunar.HaoHanLunarPlugin lunarPlugin) {
            if (lunarPlugin.getLunarRobotMechanic() != null) {
                lunarPlugin.getLunarRobotMechanic().saveRobotLocationToPlayer(owner, this);
            }
        }

        World world = targetLoc.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.PORTAL, targetLoc.clone().add(0, 0.5, 0), 25, 0.4, 0.5, 0.4, 0.05);
            world.playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.5f);
        }
    }

    public Location findSafeFollowLocation(Player owner) {
        Location ownerLoc = owner.getLocation();
        World world = ownerLoc.getWorld();
        if (world == null) return ownerLoc.clone();

        // 1. Try directly behind player
        Vector dir = ownerLoc.getDirection().setY(0);
        if (dir.lengthSquared() > 0.001) {
            dir.normalize().multiply(-2.0);
            Location behind = ownerLoc.clone().add(dir);
            behind.setYaw(ownerLoc.getYaw());
            behind.setPitch(ownerLoc.getPitch());
            if (isSafeLocation(behind)) {
                return behind;
            }
        }

        // 2. Try in surrounding offsets (radius 2-3 blocks)
        int[][] offsets = {
                {-2, 0}, {2, 0}, {0, -2}, {0, 2},
                {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
                {-3, 0}, {3, 0}, {0, -3}, {0, 3}
        };
        for (int[] offset : offsets) {
            Location candidate = ownerLoc.clone().add(offset[0], 0, offset[1]);
            candidate.setYaw(ownerLoc.getYaw());
            candidate.setPitch(ownerLoc.getPitch());
            if (isSafeLocation(candidate)) {
                return candidate;
            }
            Location candidateUp = candidate.clone().add(0, 1, 0);
            if (isSafeLocation(candidateUp)) {
                return candidateUp;
            }
            Location candidateDown = candidate.clone().add(0, -1, 0);
            if (isSafeLocation(candidateDown)) {
                return candidateDown;
            }
        }

        // Fallback to player location
        return ownerLoc.clone();
    }

    private boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
        Block ground = feet.getRelative(org.bukkit.block.BlockFace.DOWN);

        return ground.getType().isSolid()
                && !feet.getType().isSolid()
                && !feet.isLiquid()
                && !head.getType().isSolid()
                && !head.isLiquid();
    }

    public void updateStatusBossBar() {
        Set<Player> targetViewers = new HashSet<>();
        Player rider = getRider();
        if (rider != null && rider.isOnline()) {
            targetViewers.add(rider);
        }

        Player owner = getOnlineOwner();
        if (owner != null && owner.isOnline() && owner.getWorld().equals(entity.getWorld())) {
            double distSq = owner.getLocation().distanceSquared(entity.getLocation());
            if (distSq <= 1024.0 && (isPlayerHoldingTablet(owner) || notificationTicksRemaining > 0 || rider != null)) {
                targetViewers.add(owner);
            }
        }

        if (targetViewers.isEmpty()) {
            cleanupStatusBossBar();
            return;
        }

        if (statusBossBar == null) {
            statusBossBar = Bukkit.createBossBar("§b§l[ROBOT 4 CHÂN]", BarColor.BLUE, BarStyle.SEGMENTED_10);
            statusBossBar.setVisible(true);
        }

        // Add missing viewers
        for (Player p : targetViewers) {
            if (!statusBossBar.getPlayers().contains(p)) {
                statusBossBar.addPlayer(p);
            }
        }
        // Remove players no longer viewing
        for (Player p : new ArrayList<>(statusBossBar.getPlayers())) {
            if (!targetViewers.contains(p)) {
                statusBossBar.removePlayer(p);
            }
        }

        int energy = data.getEnergy();
        int maxEnergy = Math.max(1, data.getMaxEnergy());
        int integrity = (int) Math.round(data.getIntegrityPercentage());
        double health = data.getHealth();
        double maxHealth = data.getMaxHealth();

        String title;
        double progress;
        BarColor color;
        BarStyle style = BarStyle.SEGMENTED_10;

        String integrityColor = integrity > 50 ? "§a" : (integrity > 25 ? "§e" : "§c");
        String healthStr = integrityColor + "❤ " + integrity + "%";

        RobotTask task = data.getActiveTask();
        if (task == RobotTask.THRUST) {
            int currentTicks = thrustFlightTicksRemaining;
            int maxTicks = Math.max(1, maxThrustFlightTicks);
            progress = Math.max(0.0, Math.min(1.0, (double) currentTicks / maxTicks));
            int pct = (int) Math.round(progress * 100.0);

            if (isThrustFlying) {
                title = "§e§l[ĐẨY PHẢN LỰC] §fBay: §a" + pct + "% §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else if (currentTicks <= 0) {
                title = "§c§l[ĐẨY PHẢN LỰC] §cHết nhiên liệu bay! (Tiếp đất để nạp) §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else if (currentTicks >= maxTicks) {
                title = "§e§l[ĐẨY PHẢN LỰC] §aSẵn sàng (100%) §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else {
                title = "§e§l[ĐẨY PHẢN LỰC] §fNạp nhiên liệu: §e" + pct + "% §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            }

            color = pct > 50 ? BarColor.GREEN : (pct > 20 ? BarColor.YELLOW : BarColor.RED);
        } else if (task == RobotTask.SPEED) {
            int currentTicks = speedBoostTicksRemaining;
            int maxTicks = Math.max(1, maxSpeedBoostTicks);
            progress = Math.max(0.0, Math.min(1.0, (double) currentTicks / maxTicks));
            int pct = (int) Math.round(progress * 100.0);

            if (isSpeedBoosting) {
                title = "§b§l[TĂNG TỐC] §fThể lực: §b" + pct + "% §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else if (currentTicks <= 0) {
                title = "§c§l[TỐC HÀNH] §cHết thể lực! (Đang hồi phục...) §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else if (currentTicks >= maxTicks) {
                title = "§b§l[TỐC HÀNH] §aSẵn sàng bứt tốc (Space) §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            } else {
                title = "§b§l[TỐC HÀNH] §fHồi thể lực: §e" + pct + "% §8| §e⚡ " + String.format("%,d", energy) + " EU §8| " + healthStr;
            }

            color = pct > 50 ? BarColor.BLUE : (pct > 20 ? BarColor.YELLOW : BarColor.RED);
        } else {
            progress = Math.max(0.0, Math.min(1.0, (double) energy / maxEnergy));
            String taskFormatted = task.getFormattedName();
            title = "§b§l[ROBOT] " + taskFormatted + " §8| §e⚡ " + String.format("%,d", energy) + "/" + String.format("%,d", maxEnergy) + " EU §8| " + healthStr;
            color = progress > 0.3 ? BarColor.GREEN : (progress > 0.15 ? BarColor.YELLOW : BarColor.RED);
        }

        // Transient event notification takes highest priority on BossBar
        if (notificationTicksRemaining > 0 && notificationTitle != null) {
            notificationTicksRemaining--;
            title = notificationTitle;
            if (notificationColor != null) {
                color = notificationColor;
            }
        }

        statusBossBar.setTitle(title);
        statusBossBar.setProgress(progress);
        statusBossBar.setColor(color);
        statusBossBar.setStyle(style);
    }

    public void setBossBarNotification(String message, BarColor barColor, int durationTicks) {
        this.notificationTitle = message;
        this.notificationColor = barColor;
        this.notificationTicksRemaining = durationTicks;
        updateStatusBossBar();
    }

    public void cleanupStatusBossBar() {
        if (statusBossBar != null) {
            try {
                statusBossBar.removeAll();
            } catch (Throwable ignored) {}
            statusBossBar = null;
        }
    }

    private boolean isPlayerHoldingTablet(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (plugin instanceof HaoHanLunarPlugin lunarPlugin) {
            LunarRobotMechanic mechanic = lunarPlugin.getLunarRobotMechanic();
            return mechanic != null && mechanic.isHoldingTablet(player);
        }
        return false;
    }

    public void cleanup() {
        dismountRider();
        cleanupFallbackSeat();
        cleanupHolograms();
        cleanupStatusBossBar();
        if (activeModel != null) {
            try {
                activeModel.destroy();
            } catch (Throwable ignored) {}
        }
        if (modeledEntity != null) {
            try {
                modeledEntity.destroy();
            } catch (Throwable ignored) {}
        }
        try {
            ModelEngineAPI.removeModeledEntity(entity.getUniqueId());
        } catch (Throwable ignored) {}
    }

    public void cleanupHolograms() {
        for (ItemDisplay d : activeHolograms) {
            if (d.isValid()) d.remove();
        }
        activeHolograms.clear();
    }
}
