package vn.haohan.lunar.core.system.data;

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

    private static volatile Keys cachedKeys;

    private record Keys(
        NamespacedKey oxygen,
        NamespacedKey oxygenDmg,
        NamespacedKey rbRegen,
        NamespacedKey ssRegen,
        NamespacedKey tankO2,
        NamespacedKey tankTier,
        NamespacedKey tankCharge,
        NamespacedKey tankActive
    ) {
        static Keys of(Plugin plugin) {
            return new Keys(
                new NamespacedKey(plugin, "oxygen"),
                new NamespacedKey(plugin, "oxygen_dmg"),
                new NamespacedKey(plugin, "rb_regen"),
                new NamespacedKey(plugin, "ss_regen"),
                new NamespacedKey(plugin, "tank_o2"),
                new NamespacedKey(plugin, "tank_tier"),
                new NamespacedKey(plugin, "tank_charge"),
                new NamespacedKey(plugin, "tank_active")
            );
        }
    }

    private static Keys getKeys(Plugin plugin) {
        Keys k = cachedKeys;
        if (k == null) {
            synchronized (PlayerLunarData.class) {
                k = cachedKeys;
                if (k == null) {
                    k = Keys.of(plugin);
                    cachedKeys = k;
                }
            }
        }
        return k;
    }

    public PlayerLunarData(Player player) {
        this.player = player;
    }

    public Player getPlayer() { return player; }
    public int getOxygen() { return oxygen; }

    public void setOxygen(int oxygen) {
        this.oxygen = Math.clamp(oxygen, 0, 600);
    }
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
        Keys k = getKeys(plugin);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        oxygen = pdc.getOrDefault(k.oxygen(), PersistentDataType.INTEGER, 600);
        oxygenDmg = pdc.getOrDefault(k.oxygenDmg(), PersistentDataType.INTEGER, 0);
        rbRegen = pdc.getOrDefault(k.rbRegen(), PersistentDataType.INTEGER, 0);
        ssRegen = pdc.getOrDefault(k.ssRegen(), PersistentDataType.INTEGER, 0);
        tankO2 = pdc.getOrDefault(k.tankO2(), PersistentDataType.INTEGER, 0);
        tankTier = pdc.getOrDefault(k.tankTier(), PersistentDataType.INTEGER, 0);
        tankCharge = pdc.getOrDefault(k.tankCharge(), PersistentDataType.INTEGER, 0);
        
        Byte active = pdc.get(k.tankActive(), PersistentDataType.BYTE);
        tankActive = active != null && active == 1;
    }

    public void save(Plugin plugin) {
        Keys k = getKeys(plugin);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(k.oxygen(), PersistentDataType.INTEGER, oxygen);
        pdc.set(k.oxygenDmg(), PersistentDataType.INTEGER, oxygenDmg);
        pdc.set(k.rbRegen(), PersistentDataType.INTEGER, rbRegen);
        pdc.set(k.ssRegen(), PersistentDataType.INTEGER, ssRegen);
        pdc.set(k.tankO2(), PersistentDataType.INTEGER, tankO2);
        pdc.set(k.tankTier(), PersistentDataType.INTEGER, tankTier);
        pdc.set(k.tankCharge(), PersistentDataType.INTEGER, tankCharge);
        pdc.set(k.tankActive(), PersistentDataType.BYTE, (byte) (tankActive ? 1 : 0));
    }
}
