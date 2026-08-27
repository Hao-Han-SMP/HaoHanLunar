package vn.haohan.lunar.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.entity.BaseEntity;
import com.ticxo.modelengine.api.entity.data.IEntityData;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.ManualAnimator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Souls-like (Elden Ring style) AI for The Lunar Warden boss.
 * Features:
 * - Differentiated Shield Bulldozer Charge (skill_shield_charge: Ở giữa khiên bị đẩy cuốn đi và nhận sát thương liên tục đa nhịp, càng gần tâm càng đau; ở rìa khiên bị quẹt nhẹ hất sang bên và nhận sát thương thấp hơn).
 * - Perfect 100% Complete Animation Timing for skill_charge_summon (Bảo tồn trọn vẹn toàn bộ chuỗi animation bao gồm đoạn hạ kiếm và thu kiếm vào bao).
 * - Relentless Zig-Zag Phantom Pursuit (Lướt nhảy zíc-zắc 2-3 block trái phải ngẫu nhiên truy đuổi mục tiêu từ xa và chuẩn bị ra đòn chém bất ngờ).
 * - Anti-Kite / Anti-Stall Phantom Warp Strike (Dịch chuyển tức thời cắt đầu/sau lưng ra đòn tức thì khi player chạy vòng quanh thả diều ở rìa tầm đánh).
 * - Priority Execute System (Ưu tiên nhắm và kết liễu người chơi có lượng máu thấp nhất trước).
 * - Full Epic Synchronized Celestial Ultimate Spell (Chiêu Phán Quyết Mặt Trăng chuẩn 30s hồi chiêu, tụ lực và mưa phán quyết).
 * - 3-Block Step-Height System (Khả năng tự động bước/vượt qua độ cao địa hình lên tới 3 block).
 * - Dynamic Damage Threat & Aggro System (Ưu tiên nhắm người gây sát thương cao nhất khi không có mục tiêu yếu máu).
 * - Wide Crescent Sword Energy Wave (Kiếm khí hình lưỡi liềm bán nguyệt rộng quét tầm xa, bám địa hình, đa điểm chạm).
 * - Long-Range Evasive Shadow-Step (Tầm nhảy né đòn siêu xa 7.5 - 11.5 blocks).
 * - Sustained Celestial Judgment Light Pillars (Cột sáng phán quyết giáng từ trời, duy trì hiệu ứng laser và chấn động).
 * - Named Static Task Classes (Tránh triệt để NoClassDefFoundError khi reload plugin).
 * - Smart Adaptive Lunge & Point-Blank / Close-Range Corridor Hitbox for Thrust Grab.
 * - Perfectly Balanced Impact Stagger Knockback for regular slashes.
 * - Direct Culling Bypass via ModelEngine API.
 * - Full Creative Mode Combat Support.
 * - AOE Seismic Ground Shatter Slam.
 * - Precise Tip-Aligned Impale Pinning.
 * - Dynamic Combos & Real-time head/eye tracking.
 */
public class LunarWardenMechanic implements Listener {

    private final HaoHanLunarPlugin plugin;
    private final Map<UUID, BossState> bossStates = new HashMap<>();
    private final Random random = new Random();

    // All known animation names for TheLunarWarden
    private static final String[] ATTACK_ANIMATIONS = {
        "attack_slash_left",
        "attack_sweep_right",
        "attack_slash_straight",
        "attack_thrust_fling",
        "skill_charge_summon",
        "skill_shield_charge"
    };

    // Movement speed constants
    private static final double WALK_FORWARD_SPEED = 0.11;
    private static final double WALK_CHASE_SPEED = 0.165;
    private static final double WALK_STRAFE_SPEED = 0.095;
    private static final double WALK_BACKWARD_SPEED = 0.085;
    private static final double BOSS_HEAD_HEIGHT = 5.2;

    public enum Behavior {
        ADVANCE,
        STRAFE_LEFT,
        STRAFE_RIGHT,
        RETREAT,
        IDLE_STARE,
        ATTACKING
    }

    public LunarWardenMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startAITask();
    }

    public static class BossState {
        Behavior currentBehavior = Behavior.ADVANCE;
        int behaviorTimer = 0;
        double currentForwardSpeed = 0;
        double currentStrafeSpeed = 0;
        float headPitch = 0f;
        float headYawLocal = 0f;

        // Locomotion animation state tracker
        String currentPlayingMovementAnim = "";

        // Threat & Aggro System
        final Map<UUID, Double> damageThreatTable = new HashMap<>();
        UUID currentTargetUUID = null;

        // Zig-Zag Pursuit System
        int zigZagPursuitCooldown = 0;
        boolean isExecutingZigZagPursuit = false;

        // Anti-Kiting & Chase-Stall Counter System
        int chaseStallTimer = 0;

        // Attack & Combo State
        String currentAttack = "";
        String queuedComboAttack = "";
        int attackTicks = 0;
        int attackTotalTicks = 0;
        int attackHitTick = 0;
        boolean attackHitDone = false;
        boolean isMovingAttack = false;
        int attackCooldown = 25;

        // Multi-Tick Hitbox Tracking
        final Set<UUID> hitVictimsThisAttack = new HashSet<>();
        final Map<UUID, Integer> multiHitTickMap = new HashMap<>();

        // Agility & Dodge System
        int dashCooldown = 80;

        // Skill Cooldowns
        int summonSkillCooldown = 600;      // ~30s cooldown for signature ultimate
        int shieldChargeCooldown = 160;     // ~14-20s cooldown for AOE shield bulldozer rush

        // Impale Grab State
        UUID impaledTargetUUID = null;
        boolean flingExecuted = false;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/spawnwarden")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.isOp()) {
                spawnWarden(player.getLocation());
                player.sendMessage("§aSpawned TheLunarWarden!");
            }
        }
    }

    /**
     * Threat Tracker & Reactive Evasion when Boss takes damage.
     * Records damage into damageThreatTable to determine dynamic primary/secondary targets.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        BossState state = bossStates.get(golem.getUniqueId());
        if (state == null) return;

        // Extract attacking player
        Player damagerPlayer = null;
        if (event.getDamager() instanceof Player p) {
            damagerPlayer = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            damagerPlayer = p;
        }

        if (damagerPlayer == null) return;

        // 1. Accumulate Damage into Threat Table
        double dmg = event.getFinalDamage() > 0 ? event.getFinalDamage() : 1.0;
        double currentDmg = state.damageThreatTable.getOrDefault(damagerPlayer.getUniqueId(), 0.0);
        state.damageThreatTable.put(damagerPlayer.getUniqueId(), currentDmg + dmg);

        // Do not interrupt signature channel animations, heavy shield charges, or ongoing attacks
        if (state.currentBehavior == Behavior.ATTACKING || state.currentAttack.equals("skill_charge_summon") || state.currentAttack.equals("skill_shield_charge") || state.currentAttack.equals("attack_thrust_fling") || state.isExecutingZigZagPursuit) {
            return;
        }

        double dist = golem.getLocation().distance(damagerPlayer.getLocation());
        // If player is attacking in close-quarters (<= 3.8 blocks) and dash is ready
        if (dist <= 3.8 && state.dashCooldown <= 0) {
            if (random.nextInt(100) < 45) { // 45% chance to reactive-dodge incoming melee hit
                performEvasiveShadowHop(golem, state, damagerPlayer.getLocation());
            }
        }
    }

    public void spawnWarden(Location loc) {
        IronGolem golem = loc.getWorld().spawn(loc, IronGolem.class);
        golem.setCustomName("§c§lThe Lunar Warden");
        golem.setCustomNameVisible(true);
        golem.setMaxHealth(1000);
        golem.setHealth(1000);
        golem.setAware(false);

        if (golem.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            golem.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
        }

        // Apply 3.0 Block Step Height Attribute (Minecraft 1.21+ / Paper)
        try {
            if (golem.getAttribute(Attribute.STEP_HEIGHT) != null) {
                golem.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(3.0);
            }
        } catch (Throwable ignored) {}

        ActiveModel activeModel = ModelEngineAPI.createActiveModel("thelunarwarden");
        if (activeModel == null) return;

        ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(golem);
        modeledEntity.addModel(activeModel, true);
        modeledEntity.setBaseEntityVisible(false);
        modeledEntity.setModelRotationLocked(true);

        // Force continuous packet sending even when player is facing away or stationary
        activeModel.setInvisUpdate(true);
        activeModel.setViewRange(2.0f);
        if (modeledEntity.getAnimationLodHandler() != null) {
            modeledEntity.getAnimationLodHandler().setEnabled(false);
        }

        // Disable ModelEngine Culling completely on this boss entity
        if (modeledEntity.getBase() != null) {
            IEntityData data = modeledEntity.getBase().getData();
            if (data != null) {
                data.setBackCull(false);
                data.setBlockedCull(false);
                data.setVerticalCull(false);
            }
        }

        activeModel.setScale(3.0f);
        activeModel.setHitboxScale(3.0f);

        final BossState state = new BossState();
        bossStates.put(golem.getUniqueId(), state);

        activeModel.getBone("head").ifPresent(headBone -> {
            headBone.setManualAnimator(new ManualAnimator() {
                @Override
                public boolean applyBoneDefaultLocal() {
                    return true;
                }

                @Override
                public void animate(ModelBone bone) {
                    bone.getLocalTransform().mutateLeftEuler(euler -> {
                        euler.x = (float) Math.toRadians(state.headPitch);
                        euler.y = (float) Math.toRadians(state.headYawLocal);
                        euler.z = 0f;
                    });
                }
            });
        });

        // Initialize with idle animation
        playModelAnimation(golem, state, "idle", 0.1, 0.1, 1.0, true);
    }

    private void startAITask() {
        new BossAITask(this).runTaskTimer(plugin, 1L, 1L);
    }

    private static class BossAITask extends BukkitRunnable {
        private final LunarWardenMechanic mechanic;

        public BossAITask(LunarWardenMechanic mechanic) {
            this.mechanic = mechanic;
        }

        @Override
        public void run() {
            mechanic.bossStates.keySet().removeIf(uuid -> {
                Entity entity = Bukkit.getEntity(uuid);
                BossState state = mechanic.bossStates.get(uuid);
                if (entity == null || entity.isDead() || !(entity instanceof IronGolem golem)) {
                    // Release any impaled player if boss dies or despawns
                    if (state != null && state.impaledTargetUUID != null) {
                        Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                        if (victim instanceof Player p) {
                            p.removePotionEffect(PotionEffectType.SLOWNESS);
                        }
                    }
                    return true;
                }
                try {
                    mechanic.handleBossAI(golem, state);
                } catch (Exception e) {
                    mechanic.plugin.getLogger().severe("Error in LunarWarden AI: " + e.getMessage());
                    e.printStackTrace();
                }
                return false;
            });
        }
    }

    private void handleBossAI(IronGolem golem, BossState state) {
        Location golemLoc = golem.getLocation();

        // Ensure LOD bypass, back-cull bypass, and invis update are maintained
        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            if (modeledEntity.getAnimationLodHandler() != null && !Boolean.FALSE.equals(modeledEntity.getAnimationLodHandler().getEnabled())) {
                modeledEntity.getAnimationLodHandler().setEnabled(false);
            }
            if (modeledEntity.getBase() != null) {
                IEntityData data = modeledEntity.getBase().getData();
                if (data != null) {
                    if (Boolean.TRUE.equals(data.getBackCull())) data.setBackCull(false);
                    if (Boolean.TRUE.equals(data.getBlockedCull())) data.setBlockedCull(false);
                    if (Boolean.TRUE.equals(data.getVerticalCull())) data.setVerticalCull(false);
                }
            }
            modeledEntity.getModel("thelunarwarden").ifPresent(m -> {
                if (!m.isInvisUpdate()) m.setInvisUpdate(true);
            });
        }

        if (state.summonSkillCooldown > 0) state.summonSkillCooldown--;
        if (state.shieldChargeCooldown > 0) state.shieldChargeCooldown--;
        if (state.dashCooldown > 0) state.dashCooldown--;
        if (state.zigZagPursuitCooldown > 0) state.zigZagPursuitCooldown--;

        // Select primary/secondary target according to Threat & Proximity system
        Player target = selectBestTarget(golem, state);

        if (target == null) {
            // Target lost / dead / out of range
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p) {
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                }
                state.impaledTargetUUID = null;
            }

            state.chaseStallTimer = 0;
            state.currentBehavior = Behavior.IDLE_STARE;
            state.currentAttack = "";
            state.queuedComboAttack = "";
            state.flingExecuted = false;
            state.isMovingAttack = false;

            Vector vel = golem.getVelocity();
            vel.setX(vel.getX() * 0.5);
            vel.setZ(vel.getZ() * 0.5);
            golem.setVelocity(vel);
            state.headPitch = lerpAngle(state.headPitch, 0f, 0.1f);
            state.headYawLocal = lerpAngle(state.headYawLocal, 0f, 0.1f);

            if (modeledEntity != null) {
                modeledEntity.setYHeadRot(golemLoc.getYaw());
                modeledEntity.setXHeadRot(0f);
                modeledEntity.setYBodyRot(golemLoc.getYaw());
            }

            playModelAnimation(golem, state, "idle", 0.30, 0.30, 1.0, true);
            return;
        }

        // If boss is actively performing the Zig-Zag Pursuit sequence, let the runnable handle motion
        if (state.isExecutingZigZagPursuit) {
            state.chaseStallTimer = 0;
            return;
        }

        Location targetEye = target.getEyeLocation();
        Location bossEye = golemLoc.clone().add(0, BOSS_HEAD_HEIGHT, 0);

        Vector dirToTarget = targetEye.toVector().subtract(bossEye.toVector());
        double dx = dirToTarget.getX();
        double dy = dirToTarget.getY();
        double dz = dirToTarget.getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        double targetHeightDiff = target.getLocation().getY() - golemLoc.getY();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

        if (state.attackCooldown > 0) {
            state.attackCooldown--;
        }

        // ==============================================
        // 1. ATTACK / SKILL STATE HANDLING
        // ==============================================
        if (state.currentBehavior == Behavior.ATTACKING) {
            state.chaseStallTimer = 0; // Reset chase stall while actively in combat attack
            handleAttackExecution(golem, state, target, targetYaw, targetPitch, distXZ);
            return;
        }

        // ==============================================
        // 2. LONG-RANGE TARGET PURSUIT: ZIG-ZAG PHANTOM DASH (Khoảng cách xa >= 8.5m)
        // Khi mục tiêu (người gây dmg chính, người ít máu, hoặc người chơi đang giữ khoảng cách) ở xa:
        // Boss kích hoạt chuỗi nhảy lướt zíc-zắc 2-3 block trái phải ngẫu nhiên tiếp cận cực nhanh và chém bất ngờ!
        // ==============================================
        if (distXZ >= 8.5 && distXZ <= 40.0 && state.zigZagPursuitCooldown <= 0 && !state.isExecutingZigZagPursuit) {
            state.chaseStallTimer = 0;
            executeZigZagPhantomPursuit(golem, state, target);
            return;
        }

        // ==============================================
        // 3. ANTI-KITING / ANTI-STALL WARP AMBUSH (Khoảng cách trung bình 3.2m -> 8.5m)
        // Khi người chơi chạy vòng tròn quanh boss hoặc thả diều ở rìa tầm đánh trong ~3 giây (60 ticks):
        // Boss dịch chuyển tức thời ra sau lưng/trước mặt cắt đường chạy và chém ngay lập tức!
        // ==============================================
        if (distXZ > 3.2 && distXZ < 8.5) {
            state.chaseStallTimer++;
            if (state.chaseStallTimer >= 60) {
                executeAntiKitePhantomWarpStrike(golem, state, target);
                return;
            }
        } else {
            state.chaseStallTimer = 0;
        }

        // ==============================================
        // 4. CLOSE-RANGE PROXIMITY EVASION (Khi người chơi áp sát, nhảy né xa 7.5 - 11.5 blocks)
        // ==============================================
        if (state.dashCooldown <= 0 && distXZ <= 3.2) {
            if (random.nextInt(100) < 40) { // 40% chance to evasively hop far away
                performEvasiveShadowHop(golem, state, target.getLocation());
                return;
            }
        }

        // Medium-Range Agile Flank Dash (Lướt bọc sườn)
        if (state.dashCooldown <= 0 && distXZ >= 3.5 && distXZ <= 8.5) {
            boolean playerSprintingIn = target.isSprinting() && distXZ < 6.0;
            boolean flanking = Math.abs(normalizeAngle(targetYaw - golemLoc.getYaw())) > 60.0f;
            boolean randomEvadeRoll = random.nextInt(100) < 18;

            if (playerSprintingIn || flanking || randomEvadeRoll) {
                performAgileDash(golem, state, targetYaw, distXZ);
                return;
            }
        }

        // ==============================================
        // 5. CHECK ATTACK & SKILL TRIGGERS
        // ==============================================
        // Signature Ultimate Spell: skill_charge_summon (Cooldown ~30s)
        if (state.attackCooldown <= 0 && state.summonSkillCooldown <= 0 && distXZ >= 5.0 && distXZ <= 25.0) {
            if (random.nextInt(100) < 35) {
                triggerSummonSkill(golem, state);
                return;
            } else {
                state.summonSkillCooldown = 100 + random.nextInt(60);
            }
        }

        // AOE Shield Bulldozer Rush: skill_shield_charge (Distance 4.5m -> 18.0m, Cooldown ~14-20s)
        if (state.attackCooldown <= 0 && state.shieldChargeCooldown <= 0 && distXZ >= 4.5 && distXZ <= 18.0) {
            if (random.nextInt(100) < 40) {
                triggerShieldChargeSkill(golem, state);
                return;
            } else {
                state.shieldChargeCooldown = 60 + random.nextInt(40);
            }
        }

        // Standard Attack Reach (up to 8.5m)
        if (state.attackCooldown <= 0 && distXZ <= 8.5) {
            triggerAttack(golem, state, distXZ, targetHeightDiff);
            return;
        }

        // ==============================================
        // 6. REGULAR LOCOMOTION & AGILE ROTATION
        // ==============================================
        float currentYaw = golemLoc.getYaw();
        float yawDiff = normalizeAngle(targetYaw - currentYaw);
        
        float normalizedDiff = Math.min(1.0f, Math.abs(yawDiff) / 120.0f);
        float currentMaxTurnSpeed = 2.5f + (6.5f * normalizedDiff); // Snappier rotation

        float newYaw;
        if (Math.abs(yawDiff) <= currentMaxTurnSpeed) {
            newYaw = targetYaw;
        } else {
            newYaw = currentYaw + Math.signum(yawDiff) * currentMaxTurnSpeed;
        }
        newYaw = normalizeAngle(newYaw);

        golem.setRotation(newYaw, state.headPitch);

        float rawDesiredLocalYaw = normalizeAngle(targetYaw - newYaw);
        float desiredLocalYaw = Math.max(-75f, Math.min(75f, rawDesiredLocalYaw)); 
        float desiredPitch = Math.max(-60f, Math.min(60f, targetPitch));           

        state.headYawLocal = lerpAngle(state.headYawLocal, desiredLocalYaw, 0.30f);
        state.headPitch = lerpAngle(state.headPitch, desiredPitch, 0.25f);

        if (modeledEntity != null) {
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
            modeledEntity.setYBodyRot(newYaw);

            BaseEntity<?> base = modeledEntity.getBase();
            if (base != null && base.getLookController() != null) {
                base.getLookController().lookAt(targetEye.getX(), targetEye.getY(), targetEye.getZ());
            }
        }

        state.behaviorTimer--;
        if (state.behaviorTimer <= 0) {
            if (distXZ > 8.0) {
                state.currentBehavior = Behavior.ADVANCE;
                state.behaviorTimer = 25 + random.nextInt(25);
            } else if (distXZ < 2.8) {
                state.currentBehavior = Behavior.RETREAT;
                state.behaviorTimer = 20 + random.nextInt(20);
            } else {
                int roll = random.nextInt(100);
                if (roll < 50) {
                    state.currentBehavior = Behavior.ADVANCE;
                    state.behaviorTimer = 25 + random.nextInt(30);
                } else if (roll < 85) {
                    state.currentBehavior = random.nextBoolean() ? Behavior.STRAFE_LEFT : Behavior.STRAFE_RIGHT;
                    state.behaviorTimer = 30 + random.nextInt(30);
                } else {
                    state.currentBehavior = Behavior.IDLE_STARE;
                    state.behaviorTimer = 15 + random.nextInt(15);
                }
            }
        }

        if (distXZ > 10.0) state.currentBehavior = Behavior.ADVANCE;
        else if (distXZ < 2.0) state.currentBehavior = Behavior.RETREAT;

        double desiredForward = 0.0;
        double desiredStrafe = 0.0;

        switch (state.currentBehavior) {
            case ADVANCE -> desiredForward = distXZ > 10.0 ? WALK_CHASE_SPEED : WALK_FORWARD_SPEED;
            case RETREAT -> desiredForward = -WALK_BACKWARD_SPEED;
            case STRAFE_LEFT -> desiredStrafe = -WALK_STRAFE_SPEED;
            case STRAFE_RIGHT -> desiredStrafe = WALK_STRAFE_SPEED;
            case IDLE_STARE -> {
                desiredForward = 0.0;
                desiredStrafe = 0.0;
            }
        }

        state.currentForwardSpeed = lerp(state.currentForwardSpeed, desiredForward, 0.25);
        state.currentStrafeSpeed = lerp(state.currentStrafeSpeed, desiredStrafe, 0.25);

        double radFacing = Math.toRadians(newYaw);
        Vector forwardVec = new Vector(-Math.sin(radFacing), 0, Math.cos(radFacing));
        Vector rightVec = new Vector(Math.cos(radFacing), 0, Math.sin(radFacing));

        Vector moveVelocity = forwardVec.multiply(state.currentForwardSpeed)
                .add(rightVec.multiply(state.currentStrafeSpeed));

        Vector entityVelocity = golem.getVelocity();
        entityVelocity.setX(moveVelocity.getX());
        entityVelocity.setZ(moveVelocity.getZ());

        // Apply 3-Block Step-Height Assist during navigation
        apply3BlockStepAssist(golem, moveVelocity, entityVelocity);

        golem.setVelocity(entityVelocity);

        // Locomotion animation selection
        String nextAnim = "idle";
        double fw = state.currentForwardSpeed;
        double st = state.currentStrafeSpeed;

        if (Math.abs(fw) > 0.015 || Math.abs(st) > 0.015) {
            if (Math.abs(fw) >= Math.abs(st)) {
                nextAnim = fw > 0 ? "walk_forward" : "walk_backward";
            } else {
                nextAnim = st > 0 ? "walk_right" : "walk_left";
            }
        }

        playModelAnimation(golem, state, nextAnim, 0.20, 0.20, 1.0, true);
    }

    /**
     * Anti-Kiting Phantom Warp Strike:
     * When a player runs in circles or kites the boss at close-mid range (3.2m - 8.5m) for >= 3 seconds without boss landing hits,
     * boss shadow-warps directly adjacent to the player and launches an immediate, unavoidable ambush strike!
     */
    private void executeAntiKitePhantomWarpStrike(IronGolem golem, BossState state, Player target) {
        state.chaseStallTimer = 0;
        Location currentLoc = golem.getLocation();
        Location targetLoc = target.getLocation();

        // 1. Calculate teleport point right in front of target's facing or slightly behind
        Vector targetFacing = target.getLocation().getDirection().setY(0).normalize();
        if (targetFacing.lengthSquared() < 0.01) {
            targetFacing = targetLoc.toVector().subtract(currentLoc.toVector()).setY(0).normalize();
        }

        // Warp 2.2m ahead or behind the kiting player to cut off their route
        Location warpLoc = targetLoc.clone().add(targetFacing.clone().multiply(random.nextBoolean() ? 2.2 : -2.0));
        warpLoc = adjustToTerrainSurface(warpLoc, 0.0);

        if (warpLoc.getBlock().getType().isSolid()) {
            warpLoc = adjustToTerrainSurface(targetLoc.clone().add(0, 0, 0), 0.0);
        }

        // 2. Teleport Burst Visuals & Sounds
        Location startFx = currentLoc.clone().add(0, 1.8, 0);
        currentLoc.getWorld().spawnParticle(Particle.PORTAL, startFx, 45, 0.8, 1.2, 0.8, 0.6);
        currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, startFx, 3, 0.4, 0.2, 0.4, 0);
        currentLoc.getWorld().spawnParticle(Particle.DUST, startFx, 40, 0.5, 0.8, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.2f));
        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.8f, 1.5f);
        currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.5f, 1.4f);

        // 3. Teleport & align rotation squarely at player
        Vector dirToTarget = targetLoc.toVector().subtract(warpLoc.toVector()).setY(0).normalize();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dirToTarget.getX(), dirToTarget.getZ()));
        warpLoc.setYaw(targetYaw);
        warpLoc.setPitch(0f);
        golem.teleport(warpLoc);

        // Arrival FX
        Location arriveFx = warpLoc.clone().add(0, 1.5, 0);
        warpLoc.getWorld().spawnParticle(Particle.PORTAL, arriveFx, 40, 0.7, 1.0, 0.7, 0.5);
        warpLoc.getWorld().spawnParticle(Particle.DUST, arriveFx, 40, 0.6, 0.7, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(90, 240, 255), 2.0f));
        warpLoc.getWorld().playSound(warpLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 1.8f);

        // 4. Immediately trigger ambush strike
        int roll = random.nextInt(100);
        String ambushAnim = roll < 35 ? "attack_slash_left" : (roll < 70 ? "attack_sweep_right" : "skill_shield_charge");
        triggerSurpriseAttack(golem, state, ambushAnim, target);
    }

    /**
     * 3-Block Step-Height Navigation Assist:
     * Checks if there are solid blocks in front of the boss (up to 3 blocks higher than current feet).
     * If the obstacle is 1, 2, or 3 blocks tall and has clear headroom above, smoothly lifts the boss
     * so it effortlessly steps right over walls, ledges, stairs, and terrain elevations without getting stuck!
     */
    private void apply3BlockStepAssist(IronGolem golem, Vector moveVelocity, Vector entityVelocity) {
        if (moveVelocity.lengthSquared() < 0.0001) return;
        Location currentLoc = golem.getLocation();
        World world = currentLoc.getWorld();
        if (world == null) return;

        Vector dir = moveVelocity.clone().setY(0).normalize();
        Location aheadLoc = currentLoc.clone().add(dir.multiply(0.85));

        int feetY = currentLoc.getBlockY();
        int aheadX = aheadLoc.getBlockX();
        int aheadZ = aheadLoc.getBlockZ();

        // Check for the highest solid block in the column in front from feetY up to feetY + 3
        int highestObstacleY = -1;
        for (int checkY = feetY + 3; checkY >= feetY; checkY--) {
            Block b = world.getBlockAt(aheadX, checkY, aheadZ);
            if (!b.isPassable() && b.getType().isSolid()) {
                highestObstacleY = checkY;
                break;
            }
        }

        if (highestObstacleY != -1) {
            // Check clearance above the obstacle (ensure at least 3 blocks of headroom for boss)
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

                // If obstacle is between 0.15m and 3.25m higher than current feet
                if (heightDiff > 0.15 && heightDiff <= 3.25) {
                    if (heightDiff <= 1.1) {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.38));
                    } else if (heightDiff <= 2.1) {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.52));
                    } else {
                        entityVelocity.setY(Math.max(entityVelocity.getY(), 0.68));
                    }

                    // Smooth glide onto the step
                    if (currentLoc.getY() + 0.35 >= targetStepY - 0.2) {
                        Location stepUpLoc = currentLoc.clone();
                        stepUpLoc.setY(targetStepY);
                        golem.teleport(stepUpLoc);
                    }
                }
            }
        }
    }

    /**
     * Intelligent Target Selection System:
     * 1. PRIORITY EXECUTION: If any player is low health (<= 35% HP or <= 7.0 HP), boss immediately prioritizes
     *    executing them first before dealing with the main DPS!
     * 2. Evaluates damage dealt by each player in damageThreatTable.
     * 3. If top damager is within reasonable range (<= 14m) or overwhelming DPS, boss focuses them.
     * 4. If top damager is far (> 14m) and damage is not dominant, boss shifts aggro to closer damagers (<= 10m).
     */
    private Player selectBestTarget(IronGolem golem, BossState state) {
        Location golemLoc = golem.getLocation();
        if (golemLoc.getWorld() == null) return null;

        // Clean up dead/offline players
        state.damageThreatTable.keySet().removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            return p == null || !p.isValid() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getWorld() != golemLoc.getWorld();
        });

        // Collect all valid nearby players within 50m
        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player p : golemLoc.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
            if (p.getLocation().distanceSquared(golemLoc) <= 50.0 * 50.0) {
                nearbyPlayers.add(p);
            }
        }

        if (nearbyPlayers.isEmpty()) return null;

        // ====================================================
        // PRIORITY 1: EXECUTE LOW-HEALTH / VULNERABLE PLAYERS
        // ====================================================
        List<Player> lowHealthPlayers = new ArrayList<>();
        for (Player p : nearbyPlayers) {
            double maxHp = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            double hp = p.getHealth();
            double hpRatio = hp / maxHp;

            // Target has <= 35% health or <= 7.0 HP (low health threshold) and within 35m
            if ((hp <= 7.0 || hpRatio <= 0.35) && p.getLocation().distanceSquared(golemLoc) <= 35.0 * 35.0) {
                lowHealthPlayers.add(p);
            }
        }

        if (!lowHealthPlayers.isEmpty()) {
            // Sort by lowest HP first, then by closest distance
            lowHealthPlayers.sort((p1, p2) -> {
                int hpCmp = Double.compare(p1.getHealth(), p2.getHealth());
                if (hpCmp != 0) return hpCmp;
                return Double.compare(p1.getLocation().distanceSquared(golemLoc), p2.getLocation().distanceSquared(golemLoc));
            });

            Player executeTarget = lowHealthPlayers.get(0);
            state.currentTargetUUID = executeTarget.getUniqueId();
            return executeTarget;
        }

        // ====================================================
        // PRIORITY 2: THREAT & PROXIMITY BASED TARGETING
        // ====================================================
        // If no one has dealt damage yet, target closest player
        if (state.damageThreatTable.isEmpty()) {
            nearbyPlayers.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(golemLoc)));
            Player target = nearbyPlayers.get(0);
            state.currentTargetUUID = target.getUniqueId();
            return target;
        }

        // Sort active players by damage dealt descending
        nearbyPlayers.sort((p1, p2) -> {
            double d1 = state.damageThreatTable.getOrDefault(p1.getUniqueId(), 0.0);
            double d2 = state.damageThreatTable.getOrDefault(p2.getUniqueId(), 0.0);
            return Double.compare(d2, d1);
        });

        Player topDamager = nearbyPlayers.get(0);
        double topDmg = state.damageThreatTable.getOrDefault(topDamager.getUniqueId(), 0.0);
        double secondDmg = nearbyPlayers.size() > 1 ? state.damageThreatTable.getOrDefault(nearbyPlayers.get(1).getUniqueId(), 0.0) : 0.0;
        double topDist = golemLoc.distance(topDamager.getLocation());

        // Condition A: Top damager within 14m OR solo player OR top damager has dominant damage (>= 1.5x 2nd place)
        if (topDist <= 14.0 || nearbyPlayers.size() == 1 || topDmg >= secondDmg * 1.5) {
            state.currentTargetUUID = topDamager.getUniqueId();
            return topDamager;
        }

        // Condition B: Top damager is far (> 14m) and damage is not dominant -> check closer 2nd/3rd players (<= 10m)
        for (Player other : nearbyPlayers) {
            if (other.equals(topDamager)) continue;
            double otherDist = golemLoc.distance(other.getLocation());
            if (otherDist <= 10.0) {
                state.currentTargetUUID = other.getUniqueId();
                return other;
            }
        }

        // Default fallback to top damager
        state.currentTargetUUID = topDamager.getUniqueId();
        return topDamager;
    }

    /**
     * Relentless Zig-Zag Phantom Pursuit:
     * When target (top-damager, fleeing sniper, or low-HP player) stays far away (>= 8.5m),
     * boss performs rapid evasive zig-zag leaps shifting 2-3 blocks left and right randomly while surging towards them,
     * and prepares a buffered surprise ambush slash!
     */
    private void executeZigZagPhantomPursuit(IronGolem golem, BossState state, Player target) {
        state.isExecutingZigZagPursuit = true;
        state.zigZagPursuitCooldown = 120 + random.nextInt(60); // 6s - 9s cooldown
        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.8f, 1.6f);
        golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 1.8f);

        new ZigZagPursuitTask(this, golem, state, target).runTaskTimer(plugin, 3L, 5L);
    }

    private static class ZigZagPursuitTask extends BukkitRunnable {
        private final LunarWardenMechanic mechanic;
        private final IronGolem golem;
        private final BossState state;
        private final Player target;
        private int step = 0;
        private final int maxSteps = 4;
        private int lateralSign;

        public ZigZagPursuitTask(LunarWardenMechanic mechanic, IronGolem golem, BossState state, Player target) {
            this.mechanic = mechanic;
            this.golem = golem;
            this.state = state;
            this.target = target;
            this.lateralSign = mechanic.random.nextBoolean() ? 1 : -1;
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
                state.isExecutingZigZagPursuit = false;
                cancel();
                return;
            }
            dirToTarget.normalize();

            Vector cross = new Vector(-dirToTarget.getZ(), 0, dirToTarget.getX()).normalize();
            double lateralOffset = (2.2 + (mechanic.random.nextDouble() * 1.2)) * lateralSign;
            lateralSign = -lateralSign;

            double forwardStep = Math.min(dist - 2.2, 4.5 + (mechanic.random.nextDouble() * 1.2));
            if (forwardStep < 1.0) forwardStep = 1.0;

            Location nextLeapLoc = currentLoc.clone()
                    .add(dirToTarget.clone().multiply(forwardStep))
                    .add(cross.clone().multiply(lateralOffset));

            nextLeapLoc = mechanic.adjustToTerrainSurface(nextLeapLoc, 0.0);

            if (nextLeapLoc.getBlock().getType().isSolid()) {
                nextLeapLoc = currentLoc.clone().add(dirToTarget.clone().multiply(forwardStep));
            }

            Location burstLoc = currentLoc.clone().add(0, 1.5, 0);
            currentLoc.getWorld().spawnParticle(Particle.PORTAL, burstLoc, 35, 0.7, 1.0, 0.7, 0.5);
            currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, burstLoc, 2, 0.5, 0.2, 0.5, 0);
            currentLoc.getWorld().spawnParticle(Particle.DUST, burstLoc, 30, 0.5, 0.7, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f));
            currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.5f + (step * 0.1f));
            currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.5f);

            float targetYaw = (float) Math.toDegrees(Math.atan2(-dirToTarget.getX(), dirToTarget.getZ()));
            nextLeapLoc.setYaw(targetYaw);
            nextLeapLoc.setPitch(currentLoc.getPitch());
            golem.teleport(nextLeapLoc);
            golem.setVelocity(dirToTarget.clone().multiply(0.42).setY(0.10));

            String stepAnim = lateralSign > 0 ? "walk_right" : "walk_left";
            mechanic.playModelAnimation(golem, state, stepAnim, 0.05, 0.10, 1.8, true);

            double newDist = nextLeapLoc.distance(target.getLocation());

            if (newDist <= 7.2 || step >= maxSteps) {
                state.isExecutingZigZagPursuit = false;

                if (mechanic.random.nextInt(100) < 85) {
                    int roll = mechanic.random.nextInt(100);
                    String surpriseAttack = roll < 35 ? "attack_slash_left" : (roll < 65 ? "attack_sweep_right" : (roll < 85 ? "skill_shield_charge" : "attack_thrust_fling"));
                    mechanic.triggerSurpriseAttack(golem, state, surpriseAttack, target);
                } else {
                    state.currentBehavior = Behavior.ADVANCE;
                    state.attackCooldown = 8;
                }

                cancel();
            }
        }
    }

    /**
     * Executes a sudden, buffered surprise attack right out of the zig-zag shadow dash.
     */
    private void triggerSurpriseAttack(IronGolem golem, BossState state, String attackAnim, Player target) {
        state.currentBehavior = Behavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = true;

        Location golemLoc = golem.getLocation();
        Vector dir = target.getLocation().toVector().subtract(golemLoc.toVector()).setY(0).normalize();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        golem.setRotation(targetYaw, 0f);

        // Surprise burst velocity towards target
        golem.setVelocity(dir.multiply(0.48).setY(0.06));
        golem.getWorld().playSound(golemLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.8f, 0.7f);

        executeSingleAttackPhase(golem, state, attackAnim, 0.08);
    }

    /**
     * Ultra Long-Range Evasive Shadow-Hop:
     * When player gets too close or attacks the boss, boss rapidly disengages 7.5 - 11.5 blocks away!
     */
    private void performEvasiveShadowHop(IronGolem golem, BossState state, Location threatLoc) {
        state.dashCooldown = 85 + random.nextInt(35); // 4.2s - 6.0s cooldown
        state.attackCooldown = 12; // Snappy stance reset
        Location currentLoc = golem.getLocation();

        // 1. Vector away from threat with slight lateral flare
        Vector dirAway = currentLoc.toVector().subtract(threatLoc.toVector()).setY(0);
        if (dirAway.lengthSquared() < 0.01) {
            dirAway = currentLoc.getDirection().multiply(-1).setY(0);
        }
        dirAway.normalize();

        // Add slight lateral flare (-30 deg to +30 deg)
        double angleRad = Math.toRadians((random.nextDouble() - 0.5) * 60.0);
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double newX = dirAway.getX() * cos - dirAway.getZ() * sin;
        double newZ = dirAway.getX() * sin + dirAway.getZ() * cos;
        dirAway = new Vector(newX, 0, newZ).normalize();

        // Super extended leap distance: 7.5 - 11.5 blocks!
        double hopDist = 7.5 + (random.nextDouble() * 4.0);
        Location targetLoc = currentLoc.clone().add(dirAway.clone().multiply(hopDist));

        // Adjust for floor / wall safety
        targetLoc = adjustToTerrainSurface(targetLoc, 0.0);

        // If target block is inside a solid wall, fallback to 5 blocks backward
        if (targetLoc.getBlock().getType().isSolid()) {
            targetLoc = adjustToTerrainSurface(currentLoc.clone().add(currentLoc.getDirection().multiply(-5.0)), 0.0);
        }

        // 2. Start Shadow Burst Effects
        Location startEffectLoc = currentLoc.clone().add(0, 1.8, 0);
        currentLoc.getWorld().spawnParticle(Particle.PORTAL, startEffectLoc, 50, 0.8, 1.2, 0.8, 0.6);
        currentLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, startEffectLoc, 4, 0.5, 0.2, 0.5, 0);
        currentLoc.getWorld().spawnParticle(Particle.DUST, startEffectLoc, 50, 0.6, 0.9, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(70, 160, 220), 2.2f));
        currentLoc.getWorld().playSound(currentLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.6f, 1.4f);
        currentLoc.getWorld().playSound(currentLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.4f, 1.2f);

        // 3. Teleport & apply disengage velocity
        targetLoc.setYaw(currentLoc.getYaw());
        targetLoc.setPitch(currentLoc.getPitch());
        golem.teleport(targetLoc);
        golem.setVelocity(dirAway.multiply(0.40).setY(0.14));

        // 4. Landing Shadow Mist Effects
        Location endEffectLoc = targetLoc.clone().add(0, 1.2, 0);
        targetLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, endEffectLoc, 20, 0.6, 0.6, 0.6, 0.04);
        targetLoc.getWorld().spawnParticle(Particle.DUST, endEffectLoc, 45, 0.6, 0.7, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(150, 220, 255), 2.0f));

        playModelAnimation(golem, state, "walk_backward", 0.06, 0.12, 1.6, true);
    }

    /**
     * Executes a fast, deceptive phantom sidestep or backstep to reposition and catch players off guard.
     */
    private void performAgileDash(IronGolem golem, BossState state, float targetYaw, double distXZ) {
        state.dashCooldown = 110 + random.nextInt(80); // 5.5s - 9.5s cooldown
        double radFacing = Math.toRadians(targetYaw);
        Vector forwardVec = new Vector(-Math.sin(radFacing), 0, Math.cos(radFacing));
        Vector rightVec = new Vector(Math.cos(radFacing), 0, Math.sin(radFacing));

        Vector dashDir;
        String dashAnim;
        if (distXZ < 3.5) {
            // Spring backwards
            dashDir = forwardVec.clone().multiply(-1.10);
            dashAnim = "walk_backward";
        } else {
            // Rapid lateral flank dash
            boolean dashLeft = random.nextBoolean();
            dashDir = rightVec.clone().multiply(dashLeft ? -1.15 : 1.15);
            dashAnim = dashLeft ? "walk_left" : "walk_right";
        }

        golem.setVelocity(dashDir.setY(0.10));
        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 1.6f);
        golem.getWorld().playSound(golem.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.4f, 1.2f);
        
        // Shadow trail particles
        Location dLoc = golem.getLocation().add(0, 1.5, 0);
        golem.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dLoc, 3, 0.5, 0.2, 0.5, 0);
        golem.getWorld().spawnParticle(Particle.DUST, dLoc, 30, 0.6, 0.8, 0.6, 0.0, new Particle.DustOptions(Color.fromRGB(80, 150, 200), 2.0f));

        playModelAnimation(golem, state, dashAnim, 0.08, 0.12, 1.5, true);
    }

    private void triggerSummonSkill(IronGolem golem, BossState state) {
        state.currentBehavior = Behavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = false;
        state.summonSkillCooldown = 600 + random.nextInt(200); // ~30s cooldown for signature ultimate
        executeSingleAttackPhase(golem, state, "skill_charge_summon", 0.30);
    }

    private void triggerShieldChargeSkill(IronGolem golem, BossState state) {
        state.currentBehavior = Behavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.isMovingAttack = true;
        state.shieldChargeCooldown = 280 + random.nextInt(120); // ~14s - 20s cooldown
        executeSingleAttackPhase(golem, state, "skill_shield_charge", 0.18);
    }

    private void triggerAttack(IronGolem golem, BossState state, double distXZ, double targetHeightDiff) {
        state.currentBehavior = Behavior.ATTACKING;
        state.queuedComboAttack = "";
        state.impaledTargetUUID = null;
        state.flingExecuted = false;

        // Dynamic Moving Attack: Always step-in if target is beyond close hugging distance
        state.isMovingAttack = distXZ > 2.2 || random.nextInt(100) < 65;

        // If distance is far (5.5m - 8.5m), give boss a sudden burst step forward to catch player off guard
        if (distXZ > 5.5) {
            Vector burst = golem.getLocation().getDirection().multiply(0.40).setY(0.05);
            golem.setVelocity(burst);
        }

        // Intelligent Attack Selection with Relentless Combos
        String chosenAttack;
        boolean isElevated = targetHeightDiff > 1.2;

        if (isElevated) {
            chosenAttack = random.nextBoolean() ? "attack_slash_left" : "attack_sweep_right";
        } else if (distXZ >= 4.5 && state.shieldChargeCooldown <= 0 && random.nextInt(100) < 30) {
            chosenAttack = "skill_shield_charge";
            state.shieldChargeCooldown = 280 + random.nextInt(120);
        } else if (distXZ >= 3.5 && distXZ <= 8.5 && random.nextInt(100) < 20) {
            chosenAttack = "attack_thrust_fling";
            state.isMovingAttack = false;
        } else if (distXZ > 4.5) {
            if (random.nextInt(100) < 25) {
                chosenAttack = "attack_slash_straight";
            } else {
                chosenAttack = random.nextBoolean() ? "attack_sweep_right" : "attack_slash_left";
                state.queuedComboAttack = chosenAttack.equals("attack_slash_left") ? "attack_sweep_right" : "attack_slash_left";
            }
        } else {
            // Close-range: high combo rate (84%), low slam rate (8%)
            int roll = random.nextInt(100);
            if (roll < 42) {
                chosenAttack = "attack_slash_left";
                state.queuedComboAttack = "attack_sweep_right";
            } else if (roll < 84) {
                chosenAttack = "attack_sweep_right";
                state.queuedComboAttack = "attack_slash_left";
            } else if (roll < 92) {
                chosenAttack = "attack_slash_straight"; // ground slam
            } else {
                chosenAttack = "attack_thrust_fling";
            }
        }

        executeSingleAttackPhase(golem, state, chosenAttack, 0.15);
    }

    private void executeSingleAttackPhase(IronGolem golem, BossState state, String attackAnim, double lerpIn) {
        state.currentAttack = attackAnim;
        state.attackTicks = 0;
        state.attackHitDone = false;
        state.impaledTargetUUID = null;
        state.flingExecuted = false;
        state.hitVictimsThisAttack.clear();
        state.multiHitTickMap.clear();

        double animSpeed = 1.05;
        double animLerpOut = 0.18;

        switch (attackAnim) {
            case "skill_charge_summon" -> {
                state.attackTotalTicks = 216; // 10.8s - Full 10.375s animation + dedicated settling buffer for sword sheathing!
                state.attackHitTick = 68;    // 3.4s - Transition from chanting to skyward laser barrage
                animSpeed = 1.00;            // True 1.00x native playback speed so frames align with tick logic
                animLerpOut = 0.35;
            }
            case "skill_shield_charge" -> {
                state.attackTotalTicks = 54; // 2.625s (53 ticks) - Full Shield Bulldozer Rush & Shockwave Plant Finish
                state.attackHitTick = 15;   // 0.75s start of high-speed bull rush
                animSpeed = 1.00;
                animLerpOut = 0.25;
            }
            case "attack_thrust_fling" -> {
                state.attackTotalTicks = 67; // 3.33s
                state.attackHitTick = 15;   // 0.75s primary impact
            }
            case "attack_slash_straight" -> {
                state.attackTotalTicks = 34; // 1.70s heavy slam follow-through
                state.attackHitTick = 15;
            }
            case "attack_sweep_right" -> {
                state.attackTotalTicks = 30; // 1.50s
                state.attackHitTick = 13;
            }
            default -> { // "attack_slash_left"
                state.attackTotalTicks = 27; // 1.35s
                state.attackHitTick = 12;
            }
        }

        playModelAnimation(golem, state, attackAnim, lerpIn, animLerpOut, animSpeed, true);

        if (attackAnim.equals("skill_charge_summon")) {
            golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.8f);
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.8f, 0.6f);
            golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.6f, 0.5f);
        } else if (attackAnim.equals("skill_shield_charge")) {
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.8f, 1.3f);
            golem.getWorld().playSound(golem.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 0.8f);
            golem.getWorld().playSound(golem.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.5f);
        } else if (attackAnim.equals("attack_thrust_fling")) {
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.4f);
            golem.getWorld().playSound(golem.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 0.7f);
        } else if (attackAnim.equals("attack_slash_straight")) {
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.4f, 0.6f);
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
        } else {
            golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.75f);
        }
    }

    private void handleAttackExecution(IronGolem golem, BossState state, Player target, float targetYaw, float targetPitch, double distXZ) {
        state.attackTicks++;

        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();

        // Keep head tracking target during attack
        float rawDesiredLocalYaw = normalizeAngle(targetYaw - currentYaw);
        float desiredLocalYaw = Math.max(-75f, Math.min(75f, rawDesiredLocalYaw)); 
        float desiredPitch = Math.max(-60f, Math.min(60f, targetPitch));           
        state.headYawLocal = lerpAngle(state.headYawLocal, desiredLocalYaw, 0.35f);
        state.headPitch = lerpAngle(state.headPitch, desiredPitch, 0.25f);

        // Always update modeled entity rotations so client packet stream remains active
        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
            modeledEntity.setYBodyRot(currentYaw);
        }

        // Handle Specialized Signature Skill: skill_charge_summon
        if (state.currentAttack.equals("skill_charge_summon")) {
            handleSummonSpellExecution(golem, state, target, targetYaw, targetPitch);
            return;
        }

        // Handle AOE Bulldozer Shield Rush: skill_shield_charge
        if (state.currentAttack.equals("skill_shield_charge")) {
            handleShieldChargeExecution(golem, state, target, targetYaw, targetPitch, distXZ);
            return;
        }

        if (state.currentAttack.equals("attack_thrust_fling")) {
            handleThrustFlingExecution(golem, state, target, targetYaw, targetPitch, distXZ);
            return;
        }

        // 1. Regular Attacks: Aggressive Tracking & Dynamic Step-in
        if (state.attackTicks < state.attackHitTick) {
            float yawDiff = normalizeAngle(targetYaw - currentYaw);
            // High tracking turn speed (up to 14°/tick) so circling players cannot escape
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 14.0f);
            newYaw = normalizeAngle(newYaw);
            golem.setRotation(newYaw, targetPitch);

            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
            }

            // Dynamic Step-in Lunge right before the blade drops
            if (distXZ > 2.0) {
                double rad = Math.toRadians(newYaw);
                double stepIntensity = (state.attackTicks >= state.attackHitTick - 4) ? 0.32 : 0.16;
                Vector moveVec = new Vector(-Math.sin(rad) * stepIntensity, 0, Math.cos(rad) * stepIntensity);
                Vector vel = golem.getVelocity();
                vel.setX(moveVec.getX());
                vel.setZ(moveVec.getZ());
                apply3BlockStepAssist(golem, moveVec, vel);
                golem.setVelocity(vel);
            }

            // Downward slam leap forward
            if (state.currentAttack.equals("attack_slash_straight") && state.attackTicks == state.attackHitTick - 3) {
                double rad = Math.toRadians(newYaw);
                Vector lunge = new Vector(-Math.sin(rad) * 0.52, 0.14, Math.cos(rad) * 0.52);
                Vector vel = golem.getVelocity().add(lunge);
                apply3BlockStepAssist(golem, lunge, vel);
                golem.setVelocity(vel);
            }
        }

        // 2. ACTIVE MULTI-TICK STRIKE WINDOW (Ticks HitTick - 1 to HitTick + 4)
        if (state.currentAttack.equals("attack_slash_straight")) {
            if (state.attackTicks >= state.attackHitTick && !state.attackHitDone) {
                state.attackHitDone = true;
                executeGroundSlamAOE(golem);
            }
        } else {
            // For Left Slash & Right Sweep: Active lingering swing hitbox across 5 ticks
            int startStrikeTick = Math.max(1, state.attackHitTick - 1);
            int endStrikeTick = state.attackHitTick + 4;
            if (state.attackTicks >= startStrikeTick && state.attackTicks <= endStrikeTick) {
                executeMultiTickSlashStrike(golem, state, target);
            }
        }

        // 3. COMBO CHAINING OR RECOVERY
        if (state.attackTicks >= state.attackTotalTicks) {
            if (state.queuedComboAttack != null && !state.queuedComboAttack.isEmpty()) {
                String nextCombo = state.queuedComboAttack;
                state.queuedComboAttack = "";
                executeSingleAttackPhase(golem, state, nextCombo, 0.10);
                return;
            }

            // End of combo/attack -> smoothly transition back to idle
            state.currentBehavior = Behavior.IDLE_STARE;
            state.behaviorTimer = 12 + random.nextInt(12);
            state.attackCooldown = 22 + random.nextInt(16);
            state.currentAttack = "";
            state.isMovingAttack = false;
            playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    /**
     * Complete Execution of "skill_shield_charge" (AOE Bulldozer Shield Rush):
     * - Phase 1 (0 -> 14 ticks / 0.0s -> 0.70s):
     *   Bracing & Locking stance. Boss lowers shield, charges cyan kinetic energy, roots slightly while acquiring trajectory.
     * - Phase 2 (15 -> 40 ticks / 0.75s -> 2.0s):
     *   High-Speed Bulldozer Rush (Ủi khiên càn quét tốc độ cao)!
     *   * Ở GIỮA VÙNG KHIÊN (lateral <= 1.4m):
     *     - Bị đẩy cuốn theo liên tục cùng với boss.
     *     - Nhận sát thương liên tục đa nhịp (mỗi 4 ticks: 8.5 -> 15.0 damage, càng sát tâm càng đau).
     *     - Tia lửa ma sát bùng cháy dữ dội, âm thanh nghiền kim loại liên tục.
     *   * Ở GẦN RÌA VÙNG KHIÊN (1.4m < lateral <= 3.8m):
     *     - Bị va quẹt nhẹ hất văng sang sườn và thoát khỏi đường ủi.
     *     - Nhận sát thương thấp hơn (6.0 -> 12.0 damage một lần duy nhất).
     * - Phase 3 (41 -> 53 ticks / 2.05s -> 2.625s):
     *   Shield Plant Brake & Ground Shockwave Slam Finish.
     *   Boss slams shield into the floor to halt momentum, triggering a 4.5m circular seismic burst (16.0 AOE damage).
     * - Phase 4 (54 ticks):
     *   Smooth transition back to idle.
     */
    private void handleShieldChargeExecution(IronGolem golem, BossState state, Player target, float targetYaw, float targetPitch, double distXZ) {
        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();

        // Phase 1: Bracing Wind-up (0 -> 14 ticks)
        if (state.attackTicks <= 14) {
            float yawDiff = normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 8.0f);
            newYaw = normalizeAngle(newYaw);
            golem.setRotation(newYaw, state.headPitch);

            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
            }

            // Cyan charge aura & kinetic energy buildup around shield
            if (state.attackTicks % 2 == 0) {
                Location shieldFront = golemLoc.clone().add(golemLoc.getDirection().multiply(1.8)).add(0, 1.8, 0);
                golem.getWorld().spawnParticle(Particle.DUST, shieldFront, 12, 0.4, 0.6, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(60, 200, 255), 1.8f));
                golem.getWorld().spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 1, 0.2, 0.2, 0.2, 0);
            }
        }

        // Phase 2: High-Speed Bulldozer Rush (15 -> 40 ticks)
        if (state.attackTicks > 14 && state.attackTicks <= 40) {
            // Steering tracking toward target (up to 4.5°/tick)
            float yawDiff = normalizeAngle(targetYaw - currentYaw);
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 4.5f);
            newYaw = normalizeAngle(newYaw);
            golem.setRotation(newYaw, 0f);

            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
            if (modeledEntity != null) {
                modeledEntity.setYBodyRot(newYaw);
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
            }

            // High forward rush velocity
            double rad = Math.toRadians(newYaw);
            Vector forwardDir = new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
            Vector rightDir = new Vector(Math.cos(rad), 0, Math.sin(rad)).normalize();

            Vector rushVec = forwardDir.clone().multiply(0.64);
            Vector vel = golem.getVelocity();
            vel.setX(rushVec.getX());
            vel.setZ(rushVec.getZ());
            apply3BlockStepAssist(golem, rushVec, vel);
            golem.setVelocity(vel);

            // Ground rumble sound & heavy shield trail particles
            Location shieldFront = golemLoc.clone().add(forwardDir.clone().multiply(2.2)).add(0, 1.6, 0);
            golem.getWorld().spawnParticle(Particle.SWEEP_ATTACK, shieldFront, 2, 0.6, 0.3, 0.6, 0);
            golem.getWorld().spawnParticle(Particle.DUST, shieldFront, 15, 0.5, 0.7, 0.5, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.2f));
            golem.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, golemLoc.clone().add(0, 0.2, 0), 4, 0.4, 0.1, 0.4, 0.02);

            if (state.attackTicks % 4 == 0) {
                golem.getWorld().playSound(golemLoc, Sound.ENTITY_IRON_GOLEM_STEP, 1.6f, 0.7f);
                golem.getWorld().playSound(golemLoc, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 1.2f);
            }

            // Continuous Hitbox Evaluation: Center Zone (Pinned/Dragged + Multi-Hit) vs Outer Rim (Glancing Deflect)
            for (Player victim : golem.getWorld().getPlayers()) {
                if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;
                if (victim.getWorld() != golem.getWorld()) continue;

                Location vLoc = victim.getLocation();
                double heightDiff = Math.abs(vLoc.getY() - golemLoc.getY());
                if (heightDiff > 3.5) continue;

                Vector toVictim = vLoc.toVector().subtract(golemLoc.toVector());
                double fwdProj = toVictim.dot(forwardDir);
                double lateralProj = Math.abs(toVictim.dot(rightDir));

                // Check if within the forward bulldozer path (0.5m -> 4.5m forward, up to 3.8m half-width)
                if (fwdProj >= 0.5 && fwdProj <= 4.5 && lateralProj <= 3.8) {
                    // ========================================================
                    // ZONE 1: CENTER BULLDOZER ZONE (lateralProj <= 1.4m)
                    // Pinned directly in front of the shield -> Dragged along + Multi-Hit continuous damage!
                    // ========================================================
                    if (lateralProj <= 1.4) {
                        // Drag player forward along with the shield
                        Vector dragVelocity = forwardDir.clone().multiply(0.70).setY(0.08);
                        victim.setVelocity(dragVelocity);
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 2, false, false, false));

                        // Continuous multi-hit every 4 ticks (0.20s interval)
                        int lastHitTick = state.multiHitTickMap.getOrDefault(victim.getUniqueId(), -99);
                        if (state.attackTicks - lastHitTick >= 4) {
                            state.multiHitTickMap.put(victim.getUniqueId(), state.attackTicks);

                            // Sát thương tỷ lệ nghịch với khoảng cách đến tâm (càng sát tâm càng đau)
                            double centerFactor = 1.0 - (lateralProj / 1.4); // 0.0 (rìa tâm) -> 1.0 (ngay chính giữa)
                            double pulseDamage = 8.5 + (centerFactor * 6.5); // 8.5 -> 15.0 damage mỗi nhịp 4 ticks!

                            applyCombatDamage(victim, pulseDamage, golem);

                            // Metal crush friction & spark visuals
                            victim.playSound(vLoc, Sound.ITEM_SHIELD_BLOCK, 1.6f, 0.7f);
                            victim.playSound(vLoc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.3f, 0.9f);
                            victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 16, 0.3, 0.4, 0.3, 0.2);
                            victim.getWorld().spawnParticle(Particle.DUST, vLoc.clone().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.fromRGB(255, 100, 50), 1.8f));
                        }
                    }
                    // ========================================================
                    // ZONE 2: OUTER RIM DEFLECTION ZONE (1.4m < lateralProj <= 3.8m)
                    // Glancing contact -> Lower damage + pushed slightly outward to sides, breaking free!
                    // ========================================================
                    else {
                        // Only hits once during the rush to avoid unwanted multi-drag on the flanks
                        if (state.hitVictimsThisAttack.add(victim.getUniqueId())) {
                            double rimFactor = 1.0 - ((lateralProj - 1.4) / 2.4); // 1.0 (gần tâm hơn) -> 0.0 (xa nhất)
                            double rimDamage = 6.0 + (rimFactor * 6.0); // 6.0 -> 12.0 damage (thấp hơn nhiều)

                            applyCombatDamage(victim, rimDamage, golem);

                            // Đẩy lùi nhẹ dạt ra hai bên sườn
                            double sideSign = Math.signum(toVictim.dot(rightDir));
                            if (sideSign == 0) sideSign = 1;
                            Vector sideDeflect = rightDir.clone().multiply(sideSign * 0.45).add(forwardDir.clone().multiply(0.20)).setY(0.26);
                            victim.setVelocity(victim.getVelocity().add(sideDeflect));
                            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, false));

                            victim.playSound(vLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.2f, 1.3f);
                            victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 1.0f);
                            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, vLoc.clone().add(0, 1.0, 0), 2, 0.3, 0.2, 0.3, 0);
                        }
                    }
                }
            }
        }

        // Phase 3: Shield Plant Brake & Ground Shockwave Finish (41 -> 53 ticks)
        if (state.attackTicks > 40 && state.attackTicks <= 53) {
            Vector vel = golem.getVelocity();
            vel.setX(vel.getX() * 0.4);
            vel.setZ(vel.getZ() * 0.4);
            golem.setVelocity(vel);

            if (state.attackTicks == 42) {
                Location slamCenter = golemLoc.clone().add(golemLoc.getDirection().multiply(2.0));
                slamCenter = adjustToTerrainSurface(slamCenter, 0.0);

                golem.getWorld().playSound(slamCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.8f, 0.75f);
                golem.getWorld().playSound(slamCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
                golem.getWorld().playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.6f, 0.6f);

                slamCenter.getWorld().spawnParticle(Particle.EXPLOSION, slamCenter.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
                slamCenter.getWorld().spawnParticle(Particle.FLASH, slamCenter.clone().add(0, 0.4, 0), 1, 0.1, 0.1, 0.1, 0.0, Color.WHITE);
                slamCenter.getWorld().spawnParticle(Particle.DUST, slamCenter.clone().add(0, 0.2, 0), 40, 1.2, 0.2, 1.2, 0.0, new Particle.DustOptions(Color.fromRGB(90, 210, 255), 2.2f));

                // Finishing 4.5m AOE shockwave burst
                for (Player victim : golem.getWorld().getPlayers()) {
                    if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;
                    Location vLoc = victim.getLocation();
                    if (vLoc.distanceSquared(slamCenter) <= 4.5 * 4.5) {
                        applyCombatDamage(victim, 16.0, golem);
                        Vector outward = vLoc.toVector().subtract(slamCenter.toVector()).normalize().multiply(0.60).setY(0.48);
                        victim.setVelocity(victim.getVelocity().add(outward));
                        victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 0.9f);
                    }
                }
            }
        }

        // Phase 4: Recovery (Ticks >= 54)
        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = Behavior.IDLE_STARE;
            state.behaviorTimer = 16 + random.nextInt(12);
            state.attackCooldown = 28 + random.nextInt(16);
            state.currentAttack = "";
            state.impaledTargetUUID = null;
            state.flingExecuted = false;
            playModelAnimation(golem, state, "idle", 0.25, 0.25, 1.0, true);
        }
    }

    /**
     * Active Multi-Tick Sweeping Slash Strike with Crescent Shockwave & Wide 150° Hit Arc:
     * - Lingering hitbox window so rolling too early or too late gets punished.
     * - Balanced impact stagger knockback (0.48 multiplier).
     * - Launches glowing Crescent Blade Wave (Kiếm khí) traveling forward up to 16.8m,
     *   dynamically clamped to terrain elevation with wide crescent geometry and multi-point collision!
     */
    private void executeMultiTickSlashStrike(IronGolem golem, BossState state, Player target) {
        Location bossLoc = golem.getLocation();
        boolean isPrimaryHitTick = (state.attackTicks == state.attackHitTick);

        if (isPrimaryHitTick) {
            state.attackHitDone = true;
            Location strikeCenter = bossLoc.clone().add(bossLoc.getDirection().multiply(3.8)).add(0, 1.8, 0);

            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.6f, 0.8f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ITEM_MACE_SMASH_AIR, 1.3f, 0.9f);
            bossLoc.getWorld().playSound(strikeCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.7f);

            // Forward lunge boost during blade impact
            Vector forwardBurst = bossLoc.getDirection().multiply(0.24).setY(0);
            golem.setVelocity(golem.getVelocity().add(forwardBurst));

            // Crescent Shockwave Particles
            if (state.currentAttack.equals("attack_slash_left")) {
                bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, strikeCenter, 6, 0.8, 0.4, 0.8, 0);
                bossLoc.getWorld().spawnParticle(Particle.CRIT, strikeCenter, 30, 1.0, 0.6, 1.0, 0.15);
            } else {
                bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, strikeCenter, 6, 0.9, 0.4, 0.9, 0);
                bossLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, strikeCenter, 25, 1.2, 0.4, 1.2, 0.03);
            }

            // Projectile Crescent Blade Wave (Kiếm khí) traveling forward up to ~16.8m!
            Location waveStart = bossLoc.clone().add(bossLoc.getDirection().clone().setY(0).normalize().multiply(2.2));
            waveStart.setY(bossLoc.getY() + 0.6);
            spawnCrescentBladeWave(golem, waveStart, bossLoc.getDirection(), state.currentAttack.equals("attack_sweep_right"));
        }

        // Lingering multi-tick hitbox check (Radius 7.6m, Wide 150° forward cone)
        double hitRadiusSq = 7.6 * 7.6;
        double damageAmount = state.currentAttack.equals("attack_sweep_right") ? 20.0 : 17.0;

        for (Player victim : bossLoc.getWorld().getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;
            if (state.hitVictimsThisAttack.contains(victim.getUniqueId())) continue;

            Location vLoc = victim.getLocation();
            if (vLoc.distanceSquared(bossLoc) <= hitRadiusSq) {
                Vector toVictim = vLoc.toVector().subtract(bossLoc.toVector()).normalize();
                double dot = bossLoc.getDirection().dot(toVictim);

                // Generous 150° forward sweeping cone (dot > -0.25)
                if (dot > -0.25) {
                    state.hitVictimsThisAttack.add(victim.getUniqueId());
                    applyCombatDamage(victim, damageAmount, golem);

                    // Balanced, crisp Souls-like stagger knockback
                    Vector kb = toVictim.clone().multiply(0.48).setY(0.24);
                    victim.setVelocity(victim.getVelocity().add(kb));
                    victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.1f, 0.9f);
                }
            }
        }
    }

    /**
     * Spawns a high-speed crescent blade energy wave (Kiếm khí) traveling forward up to ~16.8m:
     * - Wide crescent-shaped particle arc (3.6m width, 13 distinct sample points).
     * - Full Crescent Arc Hitbox: checks collision along all points of the crescent curve so no one escapes!
     * - Clamps dynamically to terrain height at EVERY point so it never buries underground.
     * - Multi-layered vertical height profile for sharp visual prominence.
     */
    private void spawnCrescentBladeWave(IronGolem golem, Location origin, Vector direction, boolean isSoulWave) {
        new CrescentBladeWaveTask(this, golem, origin, direction, isSoulWave).runTaskTimer(plugin, 1L, 1L);
    }

    private static class CrescentBladeWaveTask extends BukkitRunnable {
        private final LunarWardenMechanic mechanic;
        private final IronGolem golem;
        private final Location current;
        private final Vector dir;
        private final Vector cross;
        private final boolean isSoulWave;
        private final Set<UUID> waveHitTargets = new HashSet<>();
        private int step = 0;

        public CrescentBladeWaveTask(LunarWardenMechanic mechanic, IronGolem golem, Location origin, Vector direction, boolean isSoulWave) {
            this.mechanic = mechanic;
            this.golem = golem;
            this.current = origin.clone();
            this.dir = direction.clone().setY(0).normalize();
            this.cross = new Vector(-this.dir.getZ(), 0, this.dir.getX()).normalize();
            this.isSoulWave = isSoulWave;
        }

        @Override
        public void run() {
            if (golem.isDead() || current.getWorld() == null || step >= 12) {
                cancel();
                return;
            }
            step++;
            current.add(dir.clone().multiply(1.4));

            Location currentCenter = mechanic.adjustToTerrainSurface(current, 0.40);

            double arcWidth = 3.6;
            int arcPoints = 13;
            List<Location> activeArcPoints = new ArrayList<>();

            for (int i = 0; i < arcPoints; i++) {
                double offsetT = -arcWidth + (i * (2.0 * arcWidth / (arcPoints - 1)));
                double curveBack = Math.abs(offsetT) * 0.52;

                Location rawArcPt = currentCenter.clone()
                        .add(cross.clone().multiply(offsetT))
                        .subtract(dir.clone().multiply(curveBack));

                Location arcPt = mechanic.adjustToTerrainSurface(rawArcPt, 0.28);
                Location arcPtUpper = arcPt.clone().add(0, 0.60, 0);
                activeArcPoints.add(arcPt);

                if (isSoulWave) {
                    arcPt.getWorld().spawnParticle(Particle.DUST, arcPt, 1, 0, 0, 0, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f));
                    arcPt.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, arcPt, 1, 0.04, 0.04, 0.04, 0.01);
                    arcPtUpper.getWorld().spawnParticle(Particle.DUST, arcPtUpper, 1, 0, 0, 0, 0.0, new Particle.DustOptions(Color.fromRGB(160, 240, 255), 1.6f));
                } else {
                    arcPt.getWorld().spawnParticle(Particle.DUST, arcPt, 1, 0, 0, 0, 0.0, new Particle.DustOptions(Color.fromRGB(150, 230, 255), 2.0f));
                    arcPt.getWorld().spawnParticle(Particle.SWEEP_ATTACK, arcPt, 1, 0.08, 0.08, 0.08, 0);
                    arcPtUpper.getWorld().spawnParticle(Particle.DUST, arcPtUpper, 1, 0, 0, 0, 0.0, new Particle.DustOptions(Color.WHITE, 1.5f));
                }
            }

            if (step % 3 == 0) {
                currentCenter.getWorld().playSound(currentCenter, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 1.3f);
            }

            for (Player p : currentCenter.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                if (waveHitTargets.contains(p.getUniqueId())) continue;

                Location pLoc = p.getLocation();
                boolean hitArc = false;
                for (Location pt : activeArcPoints) {
                    if (pLoc.distanceSquared(pt) <= 2.2 * 2.2) {
                        hitArc = true;
                        break;
                    }
                }

                if (hitArc) {
                    waveHitTargets.add(p.getUniqueId());
                    mechanic.applyCombatDamage(p, 14.0, golem);
                    p.setVelocity(p.getVelocity().add(dir.clone().multiply(0.40).setY(0.20)));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.1f, 1.0f);
                    p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1.0, 0), 14, 0.3, 0.3, 0.3, 0.12);
                }
            }
        }
    }

    /**
     * Finds the solid ground block surface under loc and returns a Location clamped on top of it.
     */
    public Location adjustToTerrainSurface(Location loc, double heightOffset) {
        if (loc.getWorld() == null) return loc;
        int blockX = loc.getBlockX();
        int blockZ = loc.getBlockZ();
        int startY = Math.min(loc.getWorld().getMaxHeight() - 1, loc.getBlockY() + 4);
        int minY = Math.max(loc.getWorld().getMinHeight(), loc.getBlockY() - 8);

        for (int checkY = startY; checkY >= minY; checkY--) {
            Block b = loc.getWorld().getBlockAt(blockX, checkY, blockZ);
            if (!b.isPassable() && b.getType().isSolid()) {
                return new Location(loc.getWorld(), loc.getX(), checkY + 1.0 + heightOffset, loc.getZ());
            }
        }
        return loc;
    }

    /**
     * Complete Execution of "skill_charge_summon":
     * - Phase 1 (0 -> 68 ticks / 0.0s -> 3.4s):
     *   Chanting stance (sword held forward). Magic circles expand under each player's feet from 0 -> 1.5m, spinning faster.
     * - Phase 2 (69 -> 170 ticks / 3.45s -> 8.5s):
     *   Skyward sword barrage. Rapid celestial targeting markers spawn under running players, followed by intense, sustained light pillars!
     * - Phase 3 (171 -> 216 ticks / 8.55s -> 10.8s):
     *   Full Sword Retraction & Sheathing Stance! Plays the complete sword-lowering, cross-body sheathing, and rest pose.
     * - Phase 4 (216 ticks):
     *   Seamless blend back to idle without clipping or premature pose cutoff!
     */
    private void handleSummonSpellExecution(IronGolem golem, BossState state, Player target, float targetYaw, float targetPitch) {
        Location golemLoc = golem.getLocation();

        // Lock boss firmly in place while casting
        Vector vel = golem.getVelocity();
        vel.setX(0.0);
        vel.setZ(0.0);
        golem.setVelocity(vel);

        // Turn boss smoothly towards target during casting
        float currentYaw = golemLoc.getYaw();
        float yawDiff = normalizeAngle(targetYaw - currentYaw);
        float newYaw = normalizeAngle(currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 2.5f));
        golem.setRotation(newYaw, state.headPitch);

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity != null) {
            modeledEntity.setYBodyRot(newYaw);
            modeledEntity.setYHeadRot(targetYaw);
            modeledEntity.setXHeadRot(targetPitch);
        }

        // ====================================================
        // PHASE 1: CHANTING & GROUND MAGIC CIRCLE EXPANSION (0 -> 68 ticks / 0.0s -> 3.4s)
        // ====================================================
        if (state.attackTicks <= 68) {
            float progress = (float) state.attackTicks / 68.0f; // 0.0 -> 1.0
            double currentRadius = progress * 1.5; // expands from 0.0 to 1.5m
            double rotationAngle = state.attackTicks * (0.12 + (progress * 0.28)); // spins faster as time passes

            // Chanting particle mist around boss sword
            if (state.attackTicks % 2 == 0) {
                Location bossChantLoc = golemLoc.clone().add(golemLoc.getDirection().multiply(2.2)).add(0, 3.2, 0);
                golem.getWorld().spawnParticle(Particle.END_ROD, bossChantLoc, 3, 0.4, 0.4, 0.4, 0.02);
                golem.getWorld().spawnParticle(Particle.DUST, bossChantLoc, 6, 0.3, 0.3, 0.3, 0.0, new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.8f));
            }

            // Ominous ambient pulse sounds
            if (state.attackTicks % 16 == 0) {
                golem.getWorld().playSound(golemLoc, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 0.9f + (progress * 0.8f));
            }

            // Render expanding magic circle under ALL players within 45m (clamped to terrain)
            for (Player p : golem.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                if (p.getLocation().distanceSquared(golemLoc) <= 45.0 * 45.0) {
                    Location pGround = adjustToTerrainSurface(p.getLocation(), 0.0);
                    renderMagicCircle(pGround, currentRadius, rotationAngle, progress, Color.fromRGB(60, 190, 255));
                    
                    if (state.attackTicks == 1) {
                        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.7f, 1.8f);
                    }
                }
            }

            // Climax cue right before barrage starts
            if (state.attackTicks == 67) {
                golem.getWorld().playSound(golemLoc, Sound.ITEM_TRIDENT_THUNDER, 2.0f, 0.8f);
                golem.getWorld().playSound(golemLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.5f);
            }
        }

        // ====================================================
        // PHASE 2: SKYWARD SWORD & CELESTIAL LIGHT PILLAR BARRAGE (69 -> 170 ticks / 3.45s -> 8.5s)
        // ====================================================
        if (state.attackTicks > 68 && state.attackTicks <= 170) {
            // Celestial beacon glow from boss raised sword
            if (state.attackTicks % 3 == 0) {
                Location skySwordTip = golemLoc.clone().add(0, 6.8, 0);
                golem.getWorld().spawnParticle(Particle.END_ROD, skySwordTip, 6, 0.3, 0.6, 0.3, 0.05);
                golem.getWorld().spawnParticle(Particle.FLASH, skySwordTip, 1, 0.1, 0.1, 0.1, 0.0, Color.fromRGB(160, 230, 255));
            }

            // Continuous rapid barrage: spawn target circle every 8 ticks under each active player
            int barrageElapsed = state.attackTicks - 68;
            if (barrageElapsed % 8 == 0) {
                for (Player p : golem.getWorld().getPlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                    if (p.getLocation().distanceSquared(golemLoc) <= 45.0 * 45.0) {
                        spawnTargetedLightStrike(golem, p.getLocation().clone());
                    }
                }
            }
        }

        // ====================================================
        // PHASE 3: SWORD RETRACTION & SHEATHING RECOVERY (171 -> 216 ticks / 8.55s -> 10.8s)
        // ====================================================
        if (state.attackTicks > 170 && state.attackTicks <= 216) {
            // Metallic sheath click sound as sword returns and locks at hip around tick 196 (9.8s)
            if (state.attackTicks == 196) {
                golemLoc.getWorld().playSound(golemLoc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.6f, 1.0f);
                golemLoc.getWorld().playSound(golemLoc, Sound.BLOCK_CHAIN_HIT, 1.4f, 1.2f);
                Location sheathLoc = golemLoc.clone().add(golemLoc.getDirection().multiply(1.2)).add(0, 1.5, 0);
                golemLoc.getWorld().spawnParticle(Particle.DUST, sheathLoc, 20, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(120, 210, 255), 1.5f));
            }
        }

        // ====================================================
        // PHASE 4: FULL COMPLETION & SEAMLESS BLEND TO IDLE (Ticks >= 216)
        // ====================================================
        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = Behavior.IDLE_STARE;
            state.behaviorTimer = 22 + random.nextInt(15);
            state.attackCooldown = 35 + random.nextInt(15);
            state.currentAttack = "";
            state.impaledTargetUUID = null;
            state.flingExecuted = false;
            playModelAnimation(golem, state, "idle", 0.35, 0.35, 1.0, true);
        }
    }

    /**
     * Spawns a Celestial Light Strike at groundLoc:
     * - 12 ticks warning phase: Rapidly pulsing red/cyan targeting magic circle with charging particles.
     * - 10 ticks sustained impact phase: Giant celestial pillar of light beaming down from the sky with shockwaves and damage!
     */
    private void spawnTargetedLightStrike(IronGolem golem, Location groundLoc) {
        new TargetedLightStrikeTask(this, golem, groundLoc).runTaskTimer(plugin, 1L, 1L);
    }

    private static class TargetedLightStrikeTask extends BukkitRunnable {
        private final LunarWardenMechanic mechanic;
        private final IronGolem golem;
        private final Location targetCenter;
        private final int warningDurationTicks = 12;
        private final int beamDurationTicks = 10;
        private final Set<UUID> beamHitPlayers = new HashSet<>();
        private int tick = 0;

        public TargetedLightStrikeTask(LunarWardenMechanic mechanic, IronGolem golem, Location groundLoc) {
            this.mechanic = mechanic;
            this.golem = golem;
            this.targetCenter = mechanic.adjustToTerrainSurface(groundLoc, 0.0);
        }

        @Override
        public void run() {
            if (golem.isDead() || targetCenter.getWorld() == null) {
                cancel();
                return;
            }

            tick++;

            // Phase A: Warning Magic Circle (Ticks 1 -> 12)
            if (tick <= warningDurationTicks) {
                float warnProgress = (float) tick / warningDurationTicks;
                double spin = tick * 0.40;
                mechanic.renderMagicCircle(targetCenter, 1.6, spin, warnProgress, Color.fromRGB(255, 70, 70));
                targetCenter.getWorld().spawnParticle(Particle.DUST, targetCenter.clone().add(0, 0.15, 0), 4, 0.4, 0.05, 0.4, 0.0, new Particle.DustOptions(Color.fromRGB(255, 90, 90), 1.4f));

                if (tick == 1) {
                    targetCenter.getWorld().playSound(targetCenter, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.8f);
                }
            }

            // Phase B: Sustained Light Pillar Impact (Ticks 13 -> 22)
            if (tick > warningDurationTicks && tick <= warningDurationTicks + beamDurationTicks) {
                int beamTick = tick - warningDurationTicks;
                mechanic.renderSustainedPillarOfLight(targetCenter, 1.6, 28, beamTick);

                // Initial crash impact explosion & sound on tick 1 of beam
                if (beamTick == 1) {
                    targetCenter.getWorld().playSound(targetCenter, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.8f, 1.2f);
                    targetCenter.getWorld().playSound(targetCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 1.6f);
                    targetCenter.getWorld().playSound(targetCenter, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.6f);
                    targetCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetCenter.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
                    targetCenter.getWorld().spawnParticle(Particle.FLASH, targetCenter.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
                }

                // Check damage for any player standing inside the beam during active beam ticks
                double impactRadius = 2.2;
                double beamDamage = 22.0;

                for (Player victim : targetCenter.getWorld().getPlayers()) {
                    if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

                    Location vLoc = victim.getLocation();
                    if (vLoc.distanceSquared(targetCenter) <= impactRadius * impactRadius) {
                        if (beamHitPlayers.add(victim.getUniqueId())) {
                            mechanic.applyCombatDamage(victim, beamDamage, golem);
                            Vector launch = new Vector(0, 1.05, 0);
                            victim.setVelocity(victim.getVelocity().add(launch));
                            victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.8f);
                        }
                    }
                }
            }

            if (tick > warningDurationTicks + beamDurationTicks) {
                cancel();
            }
        }
    }

    /**
     * Modular Visual Renderer Placeholder: Magic Circle
     * Easily replaceable when custom texture / Display Entities are ready!
     */
    public void renderMagicCircle(Location center, double radius, double rotationAngle, float progress, Color color) {
        if (center.getWorld() == null || radius < 0.05) return;
        Location base = center.clone();
        base.setY(center.getY() + 0.08); // slightly above ground to prevent z-fighting

        int circlePoints = 18;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);

        // 1. Outer Rotating Rune Ring
        for (int i = 0; i < circlePoints; i++) {
            double angle = rotationAngle + (2.0 * Math.PI * i / circlePoints);
            double px = base.getX() + radius * Math.cos(angle);
            double pz = base.getZ() + radius * Math.sin(angle);
            Location pLoc = new Location(base.getWorld(), px, base.getY(), pz);
            base.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0.0, dust);
        }

        // 2. Inner Counter-Rotating Ring
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

        // 3. Sacred Geometry Cross Points
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

    /**
     * Modular Visual Renderer Placeholder: Sustained Celestial Pillar of Light
     * Renders a persistent vertical laser column with core beams, rotating helix, and ground blast rings!
     */
    public void renderSustainedPillarOfLight(Location targetLoc, double beamRadius, int height, int beamTick) {
        if (targetLoc.getWorld() == null) return;
        Location impact = targetLoc.clone();

        // 1. Concentrated Vertical Core Laser (Sky to Ground)
        for (double y = 0; y <= height; y += 0.9) {
            Location rayPt = impact.clone().add(0, y, 0);
            impact.getWorld().spawnParticle(Particle.END_ROD, rayPt, 1, 0.15, 0.35, 0.15, 0.01);
            impact.getWorld().spawnParticle(Particle.DUST, rayPt, 2, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(Color.fromRGB(180, 240, 255), 2.2f));
            if (beamTick % 2 == 0) {
                impact.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, rayPt, 1, 0.1, 0.3, 0.1, 0.01);
            }
        }

        // 2. Descending Outer Rotating Spiral Stream
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

        // 3. Ground Impact Shockwave Rings & Sparks
        impact.getWorld().spawnParticle(Particle.SWEEP_ATTACK, impact.clone().add(0, 0.3, 0), 1, 0.4, 0.1, 0.4, 0);
        impact.getWorld().spawnParticle(Particle.DUST, impact.clone().add(0, 0.2, 0), 15, 0.8, 0.1, 0.8, 0.0, new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f));
    }

    /**
     * Massive Seismic Ground Slam (AOE Shockwave):
     * - Wide expanding shockwave rings of fractured ground debris & sonic blasts.
     * - Proximity-based damage scaling: Epic epicenter direct hit (38.0 dmg) down to outer rim (12.0 dmg).
     * - Vertical seismic launch (high knockup into the air) + outward shockwave impulse.
     * - Full Creative Mode support: visual hurt reaction, screen shake, and physics knockup.
     */
    private void executeGroundSlamAOE(IronGolem golem) {
        Location bossLoc = golem.getLocation();
        Location slamCenter = bossLoc.clone().add(bossLoc.getDirection().multiply(3.4));
        slamCenter.setY(bossLoc.getY());

        // Find floor block below slam impact
        Block groundBlock = slamCenter.clone().subtract(0, 0.2, 0).getBlock();
        if (groundBlock.isPassable()) {
            groundBlock = slamCenter.clone().subtract(0, 1.0, 0).getBlock();
        }
        BlockData particleBlockData = !groundBlock.isPassable() && groundBlock.getType().isSolid()
                ? groundBlock.getBlockData()
                : Material.STONE.createBlockData();

        // 1. Epic Sound Effects (Multi-layered bass, explosion & metal crush)
        slamCenter.getWorld().playSound(slamCenter, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 2.0f, 0.7f);
        slamCenter.getWorld().playSound(slamCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.8f);
        slamCenter.getWorld().playSound(slamCenter, Sound.BLOCK_ANVIL_LAND, 1.8f, 0.5f);
        slamCenter.getWorld().playSound(slamCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);

        // 2. Center Epic Burst
        slamCenter.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, slamCenter.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0);
        slamCenter.getWorld().spawnParticle(Particle.FLASH, slamCenter.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.0, Color.WHITE);
        slamCenter.getWorld().spawnParticle(Particle.BLOCK, slamCenter.clone().add(0, 0.5, 0), 120, 1.2, 0.8, 1.2, 0.3, particleBlockData);
        slamCenter.getWorld().spawnParticle(Particle.DUST, slamCenter.clone().add(0, 0.5, 0), 60, 1.0, 0.5, 1.0, 0.0, new Particle.DustOptions(Color.GRAY, 2.5f));

        // 3. Expanding Concentric Shockwave Rings (Simulated Ground Shatter)
        double[] waveRadii = {1.8, 3.4, 5.2, 7.2, 8.8};
        for (int rIndex = 0; rIndex < waveRadii.length; rIndex++) {
            final int ringIndex = rIndex;
            final double radius = waveRadii[rIndex];
            final int points = 16 + (rIndex * 10);
            long delayTicks = rIndex * 1L;

            new GroundSlamWaveTask(golem, slamCenter, radius, points, particleBlockData, ringIndex).runTaskLater(plugin, delayTicks);
        }

        // 4. AOE Player Knockup & Proximity-Scaled Damage
        double maxAoeRadius = 9.0;
        double maxDamage = 38.0;
        double minDamage = 12.0;

        for (Player victim : slamCenter.getWorld().getPlayers()) {
            if (victim.getGameMode() == GameMode.SPECTATOR || !victim.isValid() || victim.isDead()) continue;

            Location vLoc = victim.getLocation();
            double dist = vLoc.distance(slamCenter);

            if (dist <= maxAoeRadius) {
                double heightDiff = Math.abs(vLoc.getY() - slamCenter.getY());
                if (heightDiff > 5.0) continue;

                double falloff = 1.0 - (dist / maxAoeRadius);
                double actualDamage = minDamage + (falloff * (maxDamage - minDamage));

                applyCombatDamage(victim, actualDamage, golem);

                double upwardVelocity = 0.55 + (falloff * 0.95);

                Vector outwardDir = vLoc.toVector().subtract(slamCenter.toVector()).setY(0);
                if (outwardDir.lengthSquared() > 0.001) {
                    outwardDir.normalize().multiply(0.35 + (falloff * 0.65));
                } else {
                    outwardDir = new Vector(0, 0, 0);
                }

                outwardDir.setY(upwardVelocity);
                victim.setVelocity(outwardDir);

                victim.playSound(vLoc, Sound.ENTITY_PLAYER_HURT, 1.2f, 0.7f);
                victim.getWorld().spawnParticle(Particle.CRIT, vLoc.clone().add(0, 1.0, 0), 20, 0.3, 0.5, 0.3, 0.2);
            }
        }
    }

    private static class GroundSlamWaveTask extends BukkitRunnable {
        private final IronGolem golem;
        private final Location slamCenter;
        private final double radius;
        private final int points;
        private final BlockData particleBlockData;
        private final int ringIndex;

        public GroundSlamWaveTask(IronGolem golem, Location slamCenter, double radius, int points, BlockData particleBlockData, int ringIndex) {
            this.golem = golem;
            this.slamCenter = slamCenter;
            this.radius = radius;
            this.points = points;
            this.particleBlockData = particleBlockData;
            this.ringIndex = ringIndex;
        }

        @Override
        public void run() {
            if (golem.getWorld() == null) return;
            for (int i = 0; i < points; i++) {
                double angle = 2.0 * Math.PI * i / points;
                double px = slamCenter.getX() + radius * Math.cos(angle);
                double pz = slamCenter.getZ() + radius * Math.sin(angle);
                Location pPoint = new Location(slamCenter.getWorld(), px, slamCenter.getY() + 0.15, pz);

                slamCenter.getWorld().spawnParticle(Particle.BLOCK, pPoint, 4, 0.25, 0.35, 0.25, 0.12, particleBlockData);
                slamCenter.getWorld().spawnParticle(Particle.DUST, pPoint, 2, 0.15, 0.2, 0.15, 0.0, new Particle.DustOptions(Color.SILVER, 1.6f));
                if (ringIndex % 2 == 0) {
                    slamCenter.getWorld().spawnParticle(Particle.SCULK_SOUL, pPoint, 1, 0.1, 0.2, 0.1, 0.03);
                }
            }
        }
    }

    /**
     * Enhanced 3-stage Grab/Impale attack with Smart Adaptive Lunge & Point-Blank/Close-Range Corridor Hitbox:
     * - Stage 1 (0 -> 14 ticks): Adaptive Lunge Speed (Slows down when already close so boss never overshoots).
     * - Stage 2 (10 -> 22 ticks): Active Grab Window with generous Point-Blank detection (catches players right next to boss).
     * - Stage 2b (20 -> 48 ticks): Pinned directly to the 3D SWORD TIP with continuous bleed & soul particles.
     * - Stage 3 (48 -> 67 ticks): Violent fling bowling victim far away with burst damage.
     * Full Creative Mode support enabled.
     */
    private void handleThrustFlingExecution(IronGolem golem, BossState state, Player target, float targetYaw, float targetPitch, double distXZ) {
        Location golemLoc = golem.getLocation();
        float currentYaw = golemLoc.getYaw();

        // ----------------------------------------------------
        // STAGE 1: Adaptive Lunge & Magnetic Tracking (0 -> 14 ticks)
        // ----------------------------------------------------
        if (state.attackTicks < state.attackHitTick) {
            float yawDiff = normalizeAngle(targetYaw - currentYaw);
            // High tracking turn speed
            float newYaw = currentYaw + Math.signum(yawDiff) * Math.min(Math.abs(yawDiff), 15.0f);
            newYaw = normalizeAngle(newYaw);
            golem.setRotation(newYaw, targetPitch);

            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
            if (modeledEntity != null) {
                modeledEntity.setYHeadRot(targetYaw);
                modeledEntity.setXHeadRot(targetPitch);
                modeledEntity.setYBodyRot(newYaw);
            }

            // SMART ADAPTIVE LUNGE: If player is already within 2.5m, DO NOT overshoot them!
            double rad = Math.toRadians(newYaw);
            double lungeSpeed;
            if (distXZ < 2.0) {
                lungeSpeed = 0.04; // Minimal step forward to keep sword point squarely on player
            } else if (distXZ < 4.0) {
                lungeSpeed = 0.22; // Controlled glide
            } else {
                lungeSpeed = 0.46; // Long-range lunge to close the gap
            }

            Vector lungeVec = new Vector(-Math.sin(rad) * lungeSpeed, 0, Math.cos(rad) * lungeSpeed);
            Vector vel = golem.getVelocity();
            vel.setX(lungeVec.getX());
            vel.setZ(lungeVec.getZ());
            apply3BlockStepAssist(golem, lungeVec, vel);
            golem.setVelocity(vel);

            // Particles during lunge wind-up
            if (state.attackTicks % 2 == 0) {
                Location trail = golemLoc.clone().add(golemLoc.getDirection().multiply(1.5)).add(0, 1.5, 0);
                golem.getWorld().spawnParticle(Particle.SWEEP_ATTACK, trail, 1, 0.2, 0.2, 0.2, 0);
            }
        }

        // ----------------------------------------------------
        // STAGE 2: Multi-Tick Active Grab Window (Ticks 10 -> 22)
        // ----------------------------------------------------
        if (state.attackTicks >= 10 && state.attackTicks <= 22 && state.impaledTargetUUID == null && !state.attackHitDone) {
            Player caughtPlayer = null;
            double closestDist = Double.MAX_VALUE;

            for (Player p : golem.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || !p.isValid() || p.isDead()) continue;
                Location pLoc = p.getLocation();
                double dist = pLoc.distance(golemLoc);

                // Check reach up to 8.8m
                if (dist <= 8.8) {
                    Vector toP = pLoc.toVector().subtract(golemLoc.toVector()).normalize();
                    double dot = golemLoc.getDirection().dot(toP);
                    double heightDiff = Math.abs(pLoc.getY() - golemLoc.getY());

                    if (heightDiff < 5.0) {
                        // POINT-BLANK & CLOSE-RANGE (dist <= 3.2m): Wide generous 240° corridor (dot > -0.50)
                        // LONG-RANGE (dist > 3.2m): Forward 140° corridor (dot > 0.0)
                        boolean hitAngle = (dist <= 3.2) ? (dot > -0.50) : (dot > 0.0);

                        if (hitAngle && dist < closestDist) {
                            closestDist = dist;
                            caughtPlayer = p;
                        }
                    }
                }
            }

            if (caughtPlayer != null) {
                state.attackHitDone = true;
                state.impaledTargetUUID = caughtPlayer.getUniqueId();
                golem.setVelocity(new Vector(0, 0, 0));

                Location strikeEffectPoint = golemLoc.clone().add(golemLoc.getDirection().multiply(3.8)).add(0, 2.2, 0);
                applyCombatDamage(caughtPlayer, 22.0, golem);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.8f, 0.5f);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ITEM_TRIDENT_HIT, 1.6f, 0.5f);
                golem.getWorld().spawnParticle(Particle.CRIT, strikeEffectPoint, 45, 0.5, 0.5, 0.5, 0.25);
                golem.getWorld().spawnParticle(Particle.DUST, strikeEffectPoint, 40, 0.4, 0.4, 0.4, 0.0, new Particle.DustOptions(Color.RED, 2.2f));
            } else if (state.attackTicks == state.attackHitTick && !state.attackHitDone) {
                // Play woosh sound on primary hit tick even if missed
                Location strikeEffectPoint = golemLoc.clone().add(golemLoc.getDirection().multiply(4.0)).add(0, 2.0, 0);
                golem.getWorld().playSound(strikeEffectPoint, Sound.ITEM_TRIDENT_THROW, 1.5f, 0.7f);
            }
        }

        // ----------------------------------------------------
        // STAGE 2b: Pinned DIRECTLY ON SWORD TIP & Bleeding (Ticks 16 -> 47)
        // ----------------------------------------------------
        if (state.attackTicks > state.attackHitTick && state.attackTicks < 48) {
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p && p.isValid() && !p.isDead()) {
                    Location swordBase = null;
                    ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
                    if (me != null) {
                        swordBase = me.getModel("thelunarwarden")
                                .flatMap(m -> m.getBone("sword"))
                                .map(ModelBone::getLocation)
                                .orElse(null);
                    }

                    Location swordTip;
                    if (swordBase != null) {
                        // Offset along sword forward axis right onto the giant blade tip
                        swordTip = swordBase.clone().add(golemLoc.getDirection().multiply(3.2)).add(0, 1.8, 0);
                    } else {
                        double progress = (state.attackTicks - 15) / 33.0;
                        double currentHeight = 3.2 + (progress * 2.8);
                        double forwardDist = 4.6 - (progress * 0.4);
                        swordTip = golemLoc.clone().add(golemLoc.getDirection().multiply(forwardDist)).add(0, currentHeight, 0);
                    }

                    // Place victim directly at the sharp tip
                    Location pinLoc = swordTip.clone().subtract(0, 0.2, 0);
                    p.teleport(pinLoc);
                    p.setVelocity(new Vector(0, 0.0, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10, 4, false, false, false));

                    if (state.attackTicks % 5 == 0) {
                        applyCombatDamage(p, 3.5, golem);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 15, 0.2, 0.3, 0.2, 0.0, new Particle.DustOptions(Color.MAROON, 1.8f));
                        p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.02);
                    }
                } else {
                    state.impaledTargetUUID = null;
                }
            }
        }

        // ----------------------------------------------------
        // STAGE 3: Violent Fling & Massive Burst (Tick 48)
        // ----------------------------------------------------
        if (state.attackTicks >= 48 && !state.flingExecuted) {
            state.flingExecuted = true;
            if (state.impaledTargetUUID != null) {
                Entity victim = Bukkit.getEntity(state.impaledTargetUUID);
                if (victim instanceof Player p && p.isValid() && !p.isDead()) {
                    Location flingLoc = p.getLocation();
                    applyCombatDamage(p, 28.0, golem);

                    Vector flingDir = golemLoc.getDirection().multiply(2.2).setY(1.10);
                    p.setVelocity(flingDir);

                    p.getWorld().playSound(flingLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.2f);
                    p.getWorld().playSound(flingLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.8f, 0.5f);
                    p.getWorld().spawnParticle(Particle.EXPLOSION, flingLoc, 1, 0, 0, 0, 0);
                    p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, flingLoc, 5, 0.5, 0.5, 0.5, 0);
                    p.getWorld().spawnParticle(Particle.DUST, flingLoc, 40, 0.6, 0.6, 0.6, 0.0, new Particle.DustOptions(Color.RED, 2.0f));
                }
                state.impaledTargetUUID = null;
            }
        }

        // ----------------------------------------------------
        // RECOVERY & RESET
        // ----------------------------------------------------
        if (state.attackTicks >= state.attackTotalTicks) {
            state.currentBehavior = Behavior.IDLE_STARE;
            state.behaviorTimer = 18 + random.nextInt(12);
            state.attackCooldown = 35 + random.nextInt(20);
            state.currentAttack = "";
            state.impaledTargetUUID = null;
            state.flingExecuted = false;
            playModelAnimation(golem, state, "idle", 0.20, 0.20, 1.0, true);
        }
    }

    /**
     * Applies combat damage and feedback to a player.
     * In Creative Mode: triggers hurt animation tilt and hurt sound while allowing full physics/displacement.
     * In Survival/Adventure: deals true damage.
     */
    public void applyCombatDamage(Player player, double damage, Entity source) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.playHurtAnimation(0.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
        } else {
            player.damage(damage, source);
        }
    }

    /**
     * Seamless Animation Cross-Fade & State Transition Manager for ModelEngine 4.
     * 
     * 1. Plays targetAnim with specified lerpIn & lerpOut to blend bone transforms seamlessly.
     * 2. Calls stopAnimation on previous animation so it fades out smoothly via lerpOut.
     * 3. Cleans up any finished override animations that are no longer playing to prevent pose freezes.
     */
    public void playModelAnimation(IronGolem golem, BossState state, String targetAnim, double lerpIn, double lerpOut, double speed, boolean force) {
        if (targetAnim == null || targetAnim.isEmpty()) {
            targetAnim = "idle";
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity == null) return;

        ActiveModel model = modeledEntity.getModel("thelunarwarden").orElse(null);
        if (model == null) return;

        AnimationHandler handler = model.getAnimationHandler();
        if (handler == null) return;

        String prevAnim = state.currentPlayingMovementAnim;
        if (targetAnim.equals(prevAnim) && handler.isPlayingAnimation(targetAnim)) {
            return;
        }

        // 1. Play the new animation FIRST with lerpIn so ModelEngine blends bone transforms
        handler.playAnimation(targetAnim, lerpIn, lerpOut, speed, force);

        // 2. Soft-stop the previous animation so it lerps out seamlessly instead of snapping
        if (prevAnim != null && !prevAnim.isEmpty() && !prevAnim.equals(targetAnim)) {
            handler.stopAnimation(prevAnim);
        }

        // 3. Clean up other lingering finished animations
        for (String attack : ATTACK_ANIMATIONS) {
            if (!attack.equals(targetAnim) && !attack.equals(prevAnim) && handler.isPlayingAnimation(attack)) {
                handler.forceStopAnimation(attack);
            }
        }

        state.currentPlayingMovementAnim = targetAnim;
    }

    private float normalizeAngle(float angle) {
        while (angle <= -180.0f) angle += 360.0f;
        while (angle > 180.0f) angle -= 360.0f;
        return angle;
    }

    private double lerp(double start, double end, double factor) {
        return start + (end - start) * factor;
    }

    private float lerpAngle(float start, float end, float pct) {
        float diff = normalizeAngle(end - start);
        return normalizeAngle(start + diff * pct);
    }
}
