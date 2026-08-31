package vn.haohan.lunar.mechanics.boss.warden.visual;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.model.bone.SimpleManualAnimator;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.lunar.util.MathUtil;

import java.util.Optional;

/**
 * High-fidelity 3D ruled-surface trail renderer for Lunar Warden.
 * Resamples sword trajectory via Centripetal Catmull-Rom splines, aligns 4-vertex quadrilateral segments
 * with seamless miter compensation and dynamic crescent tapering purely via ModelEngine 3D bones.
 */
public class TrailRenderer {

    private final ArmorStand anchor;
    private final ModeledEntity modeledAnchor;
    private final ActiveModel trailModel;

    public TrailRenderer(Entity boss) {
        Location startLoc = boss.getLocation();
        startLoc.setYaw(0);
        startLoc.setPitch(0);

        anchor = boss.getWorld().spawn(startLoc, ArmorStand.class, as -> {
            as.setVisible(false);
            as.setGravity(false);
            as.setMarker(true);
            as.setPersistent(false);
        });

        modeledAnchor = ModelEngineAPI.createModeledEntity(anchor);
        ActiveModel am = null;
        try {
            am = ModelEngineAPI.createActiveModel("lunar_warden_trail");
            if (am != null) {
                modeledAnchor.addModel(am, true);
            }
        } catch (Throwable ignored) {}
        this.trailModel = am;
    }

    public static class ResampledPoint {
        public Vector3f baseLoc;
        public Vector3f tipLoc;
    }

    public void updateSegments(WardenTrailCaptureSystem.TrailPoint[] rawHistory) {
        if (trailModel == null) return;

        int validCount = 0;
        for (int i = 0; i < rawHistory.length; i++) {
            if (rawHistory[i] != null) validCount++;
            else break;
        }

        if (validCount > 0) {
            Location anchorLoc = anchor.getLocation();
            Location target = rawHistory[validCount - 1].baseLoc;
            if (anchorLoc.distanceSquared(target) > 225) {
                Location teleportLoc = target.clone();
                teleportLoc.setYaw(0);
                teleportLoc.setPitch(0);
                anchor.teleport(teleportLoc);
            }
        }

        Vector3f anchorV = new Vector3f(
                (float) anchor.getLocation().getX(),
                (float) anchor.getLocation().getY(),
                (float) anchor.getLocation().getZ()
        );

        ResampledPoint[] history = new ResampledPoint[12];

        if (validCount >= 2) {
            float[] tVals = new float[validCount];
            tVals[0] = 0;
            float totalDist = 0;
            for (int i = 1; i < validCount; i++) {
                float distBase = MathUtil.toVector3f(rawHistory[i].baseLoc).distance(MathUtil.toVector3f(rawHistory[i - 1].baseLoc));
                float distTip = MathUtil.toVector3f(rawHistory[i].tipLoc).distance(MathUtil.toVector3f(rawHistory[i - 1].tipLoc));
                // Weight tip distance heavier as it sweeps a longer arc
                float dist = (distBase * 0.35f) + (distTip * 0.65f);
                totalDist += Math.max(dist, 0.001f);
                tVals[i] = totalDist;
            }

            for (int i = 0; i < 12; i++) {
                float targetT = totalDist * ((float) i / 11f);
                int segment = 0;
                for (int j = 0; j < validCount - 1; j++) {
                    if (targetT >= tVals[j] && targetT <= tVals[j + 1]) {
                        segment = j;
                        break;
                    }
                }
                if (targetT >= totalDist) segment = Math.max(0, validCount - 2);

                float tSegStart = tVals[segment];
                float tSegEnd = tVals[segment + 1];
                float fraction = (tSegEnd == tSegStart) ? 0 : (targetT - tSegStart) / (tSegEnd - tSegStart);
                fraction = MathUtil.clamp01(fraction);

                Vector3f base0 = MathUtil.toVector3f(rawHistory[Math.max(0, segment - 1)].baseLoc);
                Vector3f base1 = MathUtil.toVector3f(rawHistory[segment].baseLoc);
                Vector3f base2 = MathUtil.toVector3f(rawHistory[Math.min(validCount - 1, segment + 1)].baseLoc);
                Vector3f base3 = MathUtil.toVector3f(rawHistory[Math.min(validCount - 1, segment + 2)].baseLoc);

                Vector3f tip0 = MathUtil.toVector3f(rawHistory[Math.max(0, segment - 1)].tipLoc);
                Vector3f tip1 = MathUtil.toVector3f(rawHistory[segment].tipLoc);
                Vector3f tip2 = MathUtil.toVector3f(rawHistory[Math.min(validCount - 1, segment + 1)].tipLoc);
                Vector3f tip3 = MathUtil.toVector3f(rawHistory[Math.min(validCount - 1, segment + 2)].tipLoc);

                history[i] = new ResampledPoint();
                // Centripetal Catmull-Rom prevents loop artifacts during sharp swing arcs
                history[i].baseLoc = MathUtil.catmullRomCentripetal(base0, base1, base2, base3, fraction).sub(anchorV);
                history[i].tipLoc = MathUtil.catmullRomCentripetal(tip0, tip1, tip2, tip3, fraction).sub(anchorV);
            }
        }

        for (int i = 0; i < 11; i++) {
            Optional<ModelBone> boneOpt = trailModel.getBone("s" + String.format("%02d", i));
            if (boneOpt.isEmpty()) continue;
            ModelBone bone = boneOpt.get();

            if (history[i] == null || history[i + 1] == null) {
                SimpleManualAnimator anim = new SimpleManualAnimator();
                anim.getScale().set(0, 0, 0);
                bone.setManualAnimator(anim);
                continue;
            }

            // 4 vertices of ruled quadrilateral patch i:
            // V0 = baseA, V1 = tipA, V2 = tipB, V3 = baseB
            Vector3f baseA = history[i].baseLoc;
            Vector3f tipA = history[i].tipLoc;
            Vector3f baseB = history[i + 1].baseLoc;
            Vector3f tipB = history[i + 1].tipLoc;

            Vector3f midA = new Vector3f(baseA).add(tipA).mul(0.5f);
            Vector3f midB = new Vector3f(baseB).add(tipB).mul(0.5f);

            // Centroid of the quadrilateral
            Vector3f pos = new Vector3f(midA).add(midB).mul(0.5f);

            // Forward sweep direction along ribbon midline
            Vector3f dirX = new Vector3f(midB).sub(midA);
            float chordLength = dirX.length();
            if (chordLength < 0.0001f) {
                dirX.set(1, 0, 0);
            } else {
                dirX.normalize();
            }

            // Outer tip sweep arc distance vs inner base sweep arc distance
            float tipSweepDist = new Vector3f(tipB).sub(tipA).length();

            // Span direction (base -> tip) averaged across station A and B
            Vector3f spanA = new Vector3f(tipA).sub(baseA);
            Vector3f spanB = new Vector3f(tipB).sub(baseB);
            Vector3f dirZ = new Vector3f(spanA).add(spanB).mul(0.5f);

            float rawWidth = dirZ.length();
            if (rawWidth < 0.0001f) {
                dirZ.set(0, 0, 1);
            } else {
                // Orthonormalize dirZ against dirX (Gram-Schmidt)
                float dot = dirZ.dot(dirX);
                dirZ.sub(new Vector3f(dirX).mul(dot)).normalize();
            }

            // Normal direction (perpendicular to ribbon surface)
            Vector3f dirY = new Vector3f();
            dirZ.cross(dirX, dirY);
            dirY.normalize();

            // Length scaling: compensate outer arc curve to ensure seamless connection without triangular gaps
            float length = Math.max(chordLength, tipSweepDist) * 1.08f;

            // Dynamic aerodynamic crescent tapering:
            // Older segments (i close to 0) smoothly taper to a sharp edge at the tail
            float prog = (float) (i + 1) / 11.0f;
            float taperFactor = (float) Math.pow(Math.sin(prog * Math.PI * 0.5f), 0.65f);
            float width = Math.max(0.01f, rawWidth * taperFactor);

            Matrix3f mat = new Matrix3f(
                    dirX.x, dirX.y, dirX.z,
                    dirY.x, dirY.y, dirY.z,
                    dirZ.x, dirZ.y, dirZ.z
            );
            Quaternionf rot = new Quaternionf().setFromNormalized(mat);

            SimpleManualAnimator anim = new SimpleManualAnimator();
            anim.getPosition().set(pos);
            Object rotObj = anim.getRotation();
            if (rotObj instanceof Quaternionf) {
                ((Quaternionf) rotObj).set(rot);
            }
            anim.getScale().set(length, Math.max(0.15f, taperFactor), width);
            bone.setManualAnimator(anim);
        }
    }

    public void destroy() {
        if (modeledAnchor != null) {
            try {
                modeledAnchor.destroy();
            } catch (Throwable ignored) {}
        }
        if (anchor != null && anchor.isValid()) {
            anchor.remove();
        }
    }
}
