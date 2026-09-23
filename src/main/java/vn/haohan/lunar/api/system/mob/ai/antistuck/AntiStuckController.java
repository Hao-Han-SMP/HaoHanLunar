package vn.haohan.lunar.api.system.mob.ai.antistuck;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;

/**
 * Navigation safety and anti-stuck controller for ActiveLunarMob during combat.
 * Detects if a mob is trapped on terrain/obstacles for 60 ticks (3s) and activates progressive un-stuck stages.
 */
public final class AntiStuckController {

    public enum AntiStuckLevel {
        LEVEL_1_JUMP,
        LEVEL_2_RECALCULATE,
        LEVEL_3_CLEAR_OBSTRUCTION,
        LEVEL_4_TELEPORT
    }

    public record AntiStuckAction(AntiStuckLevel level,
                                  Location currentLocation,
                                  Location targetLocation,
                                  Vector suggestedVelocity,
                                  Location suggestedTeleportLocation) {
        public AntiStuckAction {
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(currentLocation, "currentLocation must not be null");
        }
    }

    public static final int DEFAULT_STUCK_TICKS_THRESHOLD = 60; // 3 seconds
    public static final double DEFAULT_STUCK_DISTANCE_THRESHOLD = 0.5; // 0.5 blocks

    private final int stuckTicksThreshold;
    private final double stuckDistanceThreshold;

    private int stuckTicks = 0;
    private int currentStage = 1; // 1 to 4
    private Location lastLocation = null;

    public AntiStuckController() {
        this(DEFAULT_STUCK_TICKS_THRESHOLD, DEFAULT_STUCK_DISTANCE_THRESHOLD);
    }

    public AntiStuckController(int stuckTicksThreshold, double stuckDistanceThreshold) {
        this.stuckTicksThreshold = Math.max(1, stuckTicksThreshold);
        this.stuckDistanceThreshold = Math.max(0.01, stuckDistanceThreshold);
    }

    /**
     * Evaluates navigation progress each tick.
     *
     * @param currentLoc Current mob location
     * @param targetLoc  Current target location (null if no active target)
     * @param currentTick Current server tick
     * @return Triggered AntiStuckAction if threshold reached, or empty
     */
    public Optional<AntiStuckAction> tick(Location currentLoc, Location targetLoc, long currentTick) {
        if (currentLoc == null) {
            return Optional.empty();
        }

        if (targetLoc == null) {
            reset();
            return Optional.empty();
        }

        if (lastLocation == null || lastLocation.getWorld() != currentLoc.getWorld()) {
            lastLocation = currentLoc.clone();
            stuckTicks = 0;
            return Optional.empty();
        }

        double distanceMoved = currentLoc.distance(lastLocation);

        if (distanceMoved < stuckDistanceThreshold) {
            stuckTicks++;

            if (stuckTicks >= stuckTicksThreshold) {
                AntiStuckAction action = buildAction(currentLoc, targetLoc);
                // Advance to next stage for subsequent trigger
                currentStage = (currentStage % 4) + 1;
                stuckTicks = 0; // reset counter for next tier
                return Optional.of(action);
            }
        } else {
            // Mob successfully moved -> reset stuck detector
            stuckTicks = 0;
            currentStage = 1;
            lastLocation = currentLoc.clone();
        }

        return Optional.empty();
    }

    private AntiStuckAction buildAction(Location currentLoc, Location targetLoc) {
        AntiStuckLevel level = switch (currentStage) {
            case 1 -> AntiStuckLevel.LEVEL_1_JUMP;
            case 2 -> AntiStuckLevel.LEVEL_2_RECALCULATE;
            case 3 -> AntiStuckLevel.LEVEL_3_CLEAR_OBSTRUCTION;
            default -> AntiStuckLevel.LEVEL_4_TELEPORT;
        };

        Vector jumpVelocity = new Vector(0, 0.5, 0);

        // Calculate 3-block teleport towards target
        Vector dir = targetLoc.toVector().subtract(currentLoc.toVector());
        if (dir.lengthSquared() > 0.001) {
            dir.normalize();
        } else {
            dir = currentLoc.getDirection();
        }
        Location teleportLoc = currentLoc.clone().add(dir.multiply(3.0));

        return new AntiStuckAction(level, currentLoc.clone(), targetLoc.clone(), jumpVelocity, teleportLoc);
    }

    public void reset() {
        stuckTicks = 0;
        currentStage = 1;
        lastLocation = null;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public int getStuckTicksThreshold() {
        return stuckTicksThreshold;
    }
}
