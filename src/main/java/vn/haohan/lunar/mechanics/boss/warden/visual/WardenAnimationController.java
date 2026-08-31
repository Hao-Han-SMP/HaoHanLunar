package vn.haohan.lunar.mechanics.boss.warden.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import org.bukkit.entity.IronGolem;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;

import java.util.Map;

public final class WardenAnimationController {
    private WardenAnimationController() {}

    public static void setBoneVisible(IronGolem golem, String boneKeyword, boolean visible) {
        if (golem == null || golem.isDead()) return;
        ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
        if (me == null) return;
        for (ActiveModel model : me.getModels().values()) {
            for (Map.Entry<String, ModelBone> entry : model.getBones().entrySet()) {
                String name = entry.getKey().toLowerCase();
                if (name.contains(boneKeyword.toLowerCase())) {
                    entry.getValue().setVisible(visible);
                }
            }
        }
    }

    /**
     * Unconstrained Locomotion & Animation Manager for ModelEngine 4:
     * - When moving (walk_forward, walk_backward, walk_left, walk_right):
     *   Immediately unbinds and stops "idle" so the waist and torso bones have 100% full keyframe freedom.
     *   Only applies a quick, crisp lerp-in (0.08s) at the start of locomotion without persistent idle drag.
     * - When entering "idle" from movement:
     *   Applies a smooth, natural blend-in (0.20s) and cleans up active walking animations.
     */
    public static void playModelAnimation(IronGolem golem, WardenState state, String targetAnim, double lerpIn, double lerpOut, double speed, boolean force) {
        if (targetAnim == null || targetAnim.isEmpty()) {
            targetAnim = "idle";
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(golem);
        if (modeledEntity == null) return;

        ActiveModel model = modeledEntity.getModel(WardenConstants.MODEL_ID).orElse(null);
        if (model == null) return;

        AnimationHandler handler = model.getAnimationHandler();
        if (handler == null) return;

        String prevAnim = state.currentPlayingMovementAnim;
        boolean isLoop = targetAnim.startsWith("walk_") || targetAnim.equals("idle");
        if (isLoop && targetAnim.equals(prevAnim) && handler.isPlayingAnimation(targetAnim)) {
            return;
        }

        boolean isLocomotion = targetAnim.startsWith("walk_");

        if (isLocomotion) {
            // When walking: Play walk with crisp start and STOP idle completely to free the waist bone!
            handler.playAnimation(targetAnim, 0.08, 0.12, speed, force);
            handler.forceStopAnimation("idle");

            // Stop any other active walk directions
            String[] walks = {"walk_forward", "walk_backward", "walk_left", "walk_right"};
            for (String w : walks) {
                if (!w.equals(targetAnim) && handler.isPlayingAnimation(w)) {
                    handler.forceStopAnimation(w);
                }
            }
        } else if (targetAnim.equals("idle")) {
            // When stopping to idle: Blend smoothly from walking to idle
            handler.playAnimation("idle", 0.18, 0.18, 1.0, true);

            String[] walks = {"walk_forward", "walk_backward", "walk_left", "walk_right"};
            for (String w : walks) {
                if (handler.isPlayingAnimation(w)) {
                    handler.stopAnimation(w);
                }
            }
        } else {
            // Attacks & Skills
            handler.playAnimation(targetAnim, lerpIn, lerpOut, speed, force);
            if (prevAnim != null && !prevAnim.isEmpty() && !prevAnim.equals(targetAnim)) {
                handler.stopAnimation(prevAnim);
            }
        }

        // Clean up lingering attacks
        for (String attack : WardenConstants.ATTACK_ANIMATIONS) {
            if (!attack.equals(targetAnim) && !attack.equals(prevAnim) && handler.isPlayingAnimation(attack)) {
                handler.forceStopAnimation(attack);
            }
        }

        state.currentPlayingMovementAnim = targetAnim;
    }
}

