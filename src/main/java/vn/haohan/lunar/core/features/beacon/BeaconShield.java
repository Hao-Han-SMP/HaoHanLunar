package vn.haohan.lunar.core.features.beacon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import vn.haohan.lunar.HaoHanLunarPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the runtime state of a single beacon protection field.
 */
public final class BeaconShield {

    private final Location beacon;
    private double radius;
    private double collapseStartRadius;
    private int collapseTick;
    private boolean collapsing;
    private final List<ItemDisplay> displays = new ArrayList<>();
    private final List<Location> groundContacts = new ArrayList<>();

    public BeaconShield(Location beacon) {
        this.beacon = beacon;
    }

    public Location getBeacon() {
        return beacon;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public double getCollapseStartRadius() {
        return collapseStartRadius;
    }

    public int getCollapseTick() {
        return collapseTick;
    }

    public void incrementCollapseTick() {
        this.collapseTick++;
    }

    public boolean isCollapsing() {
        return collapsing;
    }

    public void beginCollapse() {
        if (collapsing) return;
        this.collapseStartRadius = radius;
        this.collapseTick = 0;
        this.collapsing = true;
        this.groundContacts.clear();
    }

    public List<ItemDisplay> getDisplays() {
        return displays;
    }

    public List<Location> getGroundContacts() {
        return groundContacts;
    }

    public boolean isInShield(Location location) {
        if (location == null || collapsing) return false;
        World shieldWorld = beacon.getWorld();
        if (shieldWorld == null || shieldWorld != location.getWorld()) return false;

        double dx = location.getX() - beacon.getX();
        double dy = location.getY() - beacon.getY();
        double dz = location.getZ() - beacon.getZ();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    public boolean isValid() {
        World world = beacon.getWorld();
        if (!HaoHanLunarPlugin.isLunarWorld(world)) {
            return false;
        }
        Block block = beacon.getBlock();
        return block.getType().name().equals("BEACON")
                && world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }

    @Deprecated
    public boolean isValid(String lunarWorldKey) {
        return isValid();
    }
}
