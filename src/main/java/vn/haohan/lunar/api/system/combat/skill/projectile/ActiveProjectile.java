package vn.haohan.lunar.api.system.combat.skill.projectile;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime flight simulation of a projectile with anti-tunneling raycasting, homing, and bounce dynamics.
 */
public final class ActiveProjectile {

    private static final double MAX_RAYTRACE_STEP = 0.4;

    private final UUID instanceId;
    private final ProjectileDefinition definition;
    private final Location origin;
    private final UUID shooterId;
    private final TargetRef homingTarget;

    private Location currentLocation;
    private Vector currentVelocity;
    private int ticksAlive;
    private int piercesRemaining;
    private volatile boolean dead;
    private final Set<UUID> hitEntities = new HashSet<>();

    public ActiveProjectile(ProjectileDefinition definition, Location startLocation,
                            Vector initialDirection, UUID shooterId, TargetRef homingTarget) {
        this.instanceId = UUID.randomUUID();
        this.definition = Objects.requireNonNull(definition, "ProjectileDefinition must not be null");
        this.origin = Objects.requireNonNull(startLocation, "Start location must not be null").clone();
        this.currentLocation = startLocation.clone();
        this.shooterId = shooterId;
        this.homingTarget = homingTarget;
        this.currentVelocity = initialDirection != null && initialDirection.lengthSquared() > 0
                ? initialDirection.clone().normalize().multiply(definition.velocity())
                : new Vector(0, 0, 1).multiply(definition.velocity());
        this.piercesRemaining = definition.maxPierces();
        this.ticksAlive = 0;
        this.dead = false;
    }

    public UUID instanceId() { return instanceId; }
    public ProjectileDefinition definition() { return definition; }
    public Location origin() { return origin.clone(); }
    public Location currentLocation() { return currentLocation.clone(); }
    public Vector currentVelocity() { return currentVelocity.clone(); }
    public UUID shooterId() { return shooterId; }
    public int ticksAlive() { return ticksAlive; }
    public boolean isDead() { return dead; }

    public void tick() {
        if (dead) return;

        ticksAlive++;
        if (ticksAlive > definition.maxTicks()) {
            terminate();
            return;
        }

        // 1. Homing Steering Logic
        if (definition.homing() && homingTarget != null) {
            Location targetLoc = homingTarget.location();
            if (targetLoc != null && targetLoc.getWorld() != null && targetLoc.getWorld().equals(currentLocation.getWorld())) {
                Vector toTarget = targetLoc.toVector().subtract(currentLocation.toVector());
                if (toTarget.lengthSquared() > 0.001) {
                    steerTowards(toTarget.normalize());
                }
            }
        }

        // 2. Anti-tunneling raycast step division
        World world = currentLocation.getWorld();
        if (world == null) {
            terminate();
            return;
        }

        double totalDistance = currentVelocity.length();
        if (totalDistance <= 0.0001) {
            terminate();
            return;
        }

        Vector direction = currentVelocity.clone().normalize();
        double distanceTraveled = 0.0;

        while (distanceTraveled < totalDistance && !dead) {
            double step = Math.min(MAX_RAYTRACE_STEP, totalDistance - distanceTraveled);
            Location stepOrigin = currentLocation.clone().add(direction.clone().multiply(distanceTraveled));

            // Raytrace entities first
            boolean hitEntity = performEntityRaytrace(world, stepOrigin, direction, step);
            if (hitEntity && dead) return;

            // Raytrace solid blocks
            boolean hitBlock = performBlockRaytrace(world, stepOrigin, direction, step);
            if (hitBlock && dead) return;

            distanceTraveled += step;
        }

        if (!dead) {
            currentLocation.add(currentVelocity);

            // 3. Gravity downward acceleration
            if (definition.gravity() != 0.0) {
                currentVelocity.setY(currentVelocity.getY() - definition.gravity());
            }

            // 4. Tick callback
            try {
                definition.callback().onTick(this);
            } catch (Exception ignored) {}
        }
    }

    private boolean performEntityRaytrace(World world, Location stepOrigin, Vector direction, double step) {
        try {
            RayTraceResult entityTrace = world.rayTraceEntities(stepOrigin, direction, step, definition.hitboxSize(),
                    entity -> entity instanceof LivingEntity living
                            && !living.isDead()
                            && (shooterId == null || !shooterId.equals(entity.getUniqueId()))
                            && !hitEntities.contains(entity.getUniqueId())
            );

            if (entityTrace != null && entityTrace.getHitEntity() instanceof LivingEntity victim) {
                hitEntities.add(victim.getUniqueId());
                try {
                    definition.callback().onHitEntity(this, victim);
                } catch (Exception ignored) {}

                switch (definition.surfaceMode()) {
                    case DETONATE -> {
                        terminate();
                        return true;
                    }
                    case PIERCE -> {
                        piercesRemaining--;
                        if (piercesRemaining <= 0) {
                            terminate();
                            return true;
                        }
                    }
                    case BOUNCE -> {
                        currentVelocity.multiply(-definition.bounceMultiplier());
                    }
                    case SLIDE -> {
                        // Slide continues through entity
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            // Mock or headless environment without full entity raytracing
        }
        return false;
    }

    private boolean performBlockRaytrace(World world, Location stepOrigin, Vector direction, double step) {
        try {
            RayTraceResult blockTrace = world.rayTraceBlocks(stepOrigin, direction, step, FluidCollisionMode.NEVER, true);
            if (blockTrace != null && blockTrace.getHitBlock() != null) {
                Block hitBlock = blockTrace.getHitBlock();
                BlockFace hitFace = blockTrace.getHitBlockFace();
                Vector normal = hitFace != null ? hitFace.getDirection() : new Vector(0, 1, 0);

                try {
                    definition.callback().onHitBlock(this, hitBlock.getLocation(), normal);
                } catch (Exception ignored) {}

                switch (definition.surfaceMode()) {
                    case DETONATE -> {
                        terminate();
                        return true;
                    }
                    case BOUNCE -> {
                        // Reflect velocity vector: v' = v - 2*(v . n)*n
                        double dot = currentVelocity.dot(normal);
                        currentVelocity.subtract(normal.clone().multiply(2 * dot)).multiply(definition.bounceMultiplier());
                        return true;
                    }
                    case PIERCE -> {
                        piercesRemaining--;
                        if (piercesRemaining <= 0) {
                            terminate();
                            return true;
                        }
                    }
                    case SLIDE -> {
                        // Project onto surface tangent plane: v' = v - (v . n)*n
                        double dot = currentVelocity.dot(normal);
                        currentVelocity.subtract(normal.clone().multiply(dot));
                        return true;
                    }
                }
                return true;
            }
        } catch (Exception ignored) {
            // Mock or headless environment
        }
        return false;
    }

    private void steerTowards(Vector desiredDirection) {
        double currentAngle = currentVelocity.angle(desiredDirection);
        if (Double.isNaN(currentAngle) || currentAngle <= 0.0001) return;

        double maxTurnRad = Math.toRadians(definition.turnRateDegrees());
        double speed = currentVelocity.length();

        if (currentAngle <= maxTurnRad) {
            currentVelocity = desiredDirection.clone().multiply(speed);
        } else {
            // Spherical / proportional vector blending clamped to max turn rate
            double fraction = maxTurnRad / currentAngle;
            Vector blended = currentVelocity.clone().multiply(1.0 - fraction).add(desiredDirection.clone().multiply(speed * fraction));
            currentVelocity = blended.normalize().multiply(speed);
        }
    }

    public void terminate() {
        if (dead) return;
        dead = true;
        try {
            definition.callback().onEnd(this);
        } catch (Exception ignored) {}
    }
}
