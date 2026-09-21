package vn.haohan.lunar.api.system.combat;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import vn.haohan.lunar.api.system.combat.reward.BossVictorySummary;
import vn.haohan.lunar.api.system.combat.reward.RewardTier;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Tracks player damage dealt to ActiveLunarMobs and generates immutable damage snapshots on death.
 * Includes pet damage attribution, real-time leaderboard, and victory summaries.
 */
public final class DamageTracker implements Listener {

    private final LunarMobManager mobManager;
    private final Map<UUID, Map<UUID, Double>> mobDamageRecords = new ConcurrentHashMap<>();
    private final Map<UUID, DamageContributionSnapshot> finalSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobCombatStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobCombatEnd = new ConcurrentHashMap<>();

    public DamageTracker(LunarMobManager mobManager) {
        this.mobManager = mobManager;
    }

    public DamageTracker() {
        this(null);
    }

    /**
     * Records damage dealt by a player to a mob.
     */
    public void recordDamage(UUID mobId, UUID playerId, double damage) {
        if (mobId == null || playerId == null || !Double.isFinite(damage) || damage <= 0.0) {
            return;
        }
        long now = System.currentTimeMillis();
        mobCombatStart.putIfAbsent(mobId, now);
        mobCombatEnd.put(mobId, now);
        mobDamageRecords.computeIfAbsent(mobId, k -> new ConcurrentHashMap<>())
                .merge(playerId, damage, Double::sum);
    }

    /**
     * Returns an unmodifiable snapshot of accumulated damage for a specific mob.
     */
    public Map<UUID, Double> getRawDamage(UUID mobId) {
        if (mobId == null) return Map.of();
        Map<UUID, Double> records = mobDamageRecords.get(mobId);
        return records == null ? Map.of() : Collections.unmodifiableMap(new ConcurrentHashMap<>(records));
    }

    public record TopDamagerEntry(
            int rank,
            UUID playerId,
            double damage,
            double percentage,
            double dps
    ) {}

    /**
     * Returns the real-time leaderboard of damagers for the mob sorted descending by damage.
     */
    public List<TopDamagerEntry> getLeaderboard(UUID mobId) {
        if (mobId == null) return List.of();
        Map<UUID, Double> records = mobDamageRecords.get(mobId);
        if (records == null || records.isEmpty()) {
            DamageContributionSnapshot snap = finalSnapshots.get(mobId);
            if (snap != null) {
                List<TopDamagerEntry> list = new ArrayList<>();
                int rank = 1;
                for (Map.Entry<UUID, Double> e : snap.damageMap().entrySet()) {
                    list.add(new TopDamagerEntry(
                            rank++,
                            e.getKey(),
                            e.getValue(),
                            snap.percentageMap().getOrDefault(e.getKey(), 0.0),
                            0.0
                    ));
                }
                return Collections.unmodifiableList(list);
            }
            return List.of();
        }

        double total = records.values().stream().mapToDouble(Double::doubleValue).sum();
        long start = mobCombatStart.getOrDefault(mobId, System.currentTimeMillis());
        long end = mobCombatEnd.getOrDefault(mobId, System.currentTimeMillis());
        double seconds = Math.max(1.0, (end - start) / 1000.0);

        List<Map.Entry<UUID, Double>> sorted = records.entrySet().stream()
                .filter(e -> e.getValue() > 0.0)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();

        List<TopDamagerEntry> leaderboard = new ArrayList<>(sorted.size());
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : sorted) {
            double dmg = entry.getValue();
            double pct = total > 0.0 ? (dmg / total) * 100.0 : 0.0;
            double dps = dmg / seconds;
            leaderboard.add(new TopDamagerEntry(rank++, entry.getKey(), dmg, pct, dps));
        }
        return Collections.unmodifiableList(leaderboard);
    }

    /**
     * Generates a victory summary banner for the mob.
     */
    public BossVictorySummary generateVictorySummary(UUID mobId, String bossName, Function<UUID, String> nameLookup) {
        long start = mobCombatStart.getOrDefault(mobId, System.currentTimeMillis());
        long end = mobCombatEnd.getOrDefault(mobId, System.currentTimeMillis());
        long duration = Math.max(1000L, end - start);

        List<TopDamagerEntry> leaderboard = getLeaderboard(mobId);
        List<BossVictorySummary.DamagerRank> top3 = new ArrayList<>();
        for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
            TopDamagerEntry entry = leaderboard.get(i);
            String name = nameLookup != null ? nameLookup.apply(entry.playerId()) : null;
            top3.add(new BossVictorySummary.DamagerRank(
                    entry.rank(), entry.playerId(), name, entry.damage(), entry.percentage()
            ));
        }
        return new BossVictorySummary(bossName, duration, top3);
    }

    /**
     * Calculates the player's reward tier based on damage percentage.
     */
    public RewardTier getRewardTier(UUID mobId, UUID playerId) {
        if (mobId == null || playerId == null) return RewardTier.TIER_3_PARTICIPATION;
        List<TopDamagerEntry> leaderboard = getLeaderboard(mobId);
        if (leaderboard.isEmpty()) return RewardTier.TIER_3_PARTICIPATION;
        TopDamagerEntry top = leaderboard.get(0);
        if (top.playerId().equals(playerId)) {
            return RewardTier.TIER_1_MVP;
        }
        for (TopDamagerEntry entry : leaderboard) {
            if (entry.playerId().equals(playerId)) {
                return RewardTier.of(false, entry.percentage());
            }
        }
        return RewardTier.TIER_3_PARTICIPATION;
    }

    /**
     * Generates a DamageContributionSnapshot for the mob and stores it as the final snapshot.
     */
    public DamageContributionSnapshot finalizeMob(UUID mobId, UUID killerId) {
        if (mobId == null) {
            throw new IllegalArgumentException("Mob ID must not be null");
        }
        mobCombatEnd.put(mobId, System.currentTimeMillis());
        Map<UUID, Double> raw = mobDamageRecords.remove(mobId);
        DamageContributionSnapshot snapshot = DamageContributionSnapshot.create(mobId, raw, killerId);
        finalSnapshots.put(mobId, snapshot);
        return snapshot;
    }

    /**
     * Retrieves the finalized snapshot of a dead/unregistered mob, if available.
     */
    public Optional<DamageContributionSnapshot> getFinalSnapshot(UUID mobId) {
        if (mobId == null) return Optional.empty();
        return Optional.ofNullable(finalSnapshots.get(mobId));
    }

    /**
     * Cleans up all data associated with a mob entity UUID.
     */
    public void cleanup(UUID mobId) {
        if (mobId == null) return;
        mobDamageRecords.remove(mobId);
        finalSnapshots.remove(mobId);
        mobCombatStart.remove(mobId);
        mobCombatEnd.remove(mobId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        UUID mobId = victim.getUniqueId();
        if (mobManager != null && mobManager.get(mobId) == null) {
            return;
        }

        Player player = resolvePlayerDamager(event.getDamager());
        if (player != null) {
            recordDamage(mobId, player.getUniqueId(), event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        UUID mobId = entity.getUniqueId();

        if (mobManager != null && mobManager.get(mobId) == null) {
            return;
        }

        Player killer = entity.getKiller();
        UUID killerId = killer != null ? killer.getUniqueId() : null;
        finalizeMob(mobId, killerId);
    }

    /**
     * Resolves the originating Player from various damager entity types (including tamed pets).
     */
    public static Player resolvePlayerDamager(Entity damager) {
        if (damager == null) {
            return null;
        }
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof AreaEffectCloud aec && aec.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}
