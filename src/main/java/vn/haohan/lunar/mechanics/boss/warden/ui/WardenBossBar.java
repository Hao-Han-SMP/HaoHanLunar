package vn.haohan.lunar.mechanics.boss.warden.ui;

import vn.haohan.lunar.util.MathUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import vn.haohan.lunar.mechanics.boss.warden.WardenConstants;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class WardenBossBar {
    private WardenBossBar() {}

    public static void updateBossBar(IronGolem golem, WardenState state) {
        if (state.bossBar == null) {
            state.bossBar = BossBar.bossBar(
                    buildBossBarTitle(golem.getHealth(), WardenConstants.BOSS_MAX_HEALTH),
                    0.0f,
                    BossBar.Color.WHITE,
                    BossBar.Overlay.PROGRESS
            );
        }

        double maxHp = WardenConstants.BOSS_MAX_HEALTH;
        if (golem.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHp = golem.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        double currentHp = MathUtil.clamp(golem.getHealth(), 0.0, maxHp);

        state.animTick++;
        if (state.animTick % 2 == 0) {
            state.bossBar.progress(0.0f);
            state.bossBar.name(buildBossBarTitle(currentHp, maxHp, state.animTick));
        }

        Location golemLoc = golem.getLocation();
        World world = golemLoc.getWorld();
        if (world == null) return;

        Set<UUID> nearbyViewerUUIDs = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.isValid() && !p.isDead() && p.getLocation().distanceSquared(golemLoc) <= 60.0 * 60.0) {
                nearbyViewerUUIDs.add(p.getUniqueId());
                if (!state.activeBossBarViewers.contains(p.getUniqueId())) {
                    p.showBossBar(state.bossBar);
                }
                // Manage BGM cleanly without overlap
                WardenBGMManager.updatePlayerBGM(golem, state, p);
            }
        }

        state.activeBossBarViewers.removeIf(uuid -> {
            if (!nearbyViewerUUIDs.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.hideBossBar(state.bossBar);
                    WardenBGMManager.stopPlayerBGM(p);
                }
                return true;
            }
            return false;
        });
        state.activeBossBarViewers.addAll(nearbyViewerUUIDs);
    }

    public static Component buildBossBarTitle(double currentHp, double maxHp) {
        return buildBossBarTitle(currentHp, maxHp, 0);
    }

    public static Component buildBossBarTitle(double currentHp, double maxHp, long tick) {
        float ratio = MathUtil.clamp01((float) (currentHp / maxHp));
        int pct = Math.round(ratio * 100.0f);
        pct = MathUtil.clamp(pct, 0, 100);

        int animFrame = (int) ((tick / 2) % 8);
        char fillChar = (char) (0xE400 + (pct * 8) + animFrame);

        String glyphs = "\ue300\ue3f0" + fillChar;

        return Component.text(glyphs).font(Key.key("haohan", "bossbar"));
    }

    public static Component buildBossBarShatter(int frame) {
        int clamped = MathUtil.clamp(frame, 0, 11);
        char shatterChar = (char) (0xE800 + clamped);
        return Component.text(String.valueOf(shatterChar)).font(Key.key("haohan", "bossbar"));
    }
}
