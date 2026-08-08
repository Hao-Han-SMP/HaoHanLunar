package vn.haohan.lunar.data;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class PlayerLunarData {
    private final Player player;
    
    private int oxygen = 600;
    private int oxygenDmg = 0;
    private int rbRegen = 0;
    private int ssRegen = 0;
    private int tankO2 = 0;
    private int tankTier = 0;
    private int tankCharge = 0;
    private boolean tankActive = false;

    public PlayerLunarData(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }
    public int getOxygen() { return oxygen; }
    public void setOxygen(int oxygen) { this.oxygen = Math.max(0, Math.min(600, oxygen)); }
    public int getOxygenDmg() { return oxygenDmg; }
    public void setOxygenDmg(int oxygenDmg) { this.oxygenDmg = oxygenDmg; }
    public int getRbRegen() { return rbRegen; }
    public void setRbRegen(int rbRegen) { this.rbRegen = rbRegen; }
    public int getSsRegen() { return ssRegen; }
    public void setSsRegen(int ssRegen) { this.ssRegen = ssRegen; }
    public int getTankO2() { return tankO2; }
    public void setTankO2(int tankO2) { this.tankO2 = tankO2; }
    public int getTankTier() { return tankTier; }
    public void setTankTier(int tankTier) { this.tankTier = tankTier; }
    public int getTankCharge() { return tankCharge; }
    public void setTankCharge(int tankCharge) { this.tankCharge = tankCharge; }
    public boolean isTankActive() { return tankActive; }
    public void setTankActive(boolean tankActive) { this.tankActive = tankActive; }

    public void load(Plugin plugin) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        oxygen = pdc.getOrDefault(new NamespacedKey(plugin, "oxygen"), PersistentDataType.INTEGER, 600);
        oxygenDmg = pdc.getOrDefault(new NamespacedKey(plugin, "oxygen_dmg"), PersistentDataType.INTEGER, 0);
        rbRegen = pdc.getOrDefault(new NamespacedKey(plugin, "rb_regen"), PersistentDataType.INTEGER, 0);
        ssRegen = pdc.getOrDefault(new NamespacedKey(plugin, "ss_regen"), PersistentDataType.INTEGER, 0);
        tankO2 = pdc.getOrDefault(new NamespacedKey(plugin, "tank_o2"), PersistentDataType.INTEGER, 0);
        tankTier = pdc.getOrDefault(new NamespacedKey(plugin, "tank_tier"), PersistentDataType.INTEGER, 0);
        tankCharge = pdc.getOrDefault(new NamespacedKey(plugin, "tank_charge"), PersistentDataType.INTEGER, 0);
        
        Byte active = pdc.get(new NamespacedKey(plugin, "tank_active"), PersistentDataType.BYTE);
        tankActive = active != null && active == 1;
    }

    public void save(Plugin plugin) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "oxygen"), PersistentDataType.INTEGER, oxygen);
        pdc.set(new NamespacedKey(plugin, "oxygen_dmg"), PersistentDataType.INTEGER, oxygenDmg);
        pdc.set(new NamespacedKey(plugin, "rb_regen"), PersistentDataType.INTEGER, rbRegen);
        pdc.set(new NamespacedKey(plugin, "ss_regen"), PersistentDataType.INTEGER, ssRegen);
        pdc.set(new NamespacedKey(plugin, "tank_o2"), PersistentDataType.INTEGER, tankO2);
        pdc.set(new NamespacedKey(plugin, "tank_tier"), PersistentDataType.INTEGER, tankTier);
        pdc.set(new NamespacedKey(plugin, "tank_charge"), PersistentDataType.INTEGER, tankCharge);
        pdc.set(new NamespacedKey(plugin, "tank_active"), PersistentDataType.BYTE, (byte) (tankActive ? 1 : 0));
    }
}
