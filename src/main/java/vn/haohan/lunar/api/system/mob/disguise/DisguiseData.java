package vn.haohan.lunar.api.system.mob.disguise;

import org.bukkit.entity.EntityType;

/**
 * Immutable snapshot of visual disguise metadata applied to an entity.
 */
public record DisguiseData(
        DisguiseType type,
        EntityType entityType,
        String skinTexture,
        String skinSignature,
        String displayName
) {
    public static DisguiseData player(String name, String skinTexture, String skinSignature) {
        return new DisguiseData(DisguiseType.PLAYER, EntityType.PLAYER, skinTexture, skinSignature, name);
    }

    public static DisguiseData mob(EntityType entityType, String customName) {
        return new DisguiseData(DisguiseType.MOB, entityType, null, null, customName);
    }
}
