package vn.haohan.lunar.mechanics.boss.warden.ai;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.scheduler.BukkitRunnable;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;
import vn.haohan.lunar.mechanics.boss.warden.ui.BossBarShatterTask;

import java.util.Random;

public class WardenAITask extends BukkitRunnable {
    private final HaoHanLunarPlugin plugin;
    private final LunarWardenMechanic mechanic;
    private final Random random = new Random();

    public WardenAITask(HaoHanLunarPlugin plugin, LunarWardenMechanic mechanic) {
        this.plugin = plugin;
        this.mechanic = mechanic;
    }

    @Override
    public void run() {
        mechanic.getBossStates().keySet().removeIf(uuid -> {
            Entity entity = Bukkit.getEntity(uuid);
            WardenState state = mechanic.getBossStates().get(uuid);
            if (entity == null || entity.isDead() || !(entity instanceof IronGolem golem)) {
                if (state != null) {
                    if (state.bossBar != null && !state.activeBossBarViewers.isEmpty()) {
                        new BossBarShatterTask(state.bossBar, state.activeBossBarViewers).runTaskTimer(plugin, 1L, 2L);
                        state.activeBossBarViewers.clear();
                    }
                    state.cleanup();
                }
                return true;
            }
            try {
                WardenAIController.handleBossAI(plugin, golem, state, random);
            } catch (Exception e) {
                plugin.getLogger().severe("Error in LunarWarden AI: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        });
    }
}
