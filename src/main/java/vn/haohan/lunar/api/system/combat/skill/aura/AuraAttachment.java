package vn.haohan.lunar.api.system.combat.skill.aura;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Objects;
import java.util.Optional;

/**
 * Anchor for an aura effect: can be bound to an Entity, a World Location, or a ModelEngine Bone.
 */
public interface AuraAttachment {

    String attachmentKey();

    Optional<Location> location();

    Optional<LivingEntity> entity();

    boolean isValid();

    static AuraAttachment ofEntity(LivingEntity entity) {
        Objects.requireNonNull(entity, "Entity must not be null");
        return new EntityAttachment(entity);
    }

    static AuraAttachment ofLocation(Location location) {
        Objects.requireNonNull(location, "Location must not be null");
        return new LocationAttachment(location.clone());
    }

    static AuraAttachment ofBone(LivingEntity entity, String modelId, String boneName) {
        Objects.requireNonNull(entity, "Entity must not be null");
        Objects.requireNonNull(boneName, "Bone name must not be null");
        return new BoneAttachment(entity, modelId, boneName);
    }

    final class EntityAttachment implements AuraAttachment {
        private final LivingEntity entity;

        private EntityAttachment(LivingEntity entity) {
            this.entity = entity;
        }

        @Override
        public String attachmentKey() {
            return "entity:" + entity.getUniqueId();
        }

        @Override
        public Optional<Location> location() {
            return isValid() ? Optional.ofNullable(entity.getLocation()) : Optional.empty();
        }

        @Override
        public Optional<LivingEntity> entity() {
            return Optional.of(entity);
        }

        @Override
        public boolean isValid() {
            return entity.isValid() && !entity.isDead();
        }
    }

    final class LocationAttachment implements AuraAttachment {
        private final Location location;
        private final String key;

        private LocationAttachment(Location location) {
            this.location = location;
            this.key = "loc:" + (location.getWorld() != null ? location.getWorld().getName() : "noworld")
                    + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        }

        @Override
        public String attachmentKey() {
            return key;
        }

        @Override
        public Optional<Location> location() {
            return Optional.of(location.clone());
        }

        @Override
        public Optional<LivingEntity> entity() {
            return Optional.empty();
        }

        @Override
        public boolean isValid() {
            return location.getWorld() != null;
        }
    }

    final class BoneAttachment implements AuraAttachment {
        private final LivingEntity entity;
        private final String modelId;
        private final String boneName;

        private BoneAttachment(LivingEntity entity, String modelId, String boneName) {
            this.entity = entity;
            this.modelId = modelId != null ? modelId : "unknown";
            this.boneName = boneName;
        }

        public String boneName() {
            return boneName;
        }

        public String modelId() {
            return modelId;
        }

        @Override
        public String attachmentKey() {
            return "bone:" + entity.getUniqueId() + ":" + boneName;
        }

        @Override
        public Optional<Location> location() {
            // Graceful fallback to entity location when ModelEngine is not installed or resolved
            return isValid() ? Optional.ofNullable(entity.getLocation()) : Optional.empty();
        }

        @Override
        public Optional<LivingEntity> entity() {
            return Optional.of(entity);
        }

        @Override
        public boolean isValid() {
            return entity.isValid() && !entity.isDead();
        }
    }
}
