package vn.haohan.lunar.api.world.pin;

import org.bukkit.Location;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry and persistence manager for spatial Pins and Pin Regions.
 */
public final class PinManager {

    private static volatile PinManager instance = new PinManager();

    public static PinManager get() {
        return instance;
    }

    public static void setInstance(PinManager manager) {
        if (manager != null) {
            instance = manager;
        }
    }

    private final Map<String, SinglePin> pins = new ConcurrentHashMap<>();
    private final Map<String, PinRegion> regions = new ConcurrentHashMap<>();
    private final Map<UUID, List<SinglePin>> wandSelections = new ConcurrentHashMap<>();

    public void addPin(SinglePin pin) {
        Objects.requireNonNull(pin, "Pin must not be null");
        pins.put(pin.name().toLowerCase(Locale.ROOT), pin);
    }

    public boolean removePin(String name) {
        if (name == null) return false;
        return pins.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public Optional<SinglePin> getPin(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(pins.get(name.toLowerCase(Locale.ROOT)));
    }

    public Map<String, SinglePin> allPins() {
        return Collections.unmodifiableMap(pins);
    }

    public PinRegion createRegion(String name, String world, List<String> pinNames, double minY, double maxY) {
        Objects.requireNonNull(name, "Region name must not be null");
        Objects.requireNonNull(world, "World must not be null");
        Objects.requireNonNull(pinNames, "Pin names must not be null");

        List<SinglePin> resolvedPins = new ArrayList<>();
        for (String pName : pinNames) {
            SinglePin pin = pins.get(pName.toLowerCase(Locale.ROOT));
            if (pin == null) {
                throw new IllegalArgumentException("Unknown pin referenced in region: " + pName);
            }
            resolvedPins.add(pin);
        }

        PinRegion region = new PinRegion(name, world, resolvedPins, minY, maxY);
        addRegion(region);
        return region;
    }

    public void addRegion(PinRegion region) {
        Objects.requireNonNull(region, "Region must not be null");
        regions.put(region.name().toLowerCase(Locale.ROOT), region);
    }

    public boolean removeRegion(String name) {
        if (name == null) return false;
        return regions.remove(name.toLowerCase(Locale.ROOT)) != null;
    }

    public Optional<PinRegion> getRegion(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(regions.get(name.toLowerCase(Locale.ROOT)));
    }

    public Map<String, PinRegion> allRegions() {
        return Collections.unmodifiableMap(regions);
    }

    public boolean isInsideRegion(String regionName, Location location) {
        if (regionName == null || location == null) return false;
        PinRegion region = regions.get(regionName.toLowerCase(Locale.ROOT));
        return region != null && region.contains(location);
    }

    public List<PinRegion> getRegionsContaining(Location location) {
        if (location == null) return List.of();
        List<PinRegion> matches = new ArrayList<>();
        for (PinRegion region : regions.values()) {
            if (region.contains(location)) {
                matches.add(region);
            }
        }
        return matches;
    }

    public List<SinglePin> getWandSelections(UUID playerId) {
        return wandSelections.computeIfAbsent(playerId, k -> new ArrayList<>());
    }

    public void clearWandSelections(UUID playerId) {
        wandSelections.remove(playerId);
    }

    /**
     * Loads pins and regions from a YAML file (e.g. regions.yml).
     */
    public synchronized void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;

        LoaderOptions options = new LoaderOptions();
        try (InputStream in = Files.newInputStream(file)) {
            Object raw = new Yaml(new SafeConstructor(options)).load(in);
            if (!(raw instanceof Map<?, ?> root)) return;

            // Load pins
            Object rawPins = root.get("pins");
            if (rawPins instanceof Map<?, ?> pinsMap) {
                for (Map.Entry<?, ?> entry : pinsMap.entrySet()) {
                    if (entry.getKey() instanceof String pName && entry.getValue() instanceof Map<?, ?> pData) {
                        String world = Objects.toString(pData.get("world"), "world");
                        double x = pData.get("x") instanceof Number nx ? nx.doubleValue() : 0.0;
                        double y = pData.get("y") instanceof Number ny ? ny.doubleValue() : 0.0;
                        double z = pData.get("z") instanceof Number nz ? nz.doubleValue() : 0.0;
                        addPin(new SinglePin(pName, world, x, y, z));
                    }
                }
            }

            // Load regions
            Object rawRegions = root.get("regions");
            if (rawRegions instanceof Map<?, ?> regionsMap) {
                for (Map.Entry<?, ?> entry : regionsMap.entrySet()) {
                    if (entry.getKey() instanceof String rName && entry.getValue() instanceof Map<?, ?> rData) {
                        String world = Objects.toString(rData.get("world"), "world");
                        double minY = rData.get("min_y") instanceof Number n ? n.doubleValue() : -64.0;
                        double maxY = rData.get("max_y") instanceof Number n ? n.doubleValue() : 320.0;
                        List<String> pinList = new ArrayList<>();
                        if (rData.get("pins") instanceof List<?> list) {
                            for (Object p : list) {
                                if (p != null) pinList.add(p.toString());
                            }
                        }
                        if (pinList.size() >= 3) {
                            try {
                                createRegion(rName, world, pinList, minY, maxY);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
    }

    /**
     * Saves all pins and regions to a YAML file.
     */
    public synchronized void save(Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }

        Map<String, Object> root = new LinkedHashMap<>();

        // 1. Serialize pins
        Map<String, Object> pinsMap = new LinkedHashMap<>();
        for (SinglePin pin : pins.values()) {
            Map<String, Object> pinData = new LinkedHashMap<>();
            pinData.put("world", pin.worldName());
            pinData.put("x", pin.x());
            pinData.put("y", pin.y());
            pinData.put("z", pin.z());
            pinsMap.put(pin.name(), pinData);
        }
        root.put("pins", pinsMap);

        // 2. Serialize regions
        Map<String, Object> regionsMap = new LinkedHashMap<>();
        for (PinRegion region : regions.values()) {
            Map<String, Object> regionData = new LinkedHashMap<>();
            regionData.put("world", region.worldName());
            regionData.put("min_y", region.minY());
            regionData.put("max_y", region.maxY());
            List<String> pinNames = region.pins().stream().map(SinglePin::name).toList();
            regionData.put("pins", pinNames);
            regionsMap.put(region.name(), regionData);
        }
        root.put("regions", regionsMap);

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        Yaml yaml = new Yaml(dumperOptions);

        try (Writer writer = Files.newBufferedWriter(file)) {
            yaml.dump(root, writer);
        }
    }
}
