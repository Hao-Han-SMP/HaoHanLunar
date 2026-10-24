package vn.haohan.lunar.charger;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BatteryChargerStation {

    public enum ChargerState {
        RED("haohan:robot_charger_red"),
        BLUE("haohan:robot_charger"),
        YELLOW("haohan:robot_charger_yellow"),
        CYAN("haohan:robot_charger_cyan");

        private final String customItemId;

        ChargerState(String customItemId) {
            this.customItemId = customItemId;
        }

        public String getCustomItemId() {
            return customItemId;
        }
    }

    private final Location location;
    private UUID displayEntityUuid;
    private UUID ownerUuid;
    private ItemStack batteryItem;
    private ItemStack fuelItem;
    private int fuelBuffer;
    private float yaw;
    private double cachedMultiplier = 1.0;
    private int cachedChargeRate = 450;

    private ChargerState currentState = ChargerState.RED;
    private UUID dockedRobotUuid = null;
    private int cyanDisplayTicks = 0;

    public BatteryChargerStation(Location location, UUID displayEntityUuid, UUID ownerUuid, float yaw) {
        this.location = location;
        this.displayEntityUuid = displayEntityUuid;
        this.ownerUuid = ownerUuid;
        this.yaw = yaw;
        this.batteryItem = null;
        this.fuelItem = null;
        this.fuelBuffer = 0;
        this.cachedMultiplier = 1.0;
        this.cachedChargeRate = 450;
        this.currentState = ChargerState.RED;
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

    public ItemStack getFuelItem() {
        return fuelItem;
    }

    public void setFuelItem(ItemStack fuelItem) {
        this.fuelItem = fuelItem;
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

    public double getCachedMultiplier() {
        return cachedMultiplier;
    }

    public void setCachedMultiplier(double cachedMultiplier) {
        this.cachedMultiplier = cachedMultiplier;
    }

    public int getCachedChargeRate() {
        return cachedChargeRate;
    }

    public void setCachedChargeRate(int cachedChargeRate) {
        this.cachedChargeRate = cachedChargeRate;
    }

    public ChargerState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(ChargerState currentState) {
        this.currentState = currentState;
    }

    public UUID getDockedRobotUuid() {
        return dockedRobotUuid;
    }

    public void setDockedRobotUuid(UUID dockedRobotUuid) {
        this.dockedRobotUuid = dockedRobotUuid;
    }

    public int getCyanDisplayTicks() {
        return cyanDisplayTicks;
    }

    public void setCyanDisplayTicks(int cyanDisplayTicks) {
        this.cyanDisplayTicks = cyanDisplayTicks;
    }

    public String getKey() {
        if (location == null || location.getWorld() == null) return "unknown";
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
