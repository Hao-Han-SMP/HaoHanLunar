package vn.haohan.lunar.mechanics.boss.warden;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.data.IEntityData;
import com.ticxo.modelengine.api.generator.blueprint.ModelBlueprint;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ManualAnimator;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.ui.WardenBossBar;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAnimationController;
import vn.haohan.lunar.mechanics.boss.warden.visual.WardenAudio;

public final class WardenSpawner {
    private WardenSpawner() {}

    public static boolean spawnWarden(HaoHanLunarPlugin plugin, LunarWardenMechanic mechanic, Location loc, Player player) {
        var registry = ModelEngineAPI.getAPI() != null ? ModelEngineAPI.getAPI().getModelRegistry() : null;
        var orderedIds = registry != null ? registry.getOrderedId() : null;

        if (orderedIds == null || orderedIds.isEmpty()) {
            String msg = "§c[HaoHanLunar] ModelEngine chưa nạp bất kỳ model nào! Hãy copy file TheLunarWarden.bbmodel vào 'plugins/ModelEngine/blueprints/' và gõ lệnh: §e/meg zip";
            plugin.getLogger().severe(msg);
            if (player != null) player.sendMessage(msg);
            return false;
        }

        String targetModelId = WardenConstants.MODEL_ID;
        boolean foundExact = false;
        for (String id : orderedIds) {
            if (id.equalsIgnoreCase(targetModelId)) {
                targetModelId = id;
                foundExact = true;
                break;
            }
        }

        if (!foundExact) {
            for (String id : orderedIds) {
                String lower = id.toLowerCase();
                if (lower.contains("warden") || lower.contains("lunar")) {
                    targetModelId = id;
                    foundExact = true;
                    break;
                }
            }
        }

        if (!foundExact && !orderedIds.isEmpty()) {
            targetModelId = orderedIds.iterator().next();
        }

        ModelBlueprint blueprint = null;
        try {
            blueprint = ModelEngineAPI.getBlueprint(targetModelId);
        } catch (Throwable ignored) {}

        if (blueprint == null) {
            String msg = "§c[HaoHanLunar] Blueprint của model '" + targetModelId + "' chưa được khởi tạo. Hãy gõ lệnh: §e/meg zip";
            plugin.getLogger().severe(msg);
            if (player != null) player.sendMessage(msg);
            return false;
        }

        ActiveModel activeModel;
        try {
            activeModel = ModelEngineAPI.createActiveModel(targetModelId);
        } catch (Throwable e) {
            String msg = "§c[HaoHanLunar] ModelEngine không thể tạo model '" + targetModelId + "': " + e.getMessage() + ". Hãy gõ: §e/meg zip";
            plugin.getLogger().severe(msg);
            if (player != null) player.sendMessage(msg);
            return false;
        }

        if (activeModel == null) {
            String msg = "§c[HaoHanLunar] Không tạo được ActiveModel '" + targetModelId + "'. Hãy gõ: §e/meg zip";
            plugin.getLogger().severe(msg);
            if (player != null) player.sendMessage(msg);
            return false;
        }

        activeModel.setScale(3.0f);
        activeModel.setHitboxScale(3.4);
        activeModel.setCanHurt(true);
        activeModel.setMainHitbox(true);
        activeModel.setInvisUpdate(true);
        activeModel.setViewRange(2.0f);

        IronGolem golem = null;
        try {
            golem = loc.getWorld().spawn(loc, IronGolem.class, entity -> {
                entity.setCustomName("§c§lThe Lunar Warden");
                entity.setCustomNameVisible(true);
                var maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
                if (maxHealth != null) {
                    maxHealth.setBaseValue(WardenConstants.BOSS_MAX_HEALTH);
                }
                entity.setHealth(WardenConstants.BOSS_MAX_HEALTH);
                entity.setAware(false);
                entity.setInvisible(true);

                if (entity.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
                }

                try {
                    if (entity.getAttribute(Attribute.STEP_HEIGHT) != null) {
                        entity.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(3.0);
                    }
                } catch (Throwable ignored) {}

                applyScaleAttribute(entity, 3.4);
            });

            var maxHealthAttr = golem.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(WardenConstants.BOSS_MAX_HEALTH);
            }
            golem.setHealth(WardenConstants.BOSS_MAX_HEALTH);

            // Apply Scale Attribute to enlarge entity hitbox to fully encompass 3.0x model (head, crown, arms)
            applyScaleAttribute(golem, 3.4);

            final WardenState state = new WardenState();
            state.bossBar = BossBar.bossBar(
                    WardenBossBar.buildBossBarTitle(WardenConstants.BOSS_MAX_HEALTH, WardenConstants.BOSS_MAX_HEALTH),
                    0.0f,
                    BossBar.Color.WHITE,
                    BossBar.Overlay.PROGRESS
            );
            mechanic.getBossStates().put(golem.getUniqueId(), state);

            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(golem);
            modeledEntity.addModel(activeModel, true);
            modeledEntity.setBaseEntityVisible(false);
            modeledEntity.setModelRotationLocked(true);

            if (modeledEntity.getAnimationLodHandler() != null) {
                modeledEntity.getAnimationLodHandler().setEnabled(false);
            }

            if (modeledEntity.getBase() != null) {
                IEntityData data = modeledEntity.getBase().getData();
                if (data != null) {
                    data.setBackCull(false);
                    data.setBlockedCull(false);
                    data.setVerticalCull(false);
                }
            }

            activeModel.getBone("head").ifPresent(headBone -> {
                headBone.setManualAnimator(new ManualAnimator() {
                    @Override
                    public boolean applyBoneDefaultLocal() {
                        return true;
                    }

                    @Override
                    public void animate(ModelBone bone) {
                        bone.getLocalTransform().mutateLeftEuler(euler -> {
                            euler.x = (float) Math.toRadians(state.headPitch);
                            euler.y = (float) Math.toRadians(state.headYawLocal);
                            euler.z = 0f;
                        });
                    }
                });
            });

            WardenAnimationController.playModelAnimation(golem, state, "idle", 0.1, 0.1, 1.0, true);
            WardenAudio.playCustomSound(loc, "haohan:boss.sword_draw", 1.8f, 1.0f);
            WardenAudio.playCustomSound(loc, "haohan:boss.electric", 1.5f, 1.2f);
            if (player != null) player.sendMessage("§aSpawned TheLunarWarden!");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("Lỗi khi spawn và gán model cho TheLunarWarden: " + t.getMessage());
            t.printStackTrace();
            if (golem != null && golem.isValid()) {
                mechanic.getBossStates().remove(golem.getUniqueId());
                golem.remove();
            }
            if (player != null) {
                player.sendMessage("§c[HaoHanLunar] Lỗi khi gán model: Hãy gõ lệnh §e/meg zip §crồi thử lại!");
            }
            return false;
        }
    }

    private static void applyScaleAttribute(IronGolem golem, double scale) {
        try {
            AttributeInstance instance = golem.getAttribute(Attribute.SCALE);
            if (instance != null) {
                instance.setBaseValue(scale);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
            if (attr != null) {
                AttributeInstance instance = golem.getAttribute(attr);
                if (instance != null) {
                    instance.setBaseValue(scale);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
            if (attr != null) {
                AttributeInstance instance = golem.getAttribute(attr);
                if (instance != null) {
                    instance.setBaseValue(scale);
                }
            }
        } catch (Throwable ignored) {}
    }
}
