package vn.haohan.lunar.mechanics;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Spreads the lunar beacon's surface conversion as a noisy, staged wave. */
public final class LunarSurfaceSpreadMechanic implements Listener {

    private static final String LUNAR_WORLD = "haohan:lunar";
    private static final List<Stage> STAGES = List.of(
            new Stage(Material.STONE, Set.of(Material.STONE, Material.DEEPSLATE, Material.TUFF,
                    Material.GRAVEL, Material.SAND)),
            new Stage(Material.GRAVEL, Set.of(Material.STONE)),
            new Stage(Material.SAND, Set.of(Material.GRAVEL)),
            new Stage(Material.DIRT, Set.of(Material.SAND)),
            new Stage(Material.GRASS_BLOCK, Set.of(Material.DIRT)));

    private final HaoHanLunarPlugin plugin;
    private final List<Spread> spreads = new ArrayList<>();
    private final double maxRadius;
    private final double edgeNoise;
    private final int[] delays;
    private final double[] stageSpeeds;

    public LunarSurfaceSpreadMechanic(HaoHanLunarPlugin plugin) {
        this.plugin = plugin;
        this.maxRadius = Math.max(1.0, plugin.getConfig().getDouble("lunar-surface.radius",
                plugin.getConfig().getDouble("beacon-shield.radius", 48.0)));
        this.edgeNoise = Math.max(0.0, plugin.getConfig().getDouble("lunar-surface.edge-noise", 1.8));
        this.delays = readDelays(plugin.getConfig().getIntegerList("lunar-surface.stage-delays"));
        this.stageSpeeds = readSpeeds(plugin.getConfig().getDoubleList("lunar-surface.stage-speeds"));
    }

    public void tick() {
        Iterator<Spread> iterator = spreads.iterator();
        while (iterator.hasNext()) {
            Spread spread = iterator.next();
            if (!isValid(spread)) {
                iterator.remove();
                continue;
            }
            spread.advanceAllStages();
            spread.age++;
            if (spread.isComplete()) iterator.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isLunar(block.getWorld()) || block.getType() != Material.BEACON) return;
        Iterator<Spread> iterator = spreads.iterator();
        while (iterator.hasNext()) {
            Spread spread = iterator.next();
            if (!spread.beacon.equals(block.getLocation())) continue;
            spread.restorePreviousStates();
            iterator.remove();
        }
        spreads.add(new Spread(block.getLocation().clone().add(0.5, 0.0, 0.5)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BEACON) {
            Iterator<Spread> iterator = spreads.iterator();
            while (iterator.hasNext()) {
                Spread spread = iterator.next();
                if (!spread.beacon.equals(event.getBlock().getLocation())) continue;
                spread.restorePreviousStates();
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        spreads.removeIf(spread -> spread.beacon.getWorld() == event.getWorld());
    }

    public void removeAll() {
        spreads.clear();
    }

    private int[] readDelays(List<Integer> configured) {
        int[] result = new int[STAGES.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = configured.size() > i ? Math.max(0, configured.get(i)) : 0;
        }
        return result;
    }

    private double[] readSpeeds(List<Double> configured) {
        double[] result = new double[STAGES.size()];
        double fallback = Math.max(0.05, plugin.getConfig().getDouble("lunar-surface.expansion-speed", 1.0));
        for (int i = 0; i < result.length; i++) {
            double defaultSpeed = fallback * Math.pow(0.8, i);
            result[i] = configured.size() > i ? Math.max(0.05, configured.get(i)) : defaultSpeed;
        }
        return result;
    }

    private boolean isValid(Spread spread) {
        World world = spread.beacon.getWorld();
        Block beacon = spread.beacon.getBlock();
        return isLunar(world) && beacon.getType() == Material.BEACON
                && world.isChunkLoaded(beacon.getX() >> 4, beacon.getZ() >> 4);
    }

    private boolean isLunar(World world) {
        return world != null && world.getKey().toString().equals(LUNAR_WORLD);
    }

    private final class Spread {
        private final Location beacon;
        private int age;
        private final double[] previousRadii = new double[STAGES.size()];
        private final Map<Location, BlockData> previousStates = new HashMap<>();
        private final Map<Location, BlockData> appliedStates = new HashMap<>();

        private Spread(Location beacon) {
            this.beacon = beacon;
        }

        private void advanceAllStages() {
            for (int stage = 0; stage < STAGES.size(); stage++) {
                if (age < delays[stage]) continue;
                double radius = Math.min(maxRadius, (age - delays[stage]) * stageSpeeds[stage]);
                applyRing(stage, radius);
            }
        }

        private boolean isComplete() {
            for (double radius : previousRadii) {
                if (radius < maxRadius) return false;
            }
            return true;
        }

        private int applyRing(int stage, double radius) {
            double previousRadius = previousRadii[stage];
            if (radius <= previousRadius) return 0;
            World world = beacon.getWorld();
            Stage current = STAGES.get(stage);
            int centerX = beacon.getBlockX();
            int centerZ = beacon.getBlockZ();
            int range = (int) Math.ceil(radius + edgeNoise + 1.0);
            int inspected = 0;
            // Keep both axes centered on the beacon. Using center + from as the
            // lower bound would make every later ring occupy only one quadrant.
            for (int x = centerX - range; x <= centerX + range; x++) {
                for (int z = centerZ - range; z <= centerZ + range; z++) {
                    double distance = Math.hypot(x + 0.5 - beacon.getX(), z + 0.5 - beacon.getZ());
                    double noisyBoundary = mixedNoise(x, z, stage) * edgeNoise;
                    if (distance > radius + noisyBoundary || distance <= previousRadius - 1.0 + noisyBoundary) continue;
                    // Never force-load terrain just because a beacon wave reaches it.
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block surface = world.getHighestBlockAt(x, z);
                    if (!current.sources.contains(surface.getType())) continue;
                    Location key = surface.getLocation();
                    previousStates.putIfAbsent(key, surface.getBlockData().clone());
                    surface.setType(current.result, false);
                    appliedStates.put(key, surface.getBlockData().clone());
                    world.spawnParticle(Particle.BLOCK, surface.getLocation().add(0.5, 1.0, 0.5),
                            2, 0.28, 0.08, 0.28, 0.0, current.result.createBlockData());
                    inspected++;
                }
            }
            previousRadii[stage] = radius;
            return inspected;
        }

        private void restorePreviousStates() {
            World world = beacon.getWorld();
            if (world == null) return;
            for (Map.Entry<Location, BlockData> entry : previousStates.entrySet()) {
                Location location = entry.getKey();
                Block block = world.getBlockAt(location);
                BlockData applied = appliedStates.get(location);
                // Do not overwrite a block that a player changed after the wave.
                if (applied != null && block.getBlockData().matches(applied)) {
                    block.setBlockData(entry.getValue(), false);
                }
            }
            previousStates.clear();
            appliedStates.clear();
        }

        private double mixedNoise(int x, int z, int stage) {
            long hash = x * 341873128712L + z * 132897987541L + stage * 42317861L;
            hash ^= hash >>> 13;
            double fine = ((hash & 0xFFFFL) / 32767.5) - 1.0;
            double broad = Math.sin(x * 0.23 + stage * 1.7) * Math.cos(z * 0.19 - stage * 0.8);
            return broad * 0.65 + fine * 0.35;
        }
    }

    private record Stage(Material result, Set<Material> sources) { }
}
