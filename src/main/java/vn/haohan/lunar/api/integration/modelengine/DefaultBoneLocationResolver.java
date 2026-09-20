package vn.haohan.lunar.core.integration.modelengine;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Standard ModelEngine 4 bone location resolver with safe reflection/soft-dependency guards.
 * Automatically falls back to entity eye or root location without throwing exceptions.
 */
public class DefaultBoneLocationResolver implements BoneLocationResolver {

    private static volatile BoneLocationResolver instance = new DefaultBoneLocationResolver();

    public static BoneLocationResolver getInstance() {
        return instance;
    }

    public static void setInstance(BoneLocationResolver customResolver) {
        instance = customResolver != null ? customResolver : new DefaultBoneLocationResolver();
    }

    @Override
    public Location resolveBoneLocation(Entity entity, String boneName) {
        if (entity == null) return null;

        Location fallback = fallbackLocation(entity);

        if (boneName == null || boneName.isBlank()) {
            return fallback;
        }

        try {
            Location boneLoc = resolveFromModelEngine(entity, boneName.trim());
            if (boneLoc != null) {
                return boneLoc;
            }
        } catch (Throwable ignored) {
            // Guard against soft dependency missing or runtime incompatibility
        }

        return fallback;
    }

    protected Location resolveFromModelEngine(Entity entity, String boneName) {
        try {
            // Check if ModelEngine plugin API is loaded
            if (com.ticxo.modelengine.api.ModelEngineAPI.getAPI() == null) {
                return null;
            }
            var modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(entity);
            if (modeledEntity == null) {
                return null;
            }
            for (var model : modeledEntity.getModels().values()) {
                if (model == null) continue;
                var bone = model.getBone(boneName);
                if (bone != null && bone.isPresent()) {
                    Location loc = bone.get().getLocation();
                    if (loc != null) {
                        return loc;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Safe fallback if ModelEngine classes fail or are unavailable
        }
        return null;
    }

    public static Location fallbackLocation(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof LivingEntity living) {
            try {
                Location eye = living.getEyeLocation();
                if (eye != null) return eye;
            } catch (Throwable ignored) {}
        }
        try {
            return entity.getLocation();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
