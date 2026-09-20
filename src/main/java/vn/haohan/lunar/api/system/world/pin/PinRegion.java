package vn.haohan.lunar.api.world.pin;

import org.bukkit.Location;

import java.util.List;
import java.util.Objects;

/**
 * A 3D spatial polygonal region defined by 3 or more SinglePin vertices and vertical bounds.
 */
public final class PinRegion {

    private final String name;
    private final String worldName;
    private final List<SinglePin> pins;
    private final double minY;
    private final double maxY;
    private final double minX;
    private final double maxX;
    private final double minZ;
    private final double maxZ;

    public PinRegion(String name, String worldName, List<SinglePin> pins, double minY, double maxY) {
        this.name = Objects.requireNonNull(name, "Region name must not be null");
        this.worldName = Objects.requireNonNull(worldName, "World name must not be null");
        Objects.requireNonNull(pins, "Pins list must not be null");
        if (pins.size() < 3) {
            throw new IllegalArgumentException("A PinRegion must have at least 3 pins, but got " + pins.size());
        }
        this.pins = List.copyOf(pins);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);

        double calcMinX = Double.MAX_VALUE;
        double calcMaxX = -Double.MAX_VALUE;
        double calcMinZ = Double.MAX_VALUE;
        double calcMaxZ = -Double.MAX_VALUE;

        for (SinglePin pin : this.pins) {
            if (pin.x() < calcMinX) calcMinX = pin.x();
            if (pin.x() > calcMaxX) calcMaxX = pin.x();
            if (pin.z() < calcMinZ) calcMinZ = pin.z();
            if (pin.z() > calcMaxZ) calcMaxZ = pin.z();
        }

        this.minX = calcMinX;
        this.maxX = calcMaxX;
        this.minZ = calcMinZ;
        this.maxZ = calcMaxZ;
    }

    public String name() {
        return name;
    }

    public String worldName() {
        return worldName;
    }

    public List<SinglePin> pins() {
        return pins;
    }

    public double minY() {
        return minY;
    }

    public double maxY() {
        return maxY;
    }

    public double minX() {
        return minX;
    }

    public double maxX() {
        return maxX;
    }

    public double minZ() {
        return minZ;
    }

    public double maxZ() {
        return maxZ;
    }

    /**
     * Checks whether a given Bukkit Location is inside this 3D polygonal region.
     */
    public boolean contains(Location location) {
        if (location == null) return false;
        if (location.getWorld() != null && !location.getWorld().getName().equalsIgnoreCase(worldName)) {
            return false;
        }
        return contains(location.getX(), location.getY(), location.getZ());
    }

    /**
     * Tests if a 3D point (x, y, z) lies within this polyhedral region.
     * Uses bounding box check followed by ray-casting point-in-polygon algorithm.
     */
    public boolean contains(double x, double y, double z) {
        // 1. Vertical check
        if (y < minY || y > maxY) {
            return false;
        }

        // 2. Bounding box check
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return false;
        }

        // 3. Ray-casting point-in-polygon
        boolean inside = false;
        int n = pins.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = pins.get(i).x(), zi = pins.get(i).z();
            double xj = pins.get(j).x(), zj = pins.get(j).z();

            boolean intersect = ((zi > z) != (zj > z))
                    && (x < (xj - xi) * (z - zi) / (zj - zi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }
}
