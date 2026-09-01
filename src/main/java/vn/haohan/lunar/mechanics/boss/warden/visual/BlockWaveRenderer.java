package vn.haohan.lunar.mechanics.boss.warden.visual;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenEntityManager;
import vn.haohan.lunar.mechanics.boss.warden.util.WardenLocationUtil;
import vn.haohan.lunar.util.MathUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Universal High-Fidelity Block Wave & Fractured Ground Shockwave Engine for HaoHanLunar.
 * <p>
 * Key Features:
 * <ul>
 *   <li><b>Authentic Ripple Wave Propagation:</b> The epicenter / center erupts first at tick 0 and
 *       sinks back down first, while subsequent outer rings rise and fall in a sequential outward ripple crest.</li>
 *   <li><b>Fractured Tectonic Aesthetics:</b> Creates chaotic, multi-angled fractured rock slabs
 *       (pitch/yaw/roll tilt, micro-jitter offsets, scale variations) resembling shattered terrain.</li>
 *   <li><b>Snappy Explosive Rise & Smooth Cosine Settling:</b> Ease-out launch up with buttery-smooth
 *       cosine settling descent back into the terrain plane.</li>
 *   <li><b>Shortest-Arc Geodesic Un-Rotation:</b> Each cube finds the closest flat-ground orientation
 *       (0, 90, 180, 270 deg yaw) to return along the shortest path without spinning erratically.</li>
 *   <li><b>Exact Bottom-Center Pivot Anchoring:</b> Keeps the block base locked to the ground plane while tumbling.</li>
 *   <li><b>Hardware Interpolation:</b> Uses BlockDisplay (setInterpolationDuration = 1) for 60+ FPS client smoothness.</li>
 * </ul>
 */
public final class BlockWaveRenderer {

    private BlockWaveRenderer() {}

    /**
     * Calculates the height easing multiplier at progress p in [0, 1].
     * - Phase 1 (0 -> riseFraction): Fast explosive ease-out rise to crest.
     * - Phase 2 (riseFraction -> 1.0): Smooth cosine descent settling back to ground plane.
     */
    public static double calculateWaveHeightFactor(double p, double riseFraction) {
        if (p <= 0.0) return 0.0;
        if (p >= 1.0) return 0.0;

        if (p <= riseFraction) {
            double u = p / riseFraction;
            double inv = 1.0 - u;
            return 1.0 - (inv * inv * inv); // Ease-Out Cubic rise
        } else {
            double v = (p - riseFraction) / (1.0 - riseFraction);
            return 0.5 * (1.0 + Math.cos(Math.PI * v)); // Buttery-smooth cosine descent
        }
    }

    /**
     * Calculates the rotation/tilt easing multiplier at progress p in [0, 1].
     */
    public static double calculateWaveTiltFactor(double p, double riseFraction) {
        if (p <= 0.0) return 0.0;
        if (p >= 1.0) return 0.0;

        if (p <= riseFraction) {
            double u = p / riseFraction;
            double inv = 1.0 - u;
            return 1.0 - (inv * inv * inv); // Rapid tilt surge
        } else {
            double v = (p - riseFraction) / (1.0 - riseFraction);
            return 0.5 * (1.0 + Math.cos(Math.PI * v)); // Smooth un-tilt back to flat
        }
    }

    /**
     * Finds the closest flat-ground orientation (0, 90, 180, or 270 degree yaw with pitch=0, roll=0)
     * to a given rotation quaternion, ensuring the shortest rotation path back to the terrain grid.
     */
    public static Quaternionf findClosestFlatOrientation(Quaternionf q) {
        if (q == null) return new Quaternionf();

        float halfSqrt2 = (float) (Math.sqrt(2.0) / 2.0);
        Quaternionf[] candidates = new Quaternionf[] {
                new Quaternionf(0f, 0f, 0f, 1f),              // 0 deg yaw
                new Quaternionf(0f, halfSqrt2, 0f, halfSqrt2),   // 90 deg yaw
                new Quaternionf(0f, 1f, 0f, 0f),              // 180 deg yaw
                new Quaternionf(0f, -halfSqrt2, 0f, halfSqrt2)  // 270 (-90) deg yaw
        };

        Quaternionf best = candidates[0];
        float maxDot = -1f;

        for (Quaternionf cand : candidates) {
            float dot = Math.abs(q.x * cand.x + q.y * cand.y + q.z * cand.z + q.w * cand.w);
            if (dot > maxDot) {
                maxDot = dot;
                best = cand;
            }
        }

        Quaternionf result = new Quaternionf(best);
        if (q.x * result.x + q.y * result.y + q.z * result.z + q.w * result.w < 0f) {
            result.x = -result.x;
            result.y = -result.y;
            result.z = -result.z;
            result.w = -result.w;
        }
        return result;
    }

    /**
     * Generates a realistic fractured rubble tilt rotation across X, Y, and Z axes.
     * Produces jagged, chaotic slab angles that look organically fractured like shattered tectonic plates.
     */
    public static Quaternionf createChaoticWaveRotation(Random random, float dirX, float dirZ, double amplitude) {
        Quaternionf rot = new Quaternionf();

        // Tangential & outward directional crust tilt (18 - 34 degrees)
        float tiltAxisX = -dirZ;
        float tiltAxisZ = dirX;
        float baseTiltDeg = (float) ((18.0 + random.nextDouble() * 16.0) * amplitude);
        rot.rotateAxis((float) Math.toRadians(baseTiltDeg), tiltAxisX, 0f, tiltAxisZ);

        // Multi-axis chaotic fracture angles (organic pitch & roll wobble + free yaw turn)
        float rotX = (float) Math.toRadians((random.nextDouble() * 2.0 - 1.0) * 24.0 * amplitude);
        float rotY = (float) Math.toRadians((random.nextDouble() * 2.0 - 1.0) * 180.0);
        float rotZ = (float) Math.toRadians((random.nextDouble() * 2.0 - 1.0) * 24.0 * amplitude);

        rot.rotateX(rotX).rotateY(rotY).rotateZ(rotZ);

        return rot;
    }

    /**
     * Spawns and animates an individual wave block display with smooth rise & settling easing.
     */
    public static void spawnWaveBlock(
            HaoHanLunarPlugin plugin,
            World world,
            Location groundPt,
            BlockData blockData,
            double peakHeight,
            Quaternionf maxRotation,
            Vector3f scale,
            int totalTicks,
            double riseFraction
    ) {
        if (world == null || groundPt == null || blockData == null || peakHeight <= 0.01) return;

        Location spawnLoc = groundPt.clone().add(0, 0.02, 0);

        // Subtle sub-block positional jitter for organic fractured look
        Random rnd = new Random();
        float jitterX = (float) ((rnd.nextDouble() - 0.5) * 0.18);
        float jitterZ = (float) ((rnd.nextDouble() - 0.5) * 0.18);

        try {
            BlockDisplay bd = world.spawn(spawnLoc, BlockDisplay.class, entity -> {
                entity.setBlock(blockData);
                entity.setTransformation(new Transformation(
                        new Vector3f(-0.5f * scale.x, 0.0f, -0.5f * scale.z),
                        new Quaternionf(),
                        scale,
                        new Quaternionf()
                ));
                entity.setInterpolationDuration(1);
                entity.setInterpolationDelay(0);
                entity.setBillboard(Display.Billboard.FIXED);
            });

            WardenEntityManager.registerTempEntity(bd);

            final int safeTotalTicks = Math.max(6, totalTicks);
            final double safeRiseFraction = MathUtil.clamp(riseFraction, 0.15, 0.38);
            final Quaternionf closestFlatTarget = findClosestFlatOrientation(maxRotation);

            new BukkitRunnable() {
                private int tick = 0;

                @Override
                public void run() {
                    if (!bd.isValid() || tick >= safeTotalTicks) {
                        if (peakHeight > 0.12 && bd.isValid()) {
                            world.spawnParticle(Particle.BLOCK, groundPt.clone().add(0, 0.08, 0), 2, 0.18, 0.04, 0.18, 0.02, blockData);
                        }
                        WardenEntityManager.removeTempEntity(bd);
                        cancel();
                        return;
                    }

                    double progress = (double) tick / (double) safeTotalTicks;
                    double hFactor = calculateWaveHeightFactor(progress, safeRiseFraction);
                    double tFactor = calculateWaveTiltFactor(progress, safeRiseFraction);

                    float curY = (float) (peakHeight * hFactor);

                    // Interpolate rotation along shortest geodesic to closest flat orientation
                    Quaternionf currentRot = new Quaternionf();
                    if (maxRotation != null) {
                        currentRot.set(new Quaternionf(closestFlatTarget).slerp(maxRotation, (float) tFactor));
                    }

                    // Precise bottom-center pivot tracking: keeps the block base anchored while tumbling
                    Vector3f pivot = new Vector3f(0.5f * scale.x, 0.0f, 0.5f * scale.z);
                    Vector3f rotatedPivot = new Vector3f();
                    currentRot.transform(pivot, rotatedPivot);

                    // Offset includes subtle horizontal jitter fading smoothly back to 0 on landing
                    Vector3f currentTrans = new Vector3f(
                            (float) (jitterX * tFactor) - rotatedPivot.x,
                            curY - rotatedPivot.y,
                            (float) (jitterZ * tFactor) - rotatedPivot.z
                    );

                    bd.setTransformation(new Transformation(
                            currentTrans,
                            currentRot,
                            scale,
                            new Quaternionf()
                    ));
                    bd.setInterpolationDuration(1);
                    bd.setInterpolationDelay(0);

                    tick++;
                }
            }.runTaskTimer(plugin, 1L, 1L);

        } catch (Throwable ignored) {}
    }

    /**
     * Spawns an authentic concentric ripple wave expanding outward from an epicenter.
     * <p>
     * - The epicenter (radius 0) appears first (delay 0) with maximum height and settles back down first.<br>
     * - Outer rings appear progressively later and sink down later, creating a true traveling ripple crest.
     */
    public static void spawnConcentricWave(
            HaoHanLunarPlugin plugin,
            Location epicenter,
            double maxRadius,
            double delayMultiplier,
            double maxPeakHeight,
            int blockTotalTicks,
            double riseFraction,
            Random random
    ) {
        World world = epicenter.getWorld();
        if (world == null || maxRadius <= 0.1) return;

        int centerBX = epicenter.getBlockX();
        int centerBZ = epicenter.getBlockZ();
        int radiusInt = (int) Math.ceil(maxRadius);

        Map<Integer, List<int[]>> waveBuckets = new HashMap<>();

        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= maxRadius * maxRadius) {
                    double dist = Math.sqrt(distSq);
                    // Light jagged perimeter falloff at outer rim
                    if (dist > maxRadius - 0.7 && (Math.abs(dx * dz) % 3 != 0)) {
                        continue;
                    }
                    int delay = (int) Math.round(dist * delayMultiplier);
                    waveBuckets.computeIfAbsent(delay, k -> new ArrayList<>()).add(new int[]{centerBX + dx, centerBZ + dz, dx, dz});
                }
            }
        }

        for (Map.Entry<Integer, List<int[]>> entry : waveBuckets.entrySet()) {
            int delay = entry.getKey();
            List<int[]> blockCoords = entry.getValue();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (world == null) return;

                    for (int[] coord : blockCoords) {
                        int bx = coord[0];
                        int bz = coord[1];
                        int dx = coord[2];
                        int dz = coord[3];
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        Location checkLoc = new Location(world, bx + 0.5, epicenter.getY(), bz + 0.5);
                        Location groundPt = WardenLocationUtil.adjustToTerrainSurface(checkLoc, 0.0);

                        Block blockBelow = groundPt.clone().subtract(0, 0.2, 0).getBlock();
                        if (blockBelow.isPassable() || !blockBelow.getType().isSolid()) continue;
                        if (blockBelow.getType() == Material.BEDROCK || blockBelow.getType() == Material.BARRIER) continue;

                        BlockData actualBlockData = blockBelow.getBlockData();

                        // Wave amplitude decay from center (1.0 -> ~0.25 at rim)
                        double distRatio = MathUtil.clamp01(dist / (maxRadius + 0.5));
                        double amplitude = Math.max(0.12, Math.pow(1.0 - distRatio, 0.85));

                        // Center block gets full amplitude boost
                        if (dist < 0.8) {
                            amplitude = 1.10;
                        }

                        if (amplitude > 0.035) {
                            // Organic height variance per block (+-15%)
                            double heightVar = 0.88 + random.nextDouble() * 0.24;
                            double peakY = maxPeakHeight * amplitude * heightVar;

                            float dirX = (float) (dx / (dist > 0.001 ? dist : 1.0));
                            float dirZ = (float) (dz / (dist > 0.001 ? dist : 1.0));

                            Quaternionf maxRot = createChaoticWaveRotation(random, dirX, dirZ, amplitude);

                            // Organic scale variance for jagged fractured crust look
                            float sX = (float) (0.94f + (random.nextDouble() * 0.08));
                            float sY = (float) (0.90f + (random.nextDouble() * 0.12));
                            float sZ = (float) (0.94f + (random.nextDouble() * 0.08));
                            Vector3f scale = new Vector3f(sX, sY, sZ);

                            spawnWaveBlock(
                                    plugin,
                                    world,
                                    groundPt,
                                    actualBlockData,
                                    peakY,
                                    maxRot,
                                    scale,
                                    blockTotalTicks,
                                    riseFraction
                            );
                        }
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    /**
     * Spawns a directional cone shockwave of erupting blocks expanding along a facing angle.
     * Starts at the apex (delay 0) and ripples outward into the cone, with apex dropping down first.
     */
    public static void spawnConeWave(
            HaoHanLunarPlugin plugin,
            Location origin,
            Vector direction,
            double maxDistance,
            double coneAngleDegrees,
            double delayMultiplier,
            double maxPeakHeight,
            int blockTotalTicks,
            double riseFraction,
            Random random
    ) {
        World world = origin.getWorld();
        if (world == null || direction == null || maxDistance <= 0.1) return;

        Vector fwd = direction.clone().setY(0).normalize();
        if (fwd.lengthSquared() < 0.001) fwd = origin.getDirection().setY(0).normalize();

        double halfAngleRad = Math.toRadians(coneAngleDegrees * 0.5);
        double minCos = Math.cos(halfAngleRad);

        int centerBX = origin.getBlockX();
        int centerBZ = origin.getBlockZ();
        int radiusInt = (int) Math.ceil(maxDistance);

        Map<Integer, List<int[]>> waveBuckets = new HashMap<>();

        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq <= maxDistance * maxDistance) {
                    double dist = Math.sqrt(distSq);
                    if (dist < 0.5) {
                        // Origin block included at delay 0
                        int delay = 0;
                        waveBuckets.computeIfAbsent(delay, k -> new ArrayList<>()).add(new int[]{centerBX + dx, centerBZ + dz, dx, dz});
                    } else {
                        Vector toBlock = new Vector(dx / dist, 0, dz / dist);
                        double dot = fwd.dot(toBlock);

                        if (dot >= minCos) {
                            int delay = (int) Math.round(dist * delayMultiplier);
                            waveBuckets.computeIfAbsent(delay, k -> new ArrayList<>()).add(new int[]{centerBX + dx, centerBZ + dz, dx, dz});
                        }
                    }
                }
            }
        }

        for (Map.Entry<Integer, List<int[]>> entry : waveBuckets.entrySet()) {
            int delay = entry.getKey();
            List<int[]> blockCoords = entry.getValue();

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (world == null) return;

                    for (int[] coord : blockCoords) {
                        int bx = coord[0];
                        int bz = coord[1];
                        int dx = coord[2];
                        int dz = coord[3];
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        Location checkLoc = new Location(world, bx + 0.5, origin.getY(), bz + 0.5);
                        Location groundPt = WardenLocationUtil.adjustToTerrainSurface(checkLoc, 0.0);

                        Block blockBelow = groundPt.clone().subtract(0, 0.2, 0).getBlock();
                        if (blockBelow.isPassable() || !blockBelow.getType().isSolid()) continue;
                        if (blockBelow.getType() == Material.BEDROCK || blockBelow.getType() == Material.BARRIER) continue;

                        BlockData actualBlockData = blockBelow.getBlockData();

                        double distRatio = MathUtil.clamp01(dist / (maxDistance + 0.5));
                        double amplitude = Math.max(0.12, Math.pow(1.0 - distRatio, 0.85));

                        if (amplitude > 0.035) {
                            double heightVar = 0.88 + random.nextDouble() * 0.24;
                            double peakY = maxPeakHeight * amplitude * heightVar;

                            float dirX = (float) (dx / (dist > 0.001 ? dist : 1.0));
                            float dirZ = (float) (dz / (dist > 0.001 ? dist : 1.0));

                            Quaternionf maxRot = createChaoticWaveRotation(random, dirX, dirZ, amplitude);

                            float sX = (float) (0.94f + (random.nextDouble() * 0.08));
                            float sY = (float) (0.90f + (random.nextDouble() * 0.12));
                            float sZ = (float) (0.94f + (random.nextDouble() * 0.08));
                            Vector3f scale = new Vector3f(sX, sY, sZ);

                            spawnWaveBlock(
                                    plugin,
                                    world,
                                    groundPt,
                                    actualBlockData,
                                    peakY,
                                    maxRot,
                                    scale,
                                    blockTotalTicks,
                                    riseFraction
                            );
                        }
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    /**
     * Spawns a directional linear fissure shockwave line propagating forward.
     * The start of the line rises and settles down first, propagating forward in sequence.
     */
    public static void spawnLinearWave(
            HaoHanLunarPlugin plugin,
            Location origin,
            Vector direction,
            double length,
            double width,
            double speedBlocksPerTick,
            double peakHeight,
            int blockTotalTicks,
            double riseFraction,
            Random random
    ) {
        World world = origin.getWorld();
        if (world == null || direction == null || length <= 0.1) return;

        Vector fwd = direction.clone().setY(0).normalize();
        Vector right = new Vector(-fwd.getZ(), 0, fwd.getX()).normalize();

        int steps = (int) Math.ceil(length);
        double stepDist = length / Math.max(1, steps);

        for (int i = 0; i < steps; i++) {
            double curDist = i * stepDist;
            int delay = (int) Math.round(curDist / Math.max(0.1, speedBlocksPerTick));

            final int stepIndex = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (world == null) return;

                    Location centerLine = origin.clone().add(fwd.clone().multiply(curDist));
                    int widthSamples = (int) Math.max(1, Math.round(width * 2.0));

                    for (int w = -widthSamples; w <= widthSamples; w++) {
                        double lateralOffset = (w / (double) widthSamples) * (width * 0.5);
                        Location sampleLoc = centerLine.clone().add(right.clone().multiply(lateralOffset));
                        Location groundPt = WardenLocationUtil.adjustToTerrainSurface(sampleLoc, 0.0);

                        Block bBelow = groundPt.clone().subtract(0, 0.2, 0).getBlock();
                        if (bBelow.isPassable() || !bBelow.getType().isSolid()) continue;
                        if (bBelow.getType() == Material.BEDROCK || bBelow.getType() == Material.BARRIER) continue;

                        BlockData bData = bBelow.getBlockData();
                        double progressRatio = (double) stepIndex / (double) steps;
                        double amplitude = Math.max(0.15, 1.0 - (progressRatio * 0.55));
                        double latFalloff = 1.0 - Math.abs(lateralOffset / (width * 0.5 + 0.01));
                        amplitude *= Math.max(0.25, latFalloff);

                        float dirX = (float) fwd.getX();
                        float dirZ = (float) fwd.getZ();

                        Quaternionf maxRot = createChaoticWaveRotation(random, dirX, dirZ, amplitude);

                        float sX = (float) (0.92f + (random.nextDouble() * 0.08));
                        float sY = (float) (0.88f + (random.nextDouble() * 0.12));
                        float sZ = (float) (0.92f + (random.nextDouble() * 0.08));
                        Vector3f scale = new Vector3f(sX, sY, sZ);

                        spawnWaveBlock(
                                plugin,
                                world,
                                groundPt,
                                bData,
                                peakHeight * amplitude,
                                maxRot,
                                scale,
                                blockTotalTicks,
                                riseFraction
                        );
                    }
                }
            }.runTaskLater(plugin, delay);
        }
    }

    /**
     * Spawns a fast explosive burst ring of rocks popping up around an impact point and settling back.
     */
    public static void spawnBurstRing(
            HaoHanLunarPlugin plugin,
            Location center,
            double radius,
            int count,
            double peakHeight,
            int blockTotalTicks,
            double riseFraction,
            Random random
    ) {
        World world = center.getWorld();
        if (world == null || radius < 0.1 || count <= 0) return;

        Block groundBlock = center.clone().subtract(0, 0.2, 0).getBlock();
        BlockData fallbackData = (!groundBlock.isPassable() && groundBlock.getType().isSolid())
                ? groundBlock.getBlockData()
                : Material.STONE.createBlockData();

        for (int i = 0; i < count; i++) {
            double angle = (2.0 * Math.PI / count) * i + ((random.nextDouble() - 0.5) * 0.40);
            double r = radius * (0.80 + random.nextDouble() * 0.35);
            double rx = center.getX() + Math.cos(angle) * r;
            double rz = center.getZ() + Math.sin(angle) * r;

            Location checkLoc = new Location(world, rx, center.getY(), rz);
            Location groundPt = WardenLocationUtil.adjustToTerrainSurface(checkLoc, 0.0);

            Block bBelow = groundPt.clone().subtract(0, 0.2, 0).getBlock();
            BlockData bData = (!bBelow.isPassable() && bBelow.getType().isSolid() && bBelow.getType() != Material.BEDROCK)
                    ? bBelow.getBlockData()
                    : fallbackData;

            float dirX = (float) Math.cos(angle);
            float dirZ = (float) Math.sin(angle);

            // Generate chaotic 3D random tumble rotation across full X, Y, Z
            Quaternionf maxRot = createChaoticWaveRotation(random, dirX, dirZ, 1.0);

            float sX = (float) (0.86f + (random.nextDouble() * 0.12));
            float sY = (float) (0.82f + (random.nextDouble() * 0.14));
            float sZ = (float) (0.86f + (random.nextDouble() * 0.12));
            Vector3f scale = new Vector3f(sX, sY, sZ);

            spawnWaveBlock(
                    plugin,
                    world,
                    groundPt,
                    bData,
                    peakHeight * (0.82 + random.nextDouble() * 0.36),
                    maxRot,
                    scale,
                    blockTotalTicks,
                    riseFraction
            );
        }
    }
}
