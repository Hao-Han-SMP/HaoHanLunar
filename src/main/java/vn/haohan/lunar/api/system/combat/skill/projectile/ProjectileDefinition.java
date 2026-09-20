package vn.haohan.lunar.api.system.combat.skill.projectile;

import java.util.Locale;
import java.util.Objects;

/**
 * Configuration blueprint for custom projectiles.
 */
public record ProjectileDefinition(
        String id,
        BulletType bulletType,
        double velocity,
        int maxTicks,
        double hitboxSize,
        double gravity,
        SurfaceMode surfaceMode,
        int maxPierces,
        double bounceMultiplier,
        boolean homing,
        double turnRateDegrees,
        ProjectileCallback callback
) {
    public ProjectileDefinition {
        Objects.requireNonNull(id, "Projectile ID must not be null");
        id = id.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) throw new IllegalArgumentException("Projectile ID must not be blank");
        bulletType = bulletType != null ? bulletType : BulletType.PARTICLE_ONLY;
        velocity = Math.max(0.01, velocity);
        maxTicks = Math.max(1, Math.min(maxTicks, 1200));
        hitboxSize = Math.max(0.1, hitboxSize);
        surfaceMode = surfaceMode != null ? surfaceMode : SurfaceMode.DETONATE;
        maxPierces = Math.max(1, maxPierces);
        bounceMultiplier = Math.max(0.0, Math.min(bounceMultiplier, 1.5));
        turnRateDegrees = Math.max(0.1, Math.min(turnRateDegrees, 180.0));
        callback = callback != null ? callback : new ProjectileCallback() {};
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private BulletType bulletType = BulletType.PARTICLE_ONLY;
        private double velocity = 1.0;
        private int maxTicks = 100;
        private double hitboxSize = 0.5;
        private double gravity = 0.0;
        private SurfaceMode surfaceMode = SurfaceMode.DETONATE;
        private int maxPierces = 1;
        private double bounceMultiplier = 0.7;
        private boolean homing = false;
        private double turnRateDegrees = 15.0;
        private ProjectileCallback callback = new ProjectileCallback() {};

        private Builder(String id) {
            this.id = id;
        }

        public Builder bulletType(BulletType bulletType) {
            this.bulletType = bulletType;
            return this;
        }

        public Builder velocity(double velocity) {
            this.velocity = velocity;
            return this;
        }

        public Builder maxTicks(int maxTicks) {
            this.maxTicks = maxTicks;
            return this;
        }

        public Builder hitboxSize(double hitboxSize) {
            this.hitboxSize = hitboxSize;
            return this;
        }

        public Builder gravity(double gravity) {
            this.gravity = gravity;
            return this;
        }

        public Builder surfaceMode(SurfaceMode surfaceMode) {
            this.surfaceMode = surfaceMode;
            return this;
        }

        public Builder maxPierces(int maxPierces) {
            this.maxPierces = maxPierces;
            return this;
        }

        public Builder bounceMultiplier(double bounceMultiplier) {
            this.bounceMultiplier = bounceMultiplier;
            return this;
        }

        public Builder homing(boolean homing) {
            this.homing = homing;
            return this;
        }

        public Builder turnRateDegrees(double turnRateDegrees) {
            this.turnRateDegrees = turnRateDegrees;
            return this;
        }

        public Builder callback(ProjectileCallback callback) {
            this.callback = callback;
            return this;
        }

        public ProjectileDefinition build() {
            return new ProjectileDefinition(id, bulletType, velocity, maxTicks, hitboxSize,
                    gravity, surfaceMode, maxPierces, bounceMultiplier, homing, turnRateDegrees, callback);
        }
    }
}
