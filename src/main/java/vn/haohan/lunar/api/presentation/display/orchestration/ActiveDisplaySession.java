package vn.haohan.lunar.api.presentation.display.orchestration;

import org.bukkit.entity.Display;

import java.util.UUID;

/**
 * Tracks the lifecycle and active state of a managed Minecraft 1.21 Display entity.
 */
public final class ActiveDisplaySession {

    private final UUID sessionId;
    private final UUID entityId;
    private final UUID casterId;
    private final Display displayEntity;
    private final long expireTick;
    private final DisplaySpawnOptions initialOptions;
    private DisplayTransformOptions currentTransform;

    public ActiveDisplaySession(UUID sessionId, UUID entityId, UUID casterId, Display displayEntity,
                                long expireTick, DisplaySpawnOptions initialOptions) {
        this.sessionId = sessionId != null ? sessionId : UUID.randomUUID();
        this.entityId = entityId != null ? entityId : this.sessionId;
        this.casterId = casterId;
        this.displayEntity = displayEntity;
        this.expireTick = expireTick;
        this.initialOptions = initialOptions;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getCasterId() {
        return casterId;
    }

    public Display getDisplayEntity() {
        return displayEntity;
    }

    public long getExpireTick() {
        return expireTick;
    }

    public DisplaySpawnOptions getInitialOptions() {
        return initialOptions;
    }

    public DisplayTransformOptions getCurrentTransform() {
        return currentTransform;
    }

    public void setCurrentTransform(DisplayTransformOptions currentTransform) {
        this.currentTransform = currentTransform;
    }
}
