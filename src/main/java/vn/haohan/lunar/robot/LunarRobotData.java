package vn.haohan.lunar.robot;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class LunarRobotData {
    private final UUID robotId;
    private UUID ownerUuid;
    private String ownerName;
    private String name = "Lunar Scout Quadruped";

    private double health = 60.0;
    private double maxHealth = 60.0;

    private int energy = 5000;
    private int maxEnergy = 5000;
    private String batteryType = "small"; // "none", "small", "medium", "large"

    private String module1Id = null;
    private double module1Efficiency = 100.0;

    private String module2Id = null;
    private double module2Efficiency = 100.0;

    private String module3Id = null;
    private double module3Efficiency = 100.0;

    private int totalSteps = 0;
    private double damageDealt = 0.0;
    private double damageTaken = 0.0;
    private int energySpent = 0;
    private long linkedTimestamp = System.currentTimeMillis();

    private vn.haohan.lunar.robot.ui.LunarDashboardTheme colorTheme = vn.haohan.lunar.robot.ui.LunarDashboardTheme.CYAN_WHITE;

    private RobotTask activeTask = RobotTask.IDLE;
    private boolean followOwner = true;
    private boolean isSitting = false;

    private String lastWorldName = null;
    private double lastX = 0;
    private double lastY = 0;
    private double lastZ = 0;

    public LunarRobotData(UUID robotId) {
        this.robotId = robotId;
    }

    public UUID getRobotId() {
        return robotId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public boolean isTamed() {
        return ownerUuid != null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.max(0.0, Math.min(maxHealth, health));
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
        this.health = Math.min(this.health, maxHealth);
    }

    public double getIntegrityPercentage() {
        if (maxHealth <= 0.0) return 0.0;
        return Math.max(0.0, Math.min(100.0, (health / maxHealth) * 100.0));
    }

    public String getFormattedIntegrity() {
        return (int) Math.round(getIntegrityPercentage()) + "%";
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = Math.max(0, Math.min(maxEnergy, energy));
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public void setMaxEnergy(int maxEnergy) {
        this.maxEnergy = maxEnergy;
        this.energy = Math.min(this.energy, maxEnergy);
    }

    public String getBatteryType() {
        return batteryType;
    }

    public void setBatteryType(String batteryType) {
        this.batteryType = batteryType;
        switch (batteryType) {
            case "large" -> {
                this.maxEnergy = 30000;
            }
            case "medium" -> {
                this.maxEnergy = 15000;
            }
            case "small" -> {
                this.maxEnergy = 5000;
            }
            default -> {
                this.batteryType = "none";
                this.maxEnergy = 0;
            }
        }
        this.energy = Math.min(this.energy, this.maxEnergy);
    }

    public String getModule1Id() {
        return module1Id;
    }

    public void setModule1Id(String module1Id) {
        this.module1Id = module1Id;
    }

    public double getModule1Efficiency() {
        return module1Efficiency;
    }

    public void setModule1Efficiency(double module1Efficiency) {
        this.module1Efficiency = Math.max(0.0, Math.min(100.0, module1Efficiency));
    }

    public String getModule2Id() {
        return module2Id;
    }

    public void setModule2Id(String module2Id) {
        this.module2Id = module2Id;
    }

    public double getModule2Efficiency() {
        return module2Efficiency;
    }

    public void setModule2Efficiency(double module2Efficiency) {
        this.module2Efficiency = Math.max(0.0, Math.min(100.0, module2Efficiency));
    }

    public String getModule3Id() {
        return module3Id;
    }

    public void setModule3Id(String module3Id) {
        this.module3Id = module3Id;
    }

    public double getModule3Efficiency() {
        return module3Efficiency;
    }

    public void setModule3Efficiency(double module3Efficiency) {
        this.module3Efficiency = Math.max(0.0, Math.min(100.0, module3Efficiency));
    }

    public String getModuleId(int slot) {
        return switch (slot) {
            case 0 -> module1Id;
            case 1 -> module2Id;
            case 2 -> module3Id;
            default -> null;
        };
    }

    public void setModuleId(int slot, String id) {
        switch (slot) {
            case 0 -> this.module1Id = id;
            case 1 -> this.module2Id = id;
            case 2 -> this.module3Id = id;
        }
    }

    public double getModuleEfficiency(int slot) {
        return switch (slot) {
            case 0 -> module1Efficiency;
            case 1 -> module2Efficiency;
            case 2 -> module3Efficiency;
            default -> 0.0;
        };
    }

    public void setModuleEfficiency(int slot, double eff) {
        switch (slot) {
            case 0 -> this.module1Efficiency = Math.max(0.0, Math.min(100.0, eff));
            case 1 -> this.module2Efficiency = Math.max(0.0, Math.min(100.0, eff));
            case 2 -> this.module3Efficiency = Math.max(0.0, Math.min(100.0, eff));
        }
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = Math.max(0, totalSteps);
    }

    public void addSteps(int steps) {
        if (steps > 0) this.totalSteps += steps;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public void setDamageDealt(double damageDealt) {
        this.damageDealt = Math.max(0.0, damageDealt);
    }

    public void addDamageDealt(double damage) {
        if (damage > 0) this.damageDealt += damage;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    public void setDamageTaken(double damageTaken) {
        this.damageTaken = Math.max(0.0, damageTaken);
    }

    public void addDamageTaken(double damage) {
        if (damage > 0) this.damageTaken += damage;
    }

    public int getEnergySpent() {
        return energySpent;
    }

    public void setEnergySpent(int energySpent) {
        this.energySpent = Math.max(0, energySpent);
    }

    public void addEnergySpent(int energy) {
        if (energy > 0) this.energySpent += energy;
    }

    public long getLinkedTimestamp() {
        return linkedTimestamp;
    }

    public void setLinkedTimestamp(long linkedTimestamp) {
        this.linkedTimestamp = linkedTimestamp;
    }

    public vn.haohan.lunar.robot.ui.LunarDashboardTheme getColorTheme() {
        return colorTheme != null ? colorTheme : vn.haohan.lunar.robot.ui.LunarDashboardTheme.CYAN_WHITE;
    }

    public void setColorTheme(vn.haohan.lunar.robot.ui.LunarDashboardTheme colorTheme) {
        this.colorTheme = colorTheme != null ? colorTheme : vn.haohan.lunar.robot.ui.LunarDashboardTheme.CYAN_WHITE;
    }

    public RobotTask getActiveTask() {
        return activeTask;
    }

    public void setActiveTask(RobotTask activeTask) {
        this.activeTask = activeTask != null ? activeTask : RobotTask.IDLE;
    }

    public boolean isFollowOwner() {
        return followOwner;
    }

    public void setFollowOwner(boolean followOwner) {
        this.followOwner = followOwner;
    }

    public boolean isSitting() {
        return isSitting;
    }

    public void setSitting(boolean sitting) {
        isSitting = sitting;
    }

    public boolean hasModule(String moduleTypeOrId) {
        if (moduleTypeOrId == null) return false;
        String m1 = module1Id != null ? module1Id.toLowerCase() : "";
        String m2 = module2Id != null ? module2Id.toLowerCase() : "";
        String m3 = module3Id != null ? module3Id.toLowerCase() : "";
        String target = moduleTypeOrId.toLowerCase();
        return m1.contains(target) || m2.contains(target) || m3.contains(target);
    }

    public double getModuleEfficiency(String moduleTypeOrId) {
        if (moduleTypeOrId == null) return 0.0;
        String m1 = module1Id != null ? module1Id.toLowerCase() : "";
        String m2 = module2Id != null ? module2Id.toLowerCase() : "";
        String m3 = module3Id != null ? module3Id.toLowerCase() : "";
        String target = moduleTypeOrId.toLowerCase();
        if (m1.contains(target)) return module1Efficiency;
        if (m2.contains(target)) return module2Efficiency;
        if (m3.contains(target)) return module3Efficiency;
        return 0.0;
    }

    public void degradeModule(String moduleTypeOrId, double amount) {
        if (moduleTypeOrId == null || amount <= 0) return;
        String m1 = module1Id != null ? module1Id.toLowerCase() : "";
        String m2 = module2Id != null ? module2Id.toLowerCase() : "";
        String m3 = module3Id != null ? module3Id.toLowerCase() : "";
        String target = moduleTypeOrId.toLowerCase();
        if (m1.contains(target)) {
            setModule1Efficiency(module1Efficiency - amount);
        } else if (m2.contains(target)) {
            setModule2Efficiency(module2Efficiency - amount);
        } else if (m3.contains(target)) {
            setModule3Efficiency(module3Efficiency - amount);
        }
    }

    public boolean consumeEnergy(int amount) {
        if (amount <= 0) return true;
        if (energy >= amount) {
            energy -= amount;
            energySpent += amount;
            return true;
        }
        return false;
    }

    public String getLastWorldName() {
        return lastWorldName;
    }

    public void setLastWorldName(String lastWorldName) {
        this.lastWorldName = lastWorldName;
    }

    public double getLastX() {
        return lastX;
    }

    public void setLastX(double lastX) {
        this.lastX = lastX;
    }

    public double getLastY() {
        return lastY;
    }

    public void setLastY(double lastY) {
        this.lastY = lastY;
    }

    public double getLastZ() {
        return lastZ;
    }

    public void setLastZ(double lastZ) {
        this.lastZ = lastZ;
    }

    public void saveTo(Entity entity, Plugin plugin) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING, robotId.toString());
        if (ownerUuid != null) {
            pdc.set(new NamespacedKey(plugin, "robot_owner"), PersistentDataType.STRING, ownerUuid.toString());
        } else {
            pdc.remove(new NamespacedKey(plugin, "robot_owner"));
        }
        if (ownerName != null) {
            pdc.set(new NamespacedKey(plugin, "robot_owner_name"), PersistentDataType.STRING, ownerName);
        }
        pdc.set(new NamespacedKey(plugin, "robot_name"), PersistentDataType.STRING, name);
        pdc.set(new NamespacedKey(plugin, "robot_health"), PersistentDataType.DOUBLE, health);
        pdc.set(new NamespacedKey(plugin, "robot_max_health"), PersistentDataType.DOUBLE, maxHealth);
        pdc.set(new NamespacedKey(plugin, "robot_energy"), PersistentDataType.INTEGER, energy);
        pdc.set(new NamespacedKey(plugin, "robot_max_energy"), PersistentDataType.INTEGER, maxEnergy);
        pdc.set(new NamespacedKey(plugin, "robot_battery_type"), PersistentDataType.STRING, batteryType != null ? batteryType : "none");

        if (module1Id != null) {
            pdc.set(new NamespacedKey(plugin, "robot_mod1_id"), PersistentDataType.STRING, module1Id);
            pdc.set(new NamespacedKey(plugin, "robot_mod1_eff"), PersistentDataType.DOUBLE, module1Efficiency);
        } else {
            pdc.remove(new NamespacedKey(plugin, "robot_mod1_id"));
            pdc.remove(new NamespacedKey(plugin, "robot_mod1_eff"));
        }

        if (module2Id != null) {
            pdc.set(new NamespacedKey(plugin, "robot_mod2_id"), PersistentDataType.STRING, module2Id);
            pdc.set(new NamespacedKey(plugin, "robot_mod2_eff"), PersistentDataType.DOUBLE, module2Efficiency);
        } else {
            pdc.remove(new NamespacedKey(plugin, "robot_mod2_id"));
            pdc.remove(new NamespacedKey(plugin, "robot_mod2_eff"));
        }

        if (module3Id != null) {
            pdc.set(new NamespacedKey(plugin, "robot_mod3_id"), PersistentDataType.STRING, module3Id);
            pdc.set(new NamespacedKey(plugin, "robot_mod3_eff"), PersistentDataType.DOUBLE, module3Efficiency);
        } else {
            pdc.remove(new NamespacedKey(plugin, "robot_mod3_id"));
            pdc.remove(new NamespacedKey(plugin, "robot_mod3_eff"));
        }

        pdc.set(new NamespacedKey(plugin, "robot_total_steps"), PersistentDataType.INTEGER, totalSteps);
        pdc.set(new NamespacedKey(plugin, "robot_dmg_dealt"), PersistentDataType.DOUBLE, damageDealt);
        pdc.set(new NamespacedKey(plugin, "robot_dmg_taken"), PersistentDataType.DOUBLE, damageTaken);
        pdc.set(new NamespacedKey(plugin, "robot_energy_spent"), PersistentDataType.INTEGER, energySpent);
        pdc.set(new NamespacedKey(plugin, "robot_linked_time"), PersistentDataType.LONG, linkedTimestamp);
        if (colorTheme != null) {
            pdc.set(new NamespacedKey(plugin, "robot_color_theme"), PersistentDataType.STRING, colorTheme.name());
        }

        pdc.set(new NamespacedKey(plugin, "robot_active_task"), PersistentDataType.STRING, activeTask.name());
        pdc.set(new NamespacedKey(plugin, "robot_follow_owner"), PersistentDataType.BYTE, (byte) (followOwner ? 1 : 0));
        pdc.set(new NamespacedKey(plugin, "robot_is_sitting"), PersistentDataType.BYTE, (byte) (isSitting ? 1 : 0));

        if (entity.getWorld() != null) {
            this.lastWorldName = entity.getWorld().getName();
            this.lastX = entity.getLocation().getX();
            this.lastY = entity.getLocation().getY();
            this.lastZ = entity.getLocation().getZ();

            pdc.set(new NamespacedKey(plugin, "robot_loc_world"), PersistentDataType.STRING, lastWorldName);
            pdc.set(new NamespacedKey(plugin, "robot_loc_x"), PersistentDataType.DOUBLE, lastX);
            pdc.set(new NamespacedKey(plugin, "robot_loc_y"), PersistentDataType.DOUBLE, lastY);
            pdc.set(new NamespacedKey(plugin, "robot_loc_z"), PersistentDataType.DOUBLE, lastZ);
        }
    }

    public static LunarRobotData loadFrom(Entity entity, Plugin plugin) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        String idStr = pdc.get(new NamespacedKey(plugin, "robot_id"), PersistentDataType.STRING);
        UUID id = idStr != null ? UUID.fromString(idStr) : entity.getUniqueId();

        LunarRobotData data = new LunarRobotData(id);
        String ownerStr = pdc.get(new NamespacedKey(plugin, "robot_owner"), PersistentDataType.STRING);
        if (ownerStr != null && !ownerStr.isBlank()) {
            try {
                data.setOwnerUuid(UUID.fromString(ownerStr));
            } catch (Exception ignored) {}
        }
        data.setOwnerName(pdc.get(new NamespacedKey(plugin, "robot_owner_name"), PersistentDataType.STRING));

        String n = pdc.get(new NamespacedKey(plugin, "robot_name"), PersistentDataType.STRING);
        if (n != null) data.setName(n);

        Double mh = pdc.get(new NamespacedKey(plugin, "robot_max_health"), PersistentDataType.DOUBLE);
        if (mh != null) data.setMaxHealth(mh);

        Double h = pdc.get(new NamespacedKey(plugin, "robot_health"), PersistentDataType.DOUBLE);
        if (h != null) data.setHealth(h);

        String bt = pdc.get(new NamespacedKey(plugin, "robot_battery_type"), PersistentDataType.STRING);
        if (bt != null) data.setBatteryType(bt);

        Integer me = pdc.get(new NamespacedKey(plugin, "robot_max_energy"), PersistentDataType.INTEGER);
        if (me != null) data.setMaxEnergy(me);

        Integer e = pdc.get(new NamespacedKey(plugin, "robot_energy"), PersistentDataType.INTEGER);
        if (e != null) data.setEnergy(e);

        data.setModule1Id(pdc.get(new NamespacedKey(plugin, "robot_mod1_id"), PersistentDataType.STRING));
        Double eff1 = pdc.get(new NamespacedKey(plugin, "robot_mod1_eff"), PersistentDataType.DOUBLE);
        if (eff1 != null) data.setModule1Efficiency(eff1);

        data.setModule2Id(pdc.get(new NamespacedKey(plugin, "robot_mod2_id"), PersistentDataType.STRING));
        Double eff2 = pdc.get(new NamespacedKey(plugin, "robot_mod2_eff"), PersistentDataType.DOUBLE);
        if (eff2 != null) data.setModule2Efficiency(eff2);

        data.setModule3Id(pdc.get(new NamespacedKey(plugin, "robot_mod3_id"), PersistentDataType.STRING));
        Double eff3 = pdc.get(new NamespacedKey(plugin, "robot_mod3_eff"), PersistentDataType.DOUBLE);
        if (eff3 != null) data.setModule3Efficiency(eff3);

        Integer steps = pdc.get(new NamespacedKey(plugin, "robot_total_steps"), PersistentDataType.INTEGER);
        if (steps != null) data.setTotalSteps(steps);

        Double dealt = pdc.get(new NamespacedKey(plugin, "robot_dmg_dealt"), PersistentDataType.DOUBLE);
        if (dealt != null) data.setDamageDealt(dealt);

        Double taken = pdc.get(new NamespacedKey(plugin, "robot_dmg_taken"), PersistentDataType.DOUBLE);
        if (taken != null) data.setDamageTaken(taken);

        Integer spent = pdc.get(new NamespacedKey(plugin, "robot_energy_spent"), PersistentDataType.INTEGER);
        if (spent != null) data.setEnergySpent(spent);

        Long linked = pdc.get(new NamespacedKey(plugin, "robot_linked_time"), PersistentDataType.LONG);
        if (linked != null) data.setLinkedTimestamp(linked);

        String themeStr = pdc.get(new NamespacedKey(plugin, "robot_color_theme"), PersistentDataType.STRING);
        if (themeStr != null) {
            try {
                data.setColorTheme(vn.haohan.lunar.robot.ui.LunarDashboardTheme.valueOf(themeStr));
            } catch (Exception ignored) {}
        }

        String taskStr = pdc.get(new NamespacedKey(plugin, "robot_active_task"), PersistentDataType.STRING);
        if (taskStr != null) {
            try {
                data.setActiveTask(RobotTask.valueOf(taskStr));
            } catch (Exception ignored) {}
        }

        Byte follow = pdc.get(new NamespacedKey(plugin, "robot_follow_owner"), PersistentDataType.BYTE);
        data.setFollowOwner(follow == null || follow == 1);

        Byte sitting = pdc.get(new NamespacedKey(plugin, "robot_is_sitting"), PersistentDataType.BYTE);
        data.setSitting(sitting != null && sitting == 1);

        String worldName = pdc.get(new NamespacedKey(plugin, "robot_loc_world"), PersistentDataType.STRING);
        if (worldName != null) data.setLastWorldName(worldName);

        Double lx = pdc.get(new NamespacedKey(plugin, "robot_loc_x"), PersistentDataType.DOUBLE);
        if (lx != null) data.setLastX(lx);
        Double ly = pdc.get(new NamespacedKey(plugin, "robot_loc_y"), PersistentDataType.DOUBLE);
        if (ly != null) data.setLastY(ly);
        Double lz = pdc.get(new NamespacedKey(plugin, "robot_loc_z"), PersistentDataType.DOUBLE);
        if (lz != null) data.setLastZ(lz);

        return data;
    }
}
