package vn.haohan.lunar.mechanics.boss.warden.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenBladeCalculator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ModelEngine Trail rendering for weapon slashes.
 * Renders high-fidelity sword trail during slash attack animations.
 */
public class WardenTrailCaptureSystem implements Listener {
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

                boolean shouldCaptureTrail = false;
                if (state != null) {
                    String currentAttack = state.currentAttack != null ? state.currentAttack : "";
                    switch (currentAttack) {
                        case "attack_slash_straight", "attack_slash_left", "attack_sweep_right", "skill_shield_sword_slam" -> {
                            if (state.attackTicks >= 1 && state.attackTicks <= state.attackTotalTicks) {
                                shouldCaptureTrail = true;
                            }
                        }
                    }
                } else {
                    var animHandler = activeModel.getAnimationHandler();
                    if (animHandler.isPlayingAnimation("attack_slash_left") || animHandler.isPlayingAnimation("attack_sweep_right")
                            || animHandler.isPlayingAnimation("attack_slash_straight") || animHandler.isPlayingAnimation("skill_shield_sword_slam")) {
                        shouldCaptureTrail = true;
                    }
                }

                if (!shouldCaptureTrail) {
                    continue;
                }

                // Robust blade position calculator: supports locator bones, sword bone offsets, and directional fallbacks
                WardenBladeCalculator.BladeSegment blade = WardenBladeCalculator.calculateBladeSegment(golem);
                if (blade != null && blade.base != null && blade.tip != null) {
                    Deque<TrailPoint> history = historyBuffers.computeIfAbsent(uuid, k -> new ArrayDeque<>());
                    history.addLast(new TrailPoint(blade.base.clone(), blade.tip.clone()));
                    while (history.size() > 16) {
                        history.removeFirst();
                    }
                }
            }
        }
    }

    public static void renderDebugTrails() {
        captureTick();

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Deque<TrailPoint>> entry : historyBuffers.entrySet()) {
            UUID uuid = entry.getKey();
            Deque<TrailPoint> history = entry.getValue();

            history.removeIf(p -> now - p.captureTime > 550);

            Entity boss = bossEntities.get(uuid);
            if (boss == null || !boss.isValid() || boss.isDead()) {
                clearHistory(uuid);
                continue;
            }

            if (history.isEmpty()) {
                clearHistory(uuid);
                continue;
            }

            TrailRenderer renderer = renderers.get(uuid);
            if (renderer == null) {
                renderer = new TrailRenderer(boss);
                renderers.put(uuid, renderer);
            }

            TrailPoint[] arr = new TrailPoint[12];
            int idx = 0;
            for (TrailPoint tp : history) {
                if (idx < 12) {
                    arr[idx++] = tp;
                }
            }

            renderer.updateSegments(arr);
        }

        renderers.entrySet().removeIf(entry -> {
            boolean active = historyBuffers.containsKey(entry.getKey());
            if (!active) {
                entry.getValue().destroy();
            }
            return !active;
        });
    }

    public static void clearHistory(UUID uuid) {
        historyBuffers.remove(uuid);
        bossEntities.remove(uuid);
        TrailRenderer tr = renderers.remove(uuid);
        if (tr != null) {
            tr.destroy();
        }
    }
}
