package vn.haohan.lunar.core.features.boss.warden;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.LivingEntity;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Owns ModelEngine attachment lifecycle without leaking ModelEngine calls into mob orchestration. */
public final class ModelEngineMobAdapter {

    private final ModelEngineBridge bridge;
    private final Consumer<String> errorLogger;
    private final Map<UUID, String> attachedModels = new ConcurrentHashMap<>();

    public ModelEngineMobAdapter() {
        this(new ProductionModelEngineBridge(), message -> { });
    }

    public ModelEngineMobAdapter(ModelEngineBridge bridge, Consumer<String> errorLogger) {
        this.bridge = Objects.requireNonNull(bridge, "ModelEngine bridge must not be null");
        this.errorLogger = Objects.requireNonNull(errorLogger, "Error logger must not be null");
    }

    public boolean attach(ActiveMob activeMob, String modelId) {
        Objects.requireNonNull(activeMob, "Active mob must not be null");
        if (modelId == null || modelId.isBlank()) {
            errorLogger.accept("Cannot attach ModelEngine model: model ID is blank");
            return false;
        }
        try {
            if (!bridge.attach(activeMob.entity(), modelId.trim())) {
                errorLogger.accept("ModelEngine model not found or could not attach: " + modelId);
                return false;
            }
            attachedModels.put(activeMob.entityId(), modelId.trim());
            return true;
        } catch (RuntimeException exception) {
            errorLogger.accept("ModelEngine attach failed for '" + modelId + "': " + exception.getMessage());
            return false;
        }
    }

    public void destroy(ActiveMob activeMob) {
        if (activeMob != null) destroy(activeMob.entity());
    }

    public void destroy(LivingEntity entity) {
        if (entity == null) return;
        attachedModels.remove(entity.getUniqueId());
        try {
            bridge.destroy(entity);
        } catch (RuntimeException exception) {
            errorLogger.accept("ModelEngine destroy failed: " + exception.getMessage());
        }
    }

    public boolean isAttached(UUID entityId) {
        return attachedModels.containsKey(entityId);
    }

    public int attachedCount() {
        return attachedModels.size();
    }

    public interface ModelEngineBridge {
        boolean attach(LivingEntity entity, String modelId);
        void destroy(LivingEntity entity);
    }

    private static final class ProductionModelEngineBridge implements ModelEngineBridge {
        @Override
        public boolean attach(LivingEntity entity, String modelId) {
            if (ModelEngineAPI.getAPI() == null || ModelEngineAPI.getBlueprint(modelId) == null) return false;
            ActiveModel activeModel = ModelEngineAPI.createActiveModel(modelId);
            if (activeModel == null) return false;
            activeModel.setScale(3.0f);
            activeModel.setHitboxScale(3.4);
            activeModel.setCanHurt(true);
            activeModel.setMainHitbox(true);
            activeModel.setInvisUpdate(true);
            activeModel.setViewRange(2.0f);
            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(entity);
            if (modeledEntity == null) return false;
            modeledEntity.addModel(activeModel, true);
            modeledEntity.setBaseEntityVisible(false);
            modeledEntity.setModelRotationLocked(true);
            return true;
        }

        @Override
        public void destroy(LivingEntity entity) {
            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);
            if (modeledEntity != null) modeledEntity.destroy();
        }
    }
}
