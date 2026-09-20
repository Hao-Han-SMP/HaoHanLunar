package vn.haohan.lunar.api.system.combat.reward;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Encapsulates the victory statistics, combat duration, and top damage leaderboard for a defeated boss.
 */
public record BossVictorySummary(
        String bossName,
        long combatDurationMillis,
        List<DamagerRank> topDamagers
) {

    public BossVictorySummary {
        bossName = bossName != null ? bossName : "LUNAR BOSS";
        topDamagers = topDamagers != null ? List.copyOf(topDamagers) : List.of();
    }

    public record DamagerRank(
            int rank,
            UUID playerId,
            String playerName,
            double damage,
            double percentage
    ) {
        public DamagerRank {
            Objects.requireNonNull(playerId, "Player UUID must not be null");
            playerName = playerName != null ? playerName : playerId.toString().substring(0, 8);
        }
    }

    public String formatCombatDuration() {
        long totalSeconds = Math.max(0L, combatDurationMillis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d phút %02d giây", minutes, seconds);
    }

    /**
     * Renders the victory banner box matching the MythicMobs / MMO specification.
     */
    public String renderAsciiBox() {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════╗\n");
        sb.append(String.format(Locale.ROOT, "║      CHIẾN THẮNG: %-19s║\n", bossName.length() > 19 ? bossName.substring(0, 19) : bossName));
        sb.append(String.format(Locale.ROOT, "║ Thời gian giao tranh: %-15s║\n", formatCombatDuration()));
        for (DamagerRank r : topDamagers) {
            String badge = switch (r.rank()) {
                case 1 -> "👑 MVP Sát Thương:";
                case 2 -> "🥈 Hạng 2:";
                case 3 -> "🥉 Hạng 3:";
                default -> "Hạng " + r.rank() + ":";
            };
            String line = String.format(Locale.ROOT, "%s %s (%.1f%%)", badge, r.playerName(), r.percentage());
            sb.append(String.format(Locale.ROOT, "║ %-37s║\n", line.length() > 37 ? line.substring(0, 37) : line));
        }
        sb.append("╚══════════════════════════════════════╝");
        return sb.toString();
    }
}
