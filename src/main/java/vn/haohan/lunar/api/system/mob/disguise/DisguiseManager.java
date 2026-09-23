package vn.haohan.lunar.api.system.mob.disguise;

import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Manages entity disguise visual state.
 * Preserves server-side hitbox, stats, AI, and ActiveMob identity integrity.
 */
public final class DisguiseManager {

    private final Map<UUID, DisguiseData> activeDisguises = new ConcurrentHashMap<>();
    private BiConsumer<LivingEntity, DisguiseData> disguiseApplier = this::defaultApply;
    private BiConsumer<LivingEntity, DisguiseData> disguiseRemover = this::defaultRemove;

    public void setDisguiseApplier(BiConsumer<LivingEntity, DisguiseData> applier) {
        this.disguiseApplier = applier != null ? applier : this::defaultApply;
    }

    public void setDisguiseRemover(BiConsumer<LivingEntity, DisguiseData> remover) {
        this.disguiseRemover = remover != null ? remover : this::defaultRemove;
    }

    public boolean disguise(ActiveMob mob, DisguiseData disguiseData) {
        if (mob == null || disguiseData == null) return false;
        activeDisguises.put(mob.entityId(), disguiseData);
        mob.setDisguise(disguiseData);
        try {
            disguiseApplier.accept(mob.entity(), disguiseData);
        } catch (Throwable ignored) {}
        return true;
    }

    public boolean changeSkin(ActiveMob mob, String skinTexture, String skinSignature) {
        if (mob == null || skinTexture == null || skinTexture.isBlank()) return false;
        DisguiseData current = activeDisguises.get(mob.entityId());
        String name = current != null && current.displayName() != null ? current.displayName() :
                (mob.entity() != null && mob.entity().getCustomName() != null ? mob.entity().getCustomName() : (mob.definition() != null ? mob.definition().displayName() : "Disguised"));
        DisguiseData updated = DisguiseData.player(name, skinTexture, skinSignature);
        return disguise(mob, updated);
    }

    public boolean undisguise(ActiveMob mob) {
        if (mob == null) return false;
        DisguiseData prev = activeDisguises.remove(mob.entityId());
        mob.setDisguise(null);
        if (prev != null) {
            try {
                disguiseRemover.accept(mob.entity(), prev);
            } catch (Throwable ignored) {}
        }
        return true;
    }

    public Optional<DisguiseData> getDisguise(UUID entityId) {
        if (entityId == null) return Optional.empty();
        return Optional.ofNullable(activeDisguises.get(entityId));
    }

    public boolean isDisguised(UUID entityId) {
        return entityId != null && activeDisguises.containsKey(entityId);
    }

    public Map<UUID, DisguiseData> activeDisguisesSnapshot() {
        return Collections.unmodifiableMap(activeDisguises);
    }

    public void clear() {
        activeDisguises.clear();
    }

    private void defaultApply(LivingEntity entity, DisguiseData data) {
        if (entity == null || data == null) return;
        try {
            if (data.displayName() != null && !data.displayName().isBlank()) {
                entity.setCustomName(data.displayName());
                entity.setCustomNameVisible(true);
            }
        } catch (Throwable ignored) {}
    }

    private void defaultRemove(LivingEntity entity, DisguiseData data) {
        if (entity == null) return;
        try {
            entity.setCustomNameVisible(false);
        } catch (Throwable ignored) {}
    }
}
