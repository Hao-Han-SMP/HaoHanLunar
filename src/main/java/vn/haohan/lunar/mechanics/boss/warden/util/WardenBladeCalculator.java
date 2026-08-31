package vn.haohan.lunar.mechanics.boss.warden.util;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import com.ticxo.modelengine.api.utils.OffsetMode;
import org.bukkit.Location;
import org.bukkit.entity.IronGolem;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;

/**
 * 3D blade geometry calculator from hilt base to blade tip.
 * Uses locator bones from the model.
 */
public final class WardenBladeCalculator {
    private WardenBladeCalculator() {}

    // Boss greatsword blade length (blocks)
    public static final double BLADE_LENGTH = 6.2;

    public static class BladeSegment {
        public final Location base; // Sword base / hilt near hand
        public final Location mid;  // Blade midpoint
        public final Location tip;  // Blade tip
        public final Vector direction; // Direction along blade

        public BladeSegment(Location base, Location tip) {
            this.base = base;
            this.tip = tip;
            if (base != null && tip != null && base.getWorld() != null && tip.getWorld() != null) {
                Vector diff = tip.toVector().subtract(base.toVector());
                if (diff.lengthSquared() > 0.001) {
                    this.direction = diff.clone().normalize();
                } else {
                    this.direction = new Vector(0, 1, 0);
                }
                this.mid = base.clone().add(diff.clone().multiply(0.5));
            } else {
                this.direction = new Vector(0, 1, 0);
                this.mid = base != null ? base.clone() : new Location(null, 0, 0, 0);
            }
        }

        /**
         * Gets location along blade (0.0 = Hilt, 0.5 = Mid, 1.0 = Tip).
         */
        public Location getPointAt(double progress) {
            if (base == null || tip == null) return base;
            return base.clone().add(tip.toVector().subtract(base.toVector()).multiply(progress));
        }
    }

    public static BladeSegment calculateBladeSegment(IronGolem golem) {
        if (golem == null || golem.isDead() || golem.getWorld() == null) {
            return fallbackBlade(golem);
        }

        try {
            ModeledEntity me = ModelEngineAPI.getModeledEntity(golem);
            if (me != null) {
                var modelOpt = me.getModel(WardenConstants.MODEL_ID);
                if (modelOpt.isPresent()) {
                    var model = modelOpt.get();
                    ModelBone tipBone = model.getBone("tip").orElse(null);
                    ModelBone baseBone = model.getBone("base").orElse(null);

                    // 1. Read directly from locator bones
                    if (tipBone != null && baseBone != null) {
                        Location tLoc = tipBone.getLocation();
                        Location bLoc = baseBone.getLocation();
                        if (tLoc != null && bLoc != null && tLoc.distanceSquared(bLoc) > 0.5) {
                            return new BladeSegment(bLoc, tLoc);
                        }
                    }

                    // 2. Fallback to 'sword' bone
                    ModelBone swordBone = model.getBone("sword").orElse(null);
                    if (swordBone != null) {
                        Location baseLoc = swordBone.getLocation();
                        if (baseLoc != null) {
                            Location tipCandidate = swordBone.getLocation(OffsetMode.LOCAL, new Vector3f(0f, (float) BLADE_LENGTH, 0f), false);
                            if (tipCandidate != null && tipCandidate.distanceSquared(baseLoc) > 1.0) {
                                return new BladeSegment(baseLoc, tipCandidate);
                            }
                            tipCandidate = swordBone.getLocation(OffsetMode.LOCAL, new Vector3f(0f, 0f, (float) BLADE_LENGTH), false);
                            if (tipCandidate != null && tipCandidate.distanceSquared(baseLoc) > 1.0) {
                                return new BladeSegment(baseLoc, tipCandidate);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return fallbackBlade(golem);
    }

    private static BladeSegment fallbackBlade(IronGolem golem) {
        if (golem == null) {
            Location zero = new Location(null, 0, 0, 0);
            return new BladeSegment(zero, zero);
        }
        Location base = golem.getLocation().add(0, 1.8, 0);
        Vector dir = golem.getLocation().getDirection().normalize();
        Location tip = base.clone().add(dir.multiply(BLADE_LENGTH));
        return new BladeSegment(base, tip);
    }
}
