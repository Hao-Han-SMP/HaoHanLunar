package vn.haohan.lunar.mechanics.boss.warden.ui;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BossBarShatterTask extends BukkitRunnable {
    private final BossBar bossBar;
    private final Set<UUID> viewers;
    private int frame = 0;

    public BossBarShatterTask(BossBar bossBar, Set<UUID> viewers) {
        this.bossBar = bossBar;
        this.viewers = new HashSet<>(viewers);
    }

    @Override
    public void run() {
        if (frame >= 12) {
            for (UUID viewerId : viewers) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null) {
                    p.hideBossBar(bossBar);
                }
            }
            cancel();
            return;
        }

        if (frame == 0) {
            for (UUID viewerId : viewers) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.2f, 1.4f);
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, 1.2f);
                }
            }
        } else if (frame == 3) {
            for (UUID viewerId : viewers) {
                Player p = Bukkit.getPlayer(viewerId);
                if (p != null) {
                    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.6f, 0.8f);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.6f);
                }
            }
        }

        bossBar.name(WardenBossBar.buildBossBarShatter(frame));
        frame++;
    }
}
