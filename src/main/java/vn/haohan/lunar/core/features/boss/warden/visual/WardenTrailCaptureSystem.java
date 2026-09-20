package vn.haohan.lunar.core.subsystem.features.boss.warden.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.plugin.java.JavaPlugin;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.features.boss.warden.WardenConstants;
import vn.haohan.lunar.core.subsystem.features.boss.warden.WardenState;
import vn.haohan.lunar.core.subsystem.features.boss.warden.util.WardenBladeCalculator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures the 3D position of the sword blade in real-time
 * and passes the ribbon geometry to the TrailRenderer.
 */
public class WardenTrailCaptureSystem {

    private final JavaPlugin plugin;

    public WardenTrailCaptureSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static class TrailPoint {
        public Location baseLoc;
        public Location tipLoc;
        public long captureTime;

        public TrailPoint(Location base, Location tip) {
            this.baseLoc = base;
            this.tipLoc = tip;
            this.captureTime = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, Deque<TrailPoint>> historyBuffers = new ConcurrentHashMap<>();
    private static final Map<UUID, TrailRenderer> renderers = new ConcurrentHashMap<>();
    private static final Map<UUID, Entity> bossEntities = new ConcurrentHashMap<>();

    public void onDisable() {
        for (UUID uuid : renderers.keySet()) {
            clearHistory(uuid);
        }
        historyBuffers.clear();
        renderers.clear();
        bossEntities.clear();
    }

    public static void captureTick() {
        HaoHanLunarPlugin mainPlugin = HaoHanLunarPlugin.getInstance();
        var mechanic = mainPlugin != null ? mainPlugin.getLunarWardenMechanic() : null;

        for (World world : Bukkit.getWorlds()) {
            for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
                UUID uuid = golem.getUniqueId();
                boolean isBoss = (golem.getCustomName() != null && golem.getCustomName().contains("Lunar Warden"))
                        || (mechanic != null && mechanic.getBossStates().containsKey(uuid));

                if (!isBoss) continue;

                bossEntities.putIfAbsent(uuid, golem);

                ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
                if (modeledEntity == null) continue;

                var activeModel = modeledEntity.getModel(WardenConstants.MODEL_ID).orElse(null);
                if (activeModel == null) {
                    for (var m : modeledEntity.getModels().values()) {
                        activeModel = m;
                        break;
                    }
                }
                if (activeModel == null) continue;

                WardenState state = mechanic != null ? mechanic.getBossStates().get(uuid) : null;

                boolean shouldCaptureTrail = isShouldCaptureTrail(state, activeModel);

                if (!shouldCaptureTrail) {
                    continue;
                }

                WardenBladeCalculator.BladeSegment blade = WardenBladeCalculator.calculateBladeSegment(golem);
                if (blade.base != null && blade.tip != null) {
                    Deque<TrailPoint> history = historyBuffers.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                    history.addLast(new TrailPoint(blade.base.clone(), blade.tip.clone()));
                    while (history.size() > 16) {
                        history.removeFirst();
                    }
                }
            }
        }
    }

    private static boolean isShouldCaptureTrail(WardenState state, ActiveModel activeModel) {
        boolean shouldCaptureTrail = false;
        if (state != null) {
            String currentAttack = state.currentAttack != null ? state.currentAttack : "";
            switch (currentAttack) {
                case "attack_slash_straight", "attack_slash_left", "attack_sweep_right" -> {
                    if (state.attackTicks >= 1 && state.attackTicks <= state.attackTotalTicks) {
                        shouldCaptureTrail = true;
                    }
                }
            }
        } else {
            var animHandler = activeModel.getAnimationHandler();
            if (animHandler.isPlayingAnimation("attack_slash_left") || animHandler.isPlayingAnimation("attack_sweep_right")
                    || animHandler.isPlayingAnimation("attack_slash_straight")) {
                shouldCaptureTrail = true;
            }
        }
        return shouldCaptureTrail;
    }

    public static void renderTrails() {
        captureTick();

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Deque<TrailPoint>> entry : historyBuffers.entrySet()) {
            UUID uuid = entry.getKey();
            Deque<TrailPoint> history = entry.getValue();

            // Expire old trail points (> 260ms)
            while (!history.isEmpty() && (now - history.peekFirst().captureTime) > 260) {
                history.removeFirst();
            }

            Entity entity = bossEntities.get(uuid);
            if (entity == null || !entity.isValid() || entity.isDead() || history.isEmpty()) {
                clearHistory(uuid);
                continue;
            }

            TrailRenderer renderer = renderers.computeIfAbsent(uuid, k -> new TrailRenderer(entity));
            renderer.updateSegments(history.toArray(new TrailPoint[0]));
        }
    }

    public static void clearHistory(UUID uuid) {
        historyBuffers.remove(uuid);
        TrailRenderer r = renderers.remove(uuid);
        if (r != null) {
            r.destroy();
        }
        bossEntities.remove(uuid);
    }
}
