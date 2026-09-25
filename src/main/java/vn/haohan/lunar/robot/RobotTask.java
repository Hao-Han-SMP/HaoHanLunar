package vn.haohan.lunar.robot;

public enum RobotTask {
    IDLE("Theo dõi / Nghỉ", "§7", "haohan:robot_module_none"),
    ORE_SCAN("Dò Quặng", "§6", "haohan:robot_module_ore_scan"),
    COMBAT("Chiến Đấu", "§c", "haohan:robot_module_combat"),
    SPEED("Tốc Hành", "§b", "haohan:robot_module_speed"),
    THRUST("Đẩy Phản Lực", "§e", "haohan:robot_module_thrust");

    private final String displayName;
    private final String colorCode;
    private final String requiredModuleId;

    RobotTask(String displayName, String colorCode, String requiredModuleId) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.requiredModuleId = requiredModuleId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getRequiredModuleId() {
        return requiredModuleId;
    }

    public String getFormattedName() {
        return colorCode + displayName;
    }
}
