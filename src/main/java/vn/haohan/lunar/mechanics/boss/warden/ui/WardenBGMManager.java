package vn.haohan.lunar.mechanics.boss.warden.ui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import vn.haohan.lunar.HaoHanLunarPlugin;
import vn.haohan.lunar.mechanics.boss.warden.LunarWardenMechanic;
import vn.haohan.lunar.mechanics.boss.warden.WardenState;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WardenBGMManager implements Listener {

    private static final String[] CANDIDATE_BGM_KEYS = {
            "haohan:boss.murasama_1",
            "haohan:boss.murasama_2",
            "haohan:boss.raiden",
            "haohan:boss.yamato",
            "haohan:boss.bgm",
            "haohan:boss.music",
    };

    // Tracks which players currently have active BGM playing and the associated boss UUID
    private static final Map<UUID, UUID> activeBGMListeners = new ConcurrentHashMap<>();

    private final HaoHanLunarPlugin plugin;
    private final LunarWardenMechanic mechanic;

    public WardenBGMManager(HaoHanLunarPlugin plugin, LunarWardenMechanic mechanic) {
        this.plugin = plugin;
        this.mechanic = mechanic;
    }

    public static void updatePlayerBGM(IronGolem golem, WardenState state, Player player) {
        if (player == null || !player.isValid() || player.isDead()) {
            if (player != null) {
                stopAllBGM(player);
            }
            return;
        }

        UUID playerUUID = player.getUniqueId();
        UUID bossUUID = golem.getUniqueId();

        // If player is already actively listening to this boss's BGM, do NOT play again (prevents overlap)
        if (activeBGMListeners.containsKey(playerUUID) && bossUUID.equals(activeBGMListeners.get(playerUUID))) {
            return;
        }

        // Before starting new BGM, forcefully clear any previous overlapping sound instances
        stopAllBGM(player);

        // Play the primary BGM candidate
        String selectedBgm = "haohan:boss.murasama_1";
        try {
            player.playSound(player.getLocation(), selectedBgm, SoundCategory.RECORDS, 1.0f, 1.0f);
            activeBGMListeners.put(playerUUID, bossUUID);
        } catch (Throwable ignored) {}
    }

    public static void stopPlayerBGM(Player player) {
        if (player == null) return;
        activeBGMListeners.remove(player.getUniqueId());
        stopAllBGM(player);
    }

    public static void stopAllBGM(Player player) {
        if (player == null) return;
        try {
            for (String soundKey : CANDIDATE_BGM_KEYS) {
                player.stopSound(soundKey, SoundCategory.RECORDS);
                player.stopSound(soundKey, SoundCategory.MUSIC);
                player.stopSound(soundKey, SoundCategory.HOSTILE);
                player.stopSound(soundKey);
            }
            player.stopSound(SoundCategory.RECORDS);
            player.stopSound(SoundCategory.MUSIC);
        } catch (Throwable ignored) {}
    }

    public static void stopBGMForBoss(UUID bossUUID) {
        if (bossUUID == null) return;
        activeBGMListeners.entrySet().removeIf(entry -> {
            if (bossUUID.equals(entry.getValue())) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null) {
                    stopAllBGM(p);
                }
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        activeBGMListeners.remove(player.getUniqueId());
        stopAllBGM(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        Player player = e.getPlayer();
        activeBGMListeners.remove(player.getUniqueId());
        // Clean up any residual audio right at respawn
        stopAllBGM(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        activeBGMListeners.remove(player.getUniqueId());
        stopAllBGM(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        Player player = e.getPlayer();
        if (e.getFrom().getWorld() != e.getTo().getWorld() || e.getFrom().distanceSquared(e.getTo()) > 100 * 100) {
            activeBGMListeners.remove(player.getUniqueId());
            stopAllBGM(player);
        }
    }
}
