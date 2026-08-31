package vn.haohan.lunar.mechanics.boss.warden.visual;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.IronGolem;
import org.bukkit.util.Vector;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenBladeCalculator;

/**
 * Cinematic slash trail renderer.
 * Uses particle transitions and interpolated swept ribbons.
 */
public final class WardenSlashTrailRenderer {
    private WardenSlashTrailRenderer() {}

    private static final Particle.DustTransition LUNAR_SLASH_CYAN = new Particle.DustTransition(
            Color.fromRGB(245, 255, 255), // Silver white core
            Color.fromRGB(35, 215, 255),  // Lunar cyan trail tail
            1.8f
    );

    private static final Particle.DustTransition LUNAR_OUTER_GLOW = new Particle.DustTransition(
            Color.fromRGB(40, 180, 255),
            Color.fromRGB(10, 80, 160),
            1.2f
    );

    /**
     * Renders dynamic slash trail based on sword motion.
     */
    public static void renderAttackMotionTrail(HaoHanLunarPlugin plugin, IronGolem golem, WardenState state,
                                              String attackAnim, int currentTick, int hitTick) {
        if (golem.isDead() || currentTick < 2 || currentTick > hitTick + 4) {
            state.prevBladeTip = null;
            state.prevBladeBase = null;
            return;
        }

        // 1. Calculate 3D coordinates of blade (6.2 blocks length)
        WardenBladeCalculator.BladeSegment blade = WardenBladeCalculator.calculateBladeSegment(golem);
        if (blade == null || blade.base == null || blade.tip == null) return;

        Location curTip = blade.tip;
        Location curBase = blade.base;

        // First frame: Save position
        if (state.prevBladeTip == null || state.prevBladeBase == null || state.prevBladeTip.getWorld() != curTip.getWorld()) {
            state.prevBladeTip = curTip.clone();
            state.prevBladeBase = curBase.clone();
            return;
        }

        Location prevTip = state.prevBladeTip;
        Location prevBase = state.prevBladeBase;

        double sweepDist = curTip.distance(prevTip);
        if (sweepDist > 0.12) {
            // Interpolate swept ribbon surface
            renderSweptRibbonParticles(prevBase, prevTip, curBase, curTip, sweepDist);
        }

        // Update positions for next frame
        state.prevBladeTip = curTip.clone();
        state.prevBladeBase = curBase.clone();
    }

    /**
     * Renders glowing particles along the swept ribbon.
     */
    private static void renderSweptRibbonParticles(Location prevBase, Location prevTip,
                                                  Location curBase, Location curTip,
                                                  double sweepDist) {
        int timeSteps = Math.min(12, Math.max(5, (int) (sweepDist * 3.5)));
        int bladeDivisions = 4;

        for (int t = 0; t <= timeSteps; t++) {
            double timeProgress = (double) t / (double) timeSteps;
            Location interpBase = prevBase.clone().add(curBase.toVector().subtract(prevBase.toVector()).multiply(timeProgress));
            Location interpTip = prevTip.clone().add(curTip.toVector().subtract(prevTip.toVector()).multiply(timeProgress));
            Vector bladeSpan = interpTip.toVector().subtract(interpBase.toVector());

            for (int b = 0; b < bladeDivisions; b++) {
                double bladeProgress = 0.50 + (0.50 * ((double) b / (double) (bladeDivisions - 1)));
                Location point = interpBase.clone().add(bladeSpan.clone().multiply(bladeProgress));

                if (bladeProgress >= 0.85) {
                    point.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1, 0, 0, 0, 0, LUNAR_SLASH_CYAN);
                } else {
                    point.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, point, 1, 0, 0, 0, 0, LUNAR_OUTER_GLOW);
                }
            }
        }
    }
}
