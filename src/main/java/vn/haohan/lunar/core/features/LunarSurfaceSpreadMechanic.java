package vn.haohan.lunar.core.features;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.core.subsystem.LunarSubSystem;

import java.util.*;

/** Spreads the lunar beacon's surface conversion as a noisy, staged wave. */
public final class LunarSurfaceSpreadMechanic implements Listener, LunarSubSystem {

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

    @Override
    public String name() {
        return "LunarSurfaceSpread";
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public boolean isTickable() {
        return true;
    }

    @Override
    public void disable(HaoHanLunarPlugin plugin) {
        removeAll();
    }

    @Override
    public void tick() {
        if (spreads.isEmpty()) return;

        Iterator<Spread> iterator = spreads.iterator();
        while (iterator.hasNext()) {
            Spread spread = iterator.next();
            if (!isValid(spread)) {
                spread.restorePreviousStates();
                iterator.remove();
                continue;
            }

            spread.age++;
            spread.advanceAllStages();

            if (spread.isComplete()) {
                iterator.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!HaoHanLunarPlugin.isLunarWorld(block.getWorld()) || block.getType() != Material.BEACON) return;
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
        Iterator<Spread> iterator = spreads.iterator();
        while (iterator.hasNext()) {
            Spread spread = iterator.next();
            if (spread.beacon.getWorld() == event.getWorld()) {
                spread.restorePreviousStates();
                iterator.remove();
            }
        }
    }

    public void removeAll() {
        for (Spread spread : spreads) {
            spread.restorePreviousStates();
        }
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
        return HaoHanLunarPlugin.isLunarWorld(world) && beacon.getType() == Material.BEACON
                && world.isChunkLoaded(beacon.getX() >> 4, beacon.getZ() >> 4);
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

        private void applyRing(int stageIndex, double targetRadius) {
            double currentRadius = previousRadii[stageIndex];
            if (targetRadius <= currentRadius) return;

            Stage stage = STAGES.get(stageIndex);
            World world = beacon.getWorld();
            if (world == null) return;

            int minX = (int) Math.floor(beacon.getX() - targetRadius - edgeNoise - 1);
            int maxX = (int) Math.ceil(beacon.getX() + targetRadius + edgeNoise + 1);
            int minZ = (int) Math.floor(beacon.getZ() - targetRadius - edgeNoise - 1);
            int maxZ = (int) Math.ceil(beacon.getZ() + targetRadius + edgeNoise + 1);

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dist = Math.hypot(x + 0.5 - beacon.getX(), z + 0.5 - beacon.getZ());
                    double noise = ((Math.sin(x * 0.35) + Math.cos(z * 0.35)) * 0.5) * edgeNoise;
                    double effectiveDist = dist + noise;

                    if (effectiveDist > currentRadius && effectiveDist <= targetRadius) {
                        int y = world.getHighestBlockYAt(x, z);
                        Block block = world.getBlockAt(x, y, z);
                        if (stage.replaceable().contains(block.getType())) {
                            Location loc = block.getLocation();
                            previousStates.putIfAbsent(loc, block.getBlockData().clone());
                            block.setType(stage.targetMaterial(), false);
                            appliedStates.put(loc, block.getBlockData().clone());
                        }
                    }
                }
            }
            previousRadii[stageIndex] = targetRadius;
        }

        private void restorePreviousStates() {
            for (Map.Entry<Location, BlockData> entry : previousStates.entrySet()) {
                Block block = entry.getKey().getBlock();
                BlockData applied = appliedStates.get(entry.getKey());
                if (applied == null || block.getBlockData().matches(applied)) {
                    block.setBlockData(entry.getValue(), false);
                }
            }
            previousStates.clear();
            appliedStates.clear();
        }
    }

    private record Stage(Material targetMaterial, Set<Material> replaceable) {}
}
