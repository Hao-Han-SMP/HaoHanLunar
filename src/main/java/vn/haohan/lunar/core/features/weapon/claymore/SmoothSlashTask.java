package vn.haohan.lunar.core.features.weapon.claymore;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.property.IAnimationProperty;
import com.ticxo.modelengine.api.animation.property.SimpleProperty;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehavior;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorData;
import com.ticxo.modelengine.api.model.bone.behavior.BoneBehaviorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import vn.haohan.lunar.core.features.boss.warden.util.WardenEntityManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Continuous combo slash visual task using ModelEngine.
 * <p>
 * Key features:
 * 1. Model & Entity Reuse: Reuses the existing ArmorStand and ActiveModel throughout combos.
 *    Does not destroy or recreate entities during transitions.
 * 2. Animation Blending (Cross-Fade): Blends animations across swings with lerpIn=0.10s.
 * 3. Zero Re-equip Jitter: Keeps weapon hand hidden for the combo duration.
 *    Restores original sword only when attack sequence finishes.
 * 4. Input Buffering: Opens combo chaining window at tick 5 (0.25s) and buffers inputs during swing windup.
 * 5. Strict Non-Duplicate Guarantee: Ensures neither the SlashType nor the animation repeats back-to-back.
 * 6. Defensive Guard (Block_Sword): Supports dedicated defensive guard stance on right-click.
 */
public class SmoothSlashTask extends BukkitRunnable {

    public enum SlashType {
        DIAGONAL_LEFT,
        DIAGONAL_RIGHT,
        DOWNWARD,
        HORIZONTAL_LEFT,
        HORIZONTAL_RIGHT,
        STABBING_ATTACK,
        BLOCK
    }

    private static final Map<UUID, SmoothSlashTask> ACTIVE_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, String> ANIMATION_CACHE = new ConcurrentHashMap<>();
    private static final ItemStack AIR_ITEM = new ItemStack(Material.AIR);
    private static final int CMD_SLASHING = 6002;

    public static final int COMBO_WINDOW_START_TICK = 5; // Chain window opens at tick 5 (~0.25s)

    private static final double FORWARD_OFFSET_BASE = 0.70;
    private static final double FORWARD_OFFSET_LUNGE = 0.20;
    private static final double HEIGHT_OFFSET_NORMAL = 1.0;
    private static final double HEIGHT_OFFSET_SNEAK = 0.92;

    private static final double MODEL_SCALE = 2.6;
    private static final double ANIM_SPEED = 0.85;

    private final JavaPlugin plugin;
    private final Player player;
    private final ArmorStand dummy;
    private final ModeledEntity modeledEntity;
    private final ActiveModel activeModel;

    private volatile SlashType slashType;
    private volatile IAnimationProperty animProperty;
    private volatile String animationName;
    private volatile double animationTotalLength;

    private final Location cachedLoc = new Location(null, 0, 0, 0);
    private final int savedHeldSlot;
    private final ItemStack savedWeaponItem;

    // Spatial smoothing fields for continuous transitions
    private double currentForwardDist = 0.70;
    private double currentRightDist = 0.0;
    private double currentHeightSub = 1.0;
    private boolean initializedPos = false;

    // Callbacks for damage hit timing and swing start sounds
    private final BiConsumer<SlashType, Entity> hitCallback;
    private final Consumer<SlashType> swingStartCallback;

    // Lifecycle & Input Buffering
    private volatile int tick = 0;
    private volatile int minTicks;
    private volatile int maxTicks;
    private volatile boolean animationEnded = false;
    private volatile int postEndBufferTicks = 0;

    private volatile boolean comboQueued = false;
    private volatile SlashType queuedSlashType = null;
    private volatile Entity queuedTarget = null;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SmoothSlashTask(
            JavaPlugin plugin,
            Player player,
            ItemStack weapon,
            SlashType initialType,
            Entity initialTarget,
            BiConsumer<SlashType, Entity> hitCallback,
            Consumer<SlashType> swingStartCallback
    ) {
        this.plugin = plugin;
        this.player = player;
        this.slashType = initialType;
        this.hitCallback = hitCallback;
        this.swingStartCallback = swingStartCallback;

        this.savedHeldSlot = player.getInventory().getHeldItemSlot();
        if (weapon != null && weapon.getType() != Material.AIR) {
            this.savedWeaponItem = weapon.clone();
        } else {
            this.savedWeaponItem = player.getInventory().getItemInMainHand().clone();
        }

        Location spawnLoc = calculateInitialLocation(player, initialType);

        this.dummy = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, ent -> {
            ent.setVisible(false);
            ent.setGravity(false);
            ent.setMarker(true);
            ent.setBasePlate(false);
            ent.setInvulnerable(true);
            ent.setPersistent(false);
        });

        WardenEntityManager.registerTempEntity(this.dummy);

        ModeledEntity me = null;
        ActiveModel am = null;
        IAnimationProperty prop = null;
        String resolvedAnim = null;
        double animLength = 0.60;

        try {
            me = ModelEngineAPI.createModeledEntity(this.dummy);
            if (me != null) {
                me.setBaseEntityVisible(false);
                me.setModelRotationLocked(true);

                try {
                    am = ModelEngineAPI.createActiveModel("claymore");
                } catch (Throwable ignored) {}
                if (am == null) {
                    try {
                        am = ModelEngineAPI.createActiveModel("lunar_claymore_2handed");
                    } catch (Throwable ignored) {}
                }

                if (am != null) {
                    am.setCanHurt(false);
                    am.setScale(MODEL_SCALE);
                    am.setMainHitbox(false);

                    try {
                        am.setBlockLight(15);
                        am.setSkyLight(15);
                        for (ModelBone bone : am.getBones().values()) {
                            bone.setBlockLight(15);
                            bone.setSkyLight(15);
                        }
                    } catch (Throwable ignored) {}

                    me.addModel(am, true);

                    try {
                        var headProvider = BoneBehaviorTypes.HEAD.getBehaviorProvider();
                        if (headProvider != null) {
                            for (ModelBone bone : am.getBones().values()) {
                                if (bone.isRootBone() && !bone.hasBoneBehavior(BoneBehaviorTypes.HEAD)) {
                                    var headBehavior = headProvider.create(
                                            bone,
                                            (BoneBehaviorType) BoneBehaviorTypes.HEAD,
                                            new BoneBehaviorData(Collections.emptyMap())
                                    );
                                    if (headBehavior instanceof BoneBehavior bb) {
                                        bone.addBoneBehavior(bb);
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    am.setLockPitch(false);
                    am.setLockYaw(false);

                    resolvedAnim = resolveAnimationNameCached(initialType, am);
                    animLength = lookupAnimationDuration(am, resolvedAnim);

                    updateRotation(player.getYaw(), player.getPitch(), me, am, null);

                    double playSpeed = (initialType == SlashType.BLOCK) ? 1.0 : ANIM_SPEED;
                    prop = am.getAnimationHandler().playAnimation(resolvedAnim, 0.05, 0.05, playSpeed, true);
                    if (prop != null) {
                        try {
                            prop.setForceLoopMode(BlueprintAnimation.LoopMode.ONCE);
                        } catch (Throwable ignored) {}

                        if (prop instanceof SimpleProperty sp) {
                            sp.setOnEndTask(() -> animationEnded = true);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (me != null) {
                try {
                    me.destroy();
                } catch (Throwable ignored) {}
            }
            WardenEntityManager.removeTempEntity(this.dummy);
        }

        this.modeledEntity = me;
        this.activeModel = am;
        this.animProperty = prop;
        this.animationName = resolvedAnim;
        this.animationTotalLength = animLength;

        double speedForTicks = (initialType == SlashType.BLOCK) ? 1.0 : ANIM_SPEED;
        int expectedTicks = (int) Math.ceil((animLength / speedForTicks) * 20.0);
        this.minTicks = Math.max(initialType == SlashType.BLOCK ? 8 : 11, expectedTicks);
        this.maxTicks = Math.max(initialType == SlashType.BLOCK ? 15 : 18, this.minTicks + (initialType == SlashType.BLOCK ? 4 : 6));

        // Hide vanilla held item with placeholder to eliminate first-person equip bouncing
        if (savedWeaponItem != null && savedWeaponItem.getType() != Material.AIR) {
            ItemStack slashingItem = savedWeaponItem.clone();
            slashingItem.editMeta(meta -> meta.setCustomModelData(CMD_SLASHING));
            player.getInventory().setItem(savedHeldSlot, slashingItem);
        }

        broadcastEquipmentToTrackers(player, AIR_ITEM);
        try {
            player.sendEquipmentChange(player, EquipmentSlot.HAND, AIR_ITEM);
        } catch (Throwable ignored) {}

        // Schedule first attack hit detection (only for offensive slashes, not block)
        if (initialType != SlashType.BLOCK) {
            scheduleHit(initialType, initialTarget);
        }

        if (swingStartCallback != null) {
            swingStartCallback.accept(initialType);
        }
    }

    public SlashType getSlashType() {
        return slashType;
    }

    public SlashType getQueuedOrCurrentSlashType() {
        if (comboQueued && queuedSlashType != null) {
            return queuedSlashType;
        }
        return slashType;
    }

    public ActiveModel getActiveModel() {
        return activeModel;
    }

    public String getAnimationName() {
        return animationName;
    }

    public boolean isValid() {
        return !isCancelled() && player.isOnline() && !dummy.isDead() && modeledEntity != null && activeModel != null;
    }

    public boolean isBlocking() {
        return isValid() && slashType == SlashType.BLOCK && !animationEnded;
    }

    public static boolean isPlayerBlocking(Player player) {
        if (player == null) return false;
        SmoothSlashTask task = ACTIVE_TASKS.get(player.getUniqueId());
        return task != null && task.isBlocking();
    }

    /**
     * Handles incoming combo input from the player:
     * - If nextType is BLOCK: immediately transitions into blocking stance.
     * - If swing is in wind-up (tick < COMBO_WINDOW_START_TICK): Buffers the input.
     * - If swing has crossed center (tick >= COMBO_WINDOW_START_TICK): Immediately chains to next attack.
     */
    public boolean handleComboInput(SlashType nextType, Entity target) {
        if (!isValid()) return false;

        if (nextType == SlashType.BLOCK) {
            chainTo(nextType, target);
            return true;
        }

        SlashType currentOrQueued = getQueuedOrCurrentSlashType();
        if (nextType == currentOrQueued) {
            nextType = pickAlternativeSlashType(currentOrQueued);
        }

        if (tick < COMBO_WINDOW_START_TICK) {
            // Input Buffering: Record queued attack to automatically chain at tick 5
            this.comboQueued = true;
            this.queuedSlashType = nextType;
            this.queuedTarget = target;
            return true;
        }

        // Cancel recovery frames and chain immediately
        chainTo(nextType, target);
        return true;
    }

    /**
     * Picks an alternative slash type that differs from the current attack both in enum and animation.
     */
    private SlashType pickAlternativeSlashType(SlashType avoid) {
        List<SlashType> candidates = new ArrayList<>();
        for (SlashType type : SlashType.values()) {
            if (type != SlashType.DOWNWARD && type != SlashType.BLOCK && type != avoid) {
                if (activeModel != null && animationName != null) {
                    String anim = resolveAnimationNameCached(type, activeModel);
                    if (anim != null && anim.equalsIgnoreCase(animationName)) {
                        continue;
                    }
                }
                candidates.add(type);
            }
        }
        if (candidates.isEmpty()) {
            for (SlashType type : SlashType.values()) {
                if (type != avoid && type != SlashType.DOWNWARD && type != SlashType.BLOCK) {
                    candidates.add(type);
                }
            }
        }
        if (candidates.isEmpty()) return SlashType.HORIZONTAL_LEFT;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /**
     * Transitions the active model to the next attack in the combo using cross-fade blending.
     * Uses ModelEngine's animation blending (lerpIn=0.10s) without destroying the entity.
     * Guarantees that the visual animation is strictly different from the previous one.
     */
    public void chainTo(SlashType nextType, Entity target) {
        if (!isValid()) return;

        String resolvedAnim = resolveAnimationNameCached(nextType, activeModel);

        // Strict Guarantee: Ensure the newly chosen offensive animation is visually different from current
        if (nextType != SlashType.BLOCK && resolvedAnim != null && this.animationName != null && resolvedAnim.equalsIgnoreCase(this.animationName)) {
            for (SlashType alt : SlashType.values()) {
                if (alt == SlashType.DOWNWARD || alt == SlashType.BLOCK) continue;
                String altAnim = resolveAnimationNameCached(alt, activeModel);
                if (altAnim != null && !altAnim.equalsIgnoreCase(this.animationName)) {
                    nextType = alt;
                    resolvedAnim = altAnim;
                    break;
                }
            }
        }

        this.slashType = nextType;
        this.comboQueued = false;
        this.queuedSlashType = null;
        this.queuedTarget = null;
        this.tick = 0;
        this.animationEnded = false;
        this.postEndBufferTicks = 0;
        this.animationName = resolvedAnim;

        double animLength = lookupAnimationDuration(activeModel, resolvedAnim);
        this.animationTotalLength = animLength;

        double speedForTicks = (nextType == SlashType.BLOCK) ? 1.0 : ANIM_SPEED;
        int expectedTicks = (int) Math.ceil((animLength / speedForTicks) * 20.0);
        this.minTicks = Math.max(nextType == SlashType.BLOCK ? 8 : 11, expectedTicks);
        this.maxTicks = Math.max(nextType == SlashType.BLOCK ? 15 : 18, this.minTicks + (nextType == SlashType.BLOCK ? 4 : 6));

        try {
            // Blend from current bone positions into new animation in 0.10s (~2 ticks)
            this.animProperty = activeModel.getAnimationHandler().playAnimation(resolvedAnim, 0.10, 0.10, speedForTicks, true);
            if (this.animProperty != null) {
                try {
                    this.animProperty.setForceLoopMode(BlueprintAnimation.LoopMode.ONCE);
                } catch (Throwable ignored) {}

                if (this.animProperty instanceof SimpleProperty sp) {
                    sp.setOnEndTask(() -> animationEnded = true);
                }
            }
        } catch (Throwable ignored) {}

        // Schedule hit detection for offensive attacks (not for block)
        if (nextType != SlashType.BLOCK) {
            scheduleHit(nextType, target);
        }

        if (swingStartCallback != null) {
            swingStartCallback.accept(nextType);
        }
    }

    private void scheduleHit(SlashType type, Entity target) {
        if (hitCallback == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid() && player.isOnline()) {
                hitCallback.accept(type, target);
            }
        }, 3L);
    }

    private static double lookupAnimationDuration(ActiveModel model, String animName) {
        if (model != null && model.getBlueprint() != null && model.getBlueprint().getAnimations() != null) {
            for (Map.Entry<String, BlueprintAnimation> entry : model.getBlueprint().getAnimations().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(animName)) {
                    double len = entry.getValue().getLength();
                    if (len > 0) return len;
                }
            }
        }
        return 0.60;
    }

    private static Location calculateInitialLocation(Player player, SlashType slashType) {
        Location eye = player.getEyeLocation();
        double yawRad = Math.toRadians(player.getYaw());

        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        double forwardDist;
        double rightDist;
        double heightSub;

        if (slashType == SlashType.STABBING_ATTACK) {
            forwardDist = 0.38;
            rightDist = 0.28;
            heightSub = player.isSneaking() ? 0.78 : 0.86;
        } else if (slashType == SlashType.DOWNWARD) {
            forwardDist = 0.65;
            rightDist = 0.16;
            heightSub = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        } else if (slashType == SlashType.BLOCK) {
            forwardDist = 0.45;
            rightDist = 0.0;
            heightSub = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        } else {
            forwardDist = FORWARD_OFFSET_BASE;
            rightDist = 0.0;
            heightSub = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        }

        return new Location(
                player.getWorld(),
                player.getLocation().getX() + fwdX * forwardDist + rightX * rightDist,
                eye.getY() - heightSub,
                player.getLocation().getZ() + fwdZ * forwardDist + rightZ * rightDist,
                player.getYaw(),
                player.getPitch()
        );
    }

    private static double calculateSlashEasingSpeed(float t) {
        if (t < 0.20f) {
            float phaseT = t / 0.20f;
            return 0.80 + 0.30 * (phaseT * phaseT);
        } else if (t < 0.55f) {
            float phaseT = (t - 0.20f) / 0.35f;
            return 1.10 + 0.65 * Math.sin(phaseT * Math.PI);
        } else {
            float phaseT = Math.min(1.0f, (t - 0.55f) / 0.45f);
            double decay = Math.pow(1.0 - phaseT, 2.0);
            return 0.65 + 0.45 * decay;
        }
    }

    private void updateTargetLocation(Location out, float progress) {
        Location eye = player.getEyeLocation();
        double yawRad = Math.toRadians(player.getYaw());

        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);
        double rightX = -Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        double lungeFactor = Math.sin(Math.clamp(progress, 0.0f, 1.0f) * Math.PI);

        double targetFwd;
        double targetRight;
        double targetHeight;

        if (slashType == SlashType.STABBING_ATTACK) {
            targetFwd = 0.38 + 0.30 * lungeFactor;
            targetRight = 0.28;
            targetHeight = player.isSneaking() ? 0.78 : 0.86;
        } else if (slashType == SlashType.DOWNWARD) {
            targetFwd = 0.65 + 0.16 * lungeFactor;
            targetRight = 0.16;
            targetHeight = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        } else if (slashType == SlashType.BLOCK) {
            targetFwd = 0.45;
            targetRight = 0.0;
            targetHeight = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        } else {
            targetFwd = FORWARD_OFFSET_BASE + FORWARD_OFFSET_LUNGE * lungeFactor;
            targetRight = 0.0;
            targetHeight = player.isSneaking() ? HEIGHT_OFFSET_SNEAK : HEIGHT_OFFSET_NORMAL;
        }

        if (!initializedPos) {
            currentForwardDist = targetFwd;
            currentRightDist = targetRight;
            currentHeightSub = targetHeight;
            initializedPos = true;
        } else {
            // Smooth glide between attack stances (35% lerp per tick)
            currentForwardDist += (targetFwd - currentForwardDist) * 0.35;
            currentRightDist += (targetRight - currentRightDist) * 0.35;
            currentHeightSub += (targetHeight - currentHeightSub) * 0.35;
        }

        out.setWorld(player.getWorld());
        out.setX(player.getLocation().getX() + fwdX * currentForwardDist + rightX * currentRightDist);
        out.setY(eye.getY() - currentHeightSub);
        out.setZ(player.getLocation().getZ() + fwdZ * currentForwardDist + rightZ * currentRightDist);
        out.setYaw(player.getYaw());
        out.setPitch(player.getPitch());
    }

    private void updateRotation(float yaw, float pitch, ModeledEntity entity, ActiveModel model, Location outLoc) {
        if (entity == null) return;

        float finalYaw = (yaw % 360.0f + 360.0f) % 360.0f;
        float finalPitch = Math.clamp(pitch, -90.0f, 90.0f);

        if (outLoc != null) {
            outLoc.setYaw(finalYaw);
            outLoc.setPitch(finalPitch);
        }

        entity.setYBodyRot(finalYaw);
        entity.setYHeadRot(finalYaw);
        try {
            entity.setXHeadRotImmediately(finalPitch);
        } catch (Throwable ignored) {}
        entity.setXHeadRot(finalPitch);

        if (model != null) {
            model.setModelRotationLocked(true);
            model.setLockedYBodyRot(finalYaw);
            model.setLockedYHeadRot(finalYaw);
            model.setLockedXHeadRot(finalPitch);
        }
    }

    public static String resolveAnimationNameCached(SlashType type, ActiveModel model) {
        if (model == null) return type == SlashType.BLOCK ? "Block_Sword" : "left_slash";
        String modelName = model.getBlueprint() != null ? model.getBlueprint().getName() : "default";
        String cacheKey = modelName + ":" + type.name();

        return ANIMATION_CACHE.computeIfAbsent(cacheKey, k -> resolveAnimationName(type, model));
    }

    private static String resolveAnimationName(SlashType type, ActiveModel model) {
        if (model.getBlueprint() == null || model.getBlueprint().getAnimations().isEmpty()) {
            return type == SlashType.BLOCK ? "Block_Sword" : "idle";
        }

        Set<String> available = model.getBlueprint().getAnimations().keySet();

        String target = switch (type) {
            case HORIZONTAL_LEFT -> "left_slash";
            case HORIZONTAL_RIGHT -> "right_slash";
            case DIAGONAL_LEFT -> "left_swing";
            case DIAGONAL_RIGHT -> "right_swing";
            case DOWNWARD -> "topswing";
            case STABBING_ATTACK -> "stabbing_attack";
            case BLOCK -> "Block_Sword";
        };

        for (String name : available) {
            if (name.equalsIgnoreCase(target)) {
                return name;
            }
        }

        String cleanTarget = target.replace("_", "");
        for (String name : available) {
            if (name.replace("_", "").equalsIgnoreCase(cleanTarget)) {
                return name;
            }
        }

        switch (type) {
            case BLOCK -> {
                for (String name : available) {
                    String lower = name.toLowerCase();
                    if (lower.contains("block") || lower.contains("parry") || lower.contains("guard") || lower.contains("defend")) {
                        return name;
                    }
                }
            }
            case DIAGONAL_LEFT, HORIZONTAL_LEFT -> {
                for (String name : available) {
                    String lower = name.toLowerCase();
                    if (lower.contains("left")) return name;
                }
            }
            case DIAGONAL_RIGHT, HORIZONTAL_RIGHT -> {
                for (String name : available) {
                    String lower = name.toLowerCase();
                    if (lower.contains("right")) return name;
                }
            }
            case DOWNWARD -> {
                for (String name : available) {
                    String lower = name.toLowerCase();
                    if (lower.contains("top") || lower.contains("down") || lower.contains("chop") || lower.contains("slam")) {
                        return name;
                    }
                }
            }
            case STABBING_ATTACK -> {
                for (String name : available) {
                    String lower = name.toLowerCase();
                    if (lower.contains("stab") || lower.contains("thrust") || lower.contains("pierce")) {
                        return name;
                    }
                }
            }
        }

        for (String name : available) {
            if (!name.equalsIgnoreCase("idle")) {
                return name;
            }
        }
        return available.iterator().next();
    }

    private static void broadcastEquipmentToTrackers(Player player, ItemStack item) {
        for (Player tracker : player.getTrackedPlayers()) {
            tracker.sendEquipmentChange(player, EquipmentSlot.HAND, item);
        }
    }

    @Override
    public void run() {
        if (!isValid()) {
            cleanup();
            cancel();
            return;
        }

        // Automatic Combo Chaining from Buffered Input
        if (comboQueued && tick >= COMBO_WINDOW_START_TICK) {
            chainTo(queuedSlashType, queuedTarget);
            return;
        }

        tick++;

        // Animation Completion Tracking
        boolean finished = animationEnded;
        if (!finished && animProperty != null) {
            if (animProperty.isFinished() || animProperty.isEnded()) {
                finished = true;
            }
        }
        if (!finished && activeModel != null && animationName != null) {
            try {
                if (!activeModel.getAnimationHandler().isPlayingAnimation(animationName)) {
                    finished = true;
                }
            } catch (Throwable ignored) {}
        }

        // Combo Finish Condition:
        // Only restore weapon and exit when animation finishes AND player hasn't queued another attack
        if (finished && tick >= minTicks) {
            postEndBufferTicks++;
            if (postEndBufferTicks >= 3 && !comboQueued) {
                cleanup();
                cancel();
                return;
            }
        }

        if (tick >= maxTicks && !comboQueued) {
            cleanup();
            cancel();
            return;
        }

        float progress;
        if (animProperty != null && animationTotalLength > 0) {
            double currentTime = animProperty.getTime();
            progress = (float) Math.clamp(currentTime / animationTotalLength, 0.0, 1.0);
        } else {
            progress = Math.min(1.0f, (float) tick / (float) minTicks);
        }

        if (animProperty != null) {
            try {
                if (slashType == SlashType.BLOCK) {
                    animProperty.setSpeed(1.0);
                } else {
                    animProperty.setSpeed(calculateSlashEasingSpeed(progress));
                }
            } catch (Throwable ignored) {}
        }

        updateTargetLocation(cachedLoc, progress);
        updateRotation(cachedLoc.getYaw(), cachedLoc.getPitch(), modeledEntity, activeModel, cachedLoc);
        dummy.teleport(cachedLoc);
    }

    private void cleanup() {
        ACTIVE_TASKS.remove(player.getUniqueId());

        if (player.isOnline()) {
            // Restore original weapon smoothly to hotbar once the whole combo is done
            if (savedWeaponItem != null && savedWeaponItem.getType() != Material.AIR) {
                ItemStack currentItemInSlot = player.getInventory().getItem(savedHeldSlot);
                if (currentItemInSlot == null || currentItemInSlot.getType() == Material.AIR || isSlashingPlaceholder(currentItemInSlot)) {
                    player.getInventory().setItem(savedHeldSlot, savedWeaponItem.clone());
                } else if (!currentItemInSlot.isSimilar(savedWeaponItem)) {
                    var leftover = player.getInventory().addItem(currentItemInSlot);
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    player.getInventory().setItem(savedHeldSlot, savedWeaponItem.clone());
                }
            }

            if (player.getInventory().getHeldItemSlot() == savedHeldSlot) {
                ItemStack actualItem = player.getInventory().getItem(savedHeldSlot);
                ItemStack itemToBroadcast = (actualItem != null && actualItem.getType() != Material.AIR) ? actualItem : savedWeaponItem;
                broadcastEquipmentToTrackers(player, itemToBroadcast);
                try {
                    player.sendEquipmentChange(player, EquipmentSlot.HAND, itemToBroadcast);
                } catch (Throwable ignored) {}
            }

            player.updateInventory();
        }

        if (this.modeledEntity != null) {
            try {
                this.modeledEntity.destroy();
            } catch (Throwable ignored) {}
        }

        WardenEntityManager.removeTempEntity(this.dummy);
    }

    public static boolean canChain(Player player) {
        SmoothSlashTask task = ACTIVE_TASKS.get(player.getUniqueId());
        return task != null && task.tick >= COMBO_WINDOW_START_TICK;
    }

    public static SmoothSlashTask getActiveTask(UUID uuid) {
        return ACTIVE_TASKS.get(uuid);
    }

    private static boolean isSlashingPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().hasCustomModelData() && item.getItemMeta().getCustomModelData() == CMD_SLASHING;
    }

    public static boolean isSlashing(Player player) {
        return ACTIVE_TASKS.containsKey(player.getUniqueId());
    }

    public static void stopSlashing(Player player) {
        SmoothSlashTask task = ACTIVE_TASKS.remove(player.getUniqueId());
        if (task != null) {
            try {
                task.cleanup();
                task.cancel();
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Primary entry point for playing combo slashes or defensive guard:
     * Chains into the active task if already running, or starts a new task.
     */
    public static boolean play(
            JavaPlugin plugin,
            Player player,
            ItemStack weapon,
            SlashType slashType,
            Entity target,
            BiConsumer<SlashType, Entity> hitCallback,
            Consumer<SlashType> swingStartCallback
    ) {
        SmoothSlashTask existing = ACTIVE_TASKS.get(player.getUniqueId());
        if (existing != null && existing.isValid()) {
            return existing.handleComboInput(slashType, target);
        }

        SmoothSlashTask task = new SmoothSlashTask(
                plugin,
                player,
                weapon,
                slashType,
                target,
                hitCallback,
                swingStartCallback
        );
        if (task.modeledEntity == null || task.activeModel == null) {
            task.cleanup();
            return false;
        }

        ACTIVE_TASKS.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    public static void play(JavaPlugin plugin, Player player, ItemStack weapon, SlashType slashType) {
        play(plugin, player, weapon, slashType, null, null, null);
    }

    public static void cleanupAll() {
        for (SmoothSlashTask task : ACTIVE_TASKS.values()) {
            try {
                task.cleanup();
                task.cancel();
            } catch (Throwable ignored) {}
        }
        ACTIVE_TASKS.clear();
    }
}
