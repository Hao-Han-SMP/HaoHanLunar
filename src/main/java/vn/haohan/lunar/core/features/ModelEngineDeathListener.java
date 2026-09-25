package vn.haohan.lunar.core.features;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.BlueprintAnimation;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý vòng đời chết và biến mất cho tất cả các entity mang mô hình ModelEngine.
 * Xử lý:
 * 1. Khi entity chết (bị chém chết, sát thương kết liễu...):
 *    - Nếu model có animation "death", dừng animation lặp, phát animation "death" 1 lần.
 *    - Chờ animation "death" kết thúc thì hủy triệt để ModeledEntity và ActiveModel.
 *    - Nếu model không có animation "death" (ví dụ: con lợn test của ModelEngine hoặc mob thường):
 *      lập tức dừng animation, tạo hiệu ứng tan biến và xóa sạch model, không để lại model ma.
 * 2. Khi entity bị remove khỏi world (despawn, /kill...): dọn dẹp an toàn tránh rò rỉ bộ nhớ.
 */
public class ModelEngineDeathListener implements Listener {

    private final Plugin plugin;
    // Lưu các entity đang chạy animation death để tránh hủy trùng lặp
    private final Set<UUID> dyingEntities = ConcurrentHashMap.newKeySet();

    public ModelEngineDeathListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean isDying(UUID uuid) {
        return uuid != null && dyingEntities.contains(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        UUID uuid = entity.getUniqueId();
        if (dyingEntities.contains(uuid)) {
            return;
        }

        ModeledEntity modeledEntity;
        try {
            modeledEntity = ModelEngineAPI.getModeledEntity(uuid);
        } catch (Throwable t) {
            return;
        }

        if (modeledEntity == null || modeledEntity.isDestroyed()) {
            return;
        }

        Map<String, ActiveModel> models = modeledEntity.getModels();
        if (models == null || models.isEmpty()) {
            cleanupModeledEntity(entity, modeledEntity);
            return;
        }

        double maxDeathDuration = 0.0;
        boolean hasDeathAnimation = false;

        // Kiểm tra xem có model nào có animation "death" không
        for (ActiveModel activeModel : models.values()) {
            if (activeModel == null) continue;

            AnimationHandler handler = activeModel.getAnimationHandler();
            if (handler != null) {
                // Buộc dừng tất cả animation đang chạy (đặc biệt là animation loop như idle, walk...)
                handler.forceStopAllAnimations();
            }

            ModelBlueprint blueprint = activeModel.getBlueprint();
            if (blueprint != null && blueprint.getAnimations().containsKey("death")) {
                BlueprintAnimation deathAnim = blueprint.getAnimations().get("death");
                double len = deathAnim != null ? deathAnim.getLength() : 1.5;
                if (len > maxDeathDuration) {
                    maxDeathDuration = len;
                }
                hasDeathAnimation = true;

                if (handler != null) {
                    // Phát animation death không lặp (loop = false)
                    handler.playAnimation("death", 0.1, 0.1, 1.0, false);
                }
            }
        }

        Location loc = entity.getLocation();

        if (hasDeathAnimation && maxDeathDuration > 0.0) {
            dyingEntities.add(uuid);

            // Hiệu ứng bắt đầu ngã chết
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.05);
                loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
            }

            long delayTicks = Math.max(1L, (long) Math.ceil(maxDeathDuration * 20.0));

            if (!plugin.isEnabled()) {
                cleanupModeledEntity(entity, modeledEntity);
                return;
            }

            // Lên lịch dọn dẹp sạch sẽ sau khi animation death kết thúc
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                dyingEntities.remove(uuid);
                if (loc.getWorld() != null) {
                    loc.getWorld().spawnParticle(Particle.POOF, loc.clone().add(0, 0.4, 0), 12, 0.3, 0.2, 0.3, 0.05);
                    loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 0.3, 0), 6, 0.2, 0.2, 0.2, 0.02);
                    loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 0.7f, 1.2f);
                }
                cleanupModeledEntity(entity, modeledEntity);
            }, delayTicks);
        } else {
            // Không có animation death (ví dụ: con lợn test của ModelEngine hoặc mob thông thường)
            // Lập tức tạo hiệu ứng chết và dọn dẹp sạch sẽ model, không để model bị kẹt lại
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.POOF, loc.clone().add(0, 0.5, 0), 12, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DEATH, 0.7f, 1.2f);
            }
            cleanupModeledEntity(entity, modeledEntity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        UUID uuid = entity.getUniqueId();
        if (dyingEntities.contains(uuid)) {
            // Đang chạy animation death theo lịch trình của onEntityDeath, không dọn dẹp giữa chừng
            return;
        }

        try {
            ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(uuid);
            if (modeledEntity != null && !modeledEntity.isDestroyed()) {
                cleanupModeledEntity(entity, modeledEntity);
            }
        } catch (Throwable ignored) {}
    }

    private void cleanupModeledEntity(Entity entity, ModeledEntity modeledEntity) {
        try {
            if (modeledEntity != null && !modeledEntity.isDestroyed()) {
                modeledEntity.destroy();
            }
        } catch (Throwable ignored) {}

        try {
            ModelEngineAPI.removeModeledEntity(entity.getUniqueId());
        } catch (Throwable ignored) {}

        try {
            if (entity.isValid() && entity instanceof LivingEntity living && living.isDead()) {
                entity.remove();
            }
        } catch (Throwable ignored) {}
    }
}
