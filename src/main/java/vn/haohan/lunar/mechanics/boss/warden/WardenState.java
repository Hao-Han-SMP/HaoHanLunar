package vn.haohan.lunar.mechanics.boss.warden;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.haohan.lunar.util.MathUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WardenState {
    public WardenBehavior currentBehavior = WardenBehavior.ADVANCE;
    public int behaviorTimer = 0;
    public double currentForwardSpeed = 0;
    public double currentStrafeSpeed = 0;
    public float headPitch = 0f;
    public float headYawLocal = 0f;

    // Locomotion animation state tracker
    public String currentPlayingMovementAnim = "";

    // Boss Bar System
    public BossBar bossBar = null;
    public long animTick = 0;
    public final Set<UUID> activeBossBarViewers = new HashSet<>();

    // Threat & Aggro System
    public final Map<UUID, Double> damageThreatTable = new HashMap<>();
    public UUID currentTargetUUID = null;

    // Zig-Zag Pursuit System
    public int zigZagPursuitCooldown = 0;
    public boolean isExecutingZigZagPursuit = false;

    // Anti-Kiting & Chase-Stall Counter System
    public int chaseStallTimer = 0;

    // Attack & Combo State
    public String currentAttack = "";
    public String queuedComboAttack = "";
    public int attackTicks = 0;
    public int attackTotalTicks = 0;
    public int attackHitTick = 0;
    public boolean attackHitDone = false;
    public boolean isMovingAttack = false;
    public int attackCooldown = MathUtil.secondsToTicks(1.25);
    public double currentAttackTotalDamage = 0.0;

    // Multi-Tick Hitbox Tracking
    public final Set<UUID> hitVictimsThisAttack = new HashSet<>();
    public final Map<UUID, Integer> multiHitTickMap = new HashMap<>();
    public final Map<UUID, Integer> sideGrazeHitTickMap = new HashMap<>();

    // Blade Mesh Ribbon Trail Tracking
    public Location prevBladeTip = null;
    public Location prevBladeBase = null;

    // Agility & Dodge System
    public int dashCooldown = MathUtil.secondsToTicks(4.0);

    // Model Warp / Shrink FX Task Tracking
    public BukkitRunnable activeWarpShrinkTask = null;

    // Skill & Attack Cooldowns
    public int summonSkillCooldown = MathUtil.secondsToTicks(45.0);
    public int shieldChargeCooldown = MathUtil.secondsToTicks(12.0);
    public int shieldSwordSlamCooldown = MathUtil.secondsToTicks(22.0);
    public int groundSlamCooldown = MathUtil.secondsToTicks(10.0);
    public int thrustCooldown = MathUtil.secondsToTicks(6.0);
    public int shieldBlockPushCooldown = MathUtil.secondsToTicks(8.0);
    public int shieldBlockCooldown = MathUtil.secondsToTicks(10.0);

    // Celestial Summon Tracking
    public final List<Location> lockedSummonLocations = new ArrayList<>();

    // Aerial Throw & Slam Tracking
    public Location slamTargetGroundLoc = null;
    public final List<Entity> activeWeaponDisplays = new ArrayList<>();
    public boolean shieldThrownDone = false;
    public boolean swordThrownDone = false;
    public boolean slamImpactDone = false;
    public boolean weaponPickupDone = false;

    // Impale Grab State
    public UUID impaledTargetUUID = null;
    public boolean flingExecuted = false;

    // Anti-Wall-Clip & Anti-Corner-Stuck Tracking
    public Location lastTrackedLoc = null;
    public int cornerStuckTicks = 0;
    public int insideWallTicks = 0;

    public void cleanup() {
        if (activeWarpShrinkTask != null) {
            activeWarpShrinkTask.cancel();
            activeWarpShrinkTask = null;
        }
        for (Entity e : activeWeaponDisplays) {
            if (e != null && e.isValid()) {
                try {
                    ModeledEntity me = ModelEngineAPI.getModeledEntity(e);
                    if (me != null) {
                        me.destroy();
                    }
                } catch (Throwable ignored) {}
                e.remove();
            }
        }
        activeWeaponDisplays.clear();
        lockedSummonLocations.clear();
        multiHitTickMap.clear();
        sideGrazeHitTickMap.clear();
        hitVictimsThisAttack.clear();
        currentAttackTotalDamage = 0.0;

        if (bossBar != null) {
            for (UUID viewerId : activeBossBarViewers) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null) {
                    p.hideBossBar(bossBar);
                }
            }
            activeBossBarViewers.clear();
        }
        if (impaledTargetUUID != null) {
            Entity victim = Bukkit.getEntity(impaledTargetUUID);
            if (victim instanceof Player p) {
                p.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            impaledTargetUUID = null;
        }
        prevBladeTip = null;
        prevBladeBase = null;
    }
}
