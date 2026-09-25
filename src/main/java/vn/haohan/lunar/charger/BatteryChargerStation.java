package vn.haohan.lunar.charger;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BatteryChargerStation {
    private final Location location;
    private UUID displayEntityUuid;
    private UUID ownerUuid;
    private ItemStack batteryItem;
    private int fuelBuffer;
    private float yaw;

    public BatteryChargerStation(Location location, UUID displayEntityUuid, UUID ownerUuid, float yaw) {
        this.location = location;
        this.displayEntityUuid = displayEntityUuid;
        this.ownerUuid = ownerUuid;
        this.yaw = yaw;
        this.batteryItem = null;
        this.fuelBuffer = 0;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getDisplayEntityUuid() {
        return displayEntityUuid;
    }

    public void setDisplayEntityUuid(UUID displayEntityUuid) {
        this.displayEntityUuid = displayEntityUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public ItemStack getBatteryItem() {
        return batteryItem;
    }

    public void setBatteryItem(ItemStack batteryItem) {
        this.batteryItem = batteryItem;
    }

    public int getFuelBuffer() {
        return fuelBuffer;
    }

    public void setFuelBuffer(int fuelBuffer) {
        this.fuelBuffer = Math.max(0, fuelBuffer);
    }

    public void addFuelBuffer(int amount) {
        if (amount > 0) {
            this.fuelBuffer += amount;
        }
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public String getKey() {
        if (location == null || location.getWorld() == null) return "unknown";
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
