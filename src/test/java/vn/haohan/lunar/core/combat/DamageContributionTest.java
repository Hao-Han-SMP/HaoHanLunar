package vn.haohan.lunar.core.combat;

import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.combat.reward.BossVictorySummary;
import vn.haohan.lunar.core.combat.reward.RewardTier;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageContributionTest {

    @Test
    void snapshotCalculatesTotalPercentagesAndTopDamager() {
        UUID mobId = UUID.randomUUID();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        // P1: 50 dmg (50%), P2: 30 dmg (30%), P3: 20 dmg (20%) -> Total 100 dmg
        Map<UUID, Double> raw = Map.of(
                player1, 50.0,
                player2, 30.0,
                player3, 20.0
        );

        DamageContributionSnapshot snapshot = DamageContributionSnapshot.create(mobId, raw, player3);

        assertEquals(100.0, snapshot.totalDamage());
        assertEquals(player1, snapshot.topDamager());
        assertEquals(50.0, snapshot.topDamage());
        assertEquals(player3, snapshot.killer());

        assertEquals(50.0, snapshot.getPercentage(player1), 0.001);
        assertEquals(30.0, snapshot.getPercentage(player2), 0.001);
        assertEquals(20.0, snapshot.getPercentage(player3), 0.001);

        // Required percent tests
        assertTrue(snapshot.meetsRequiredPercentage(player1, 25.0));
        assertTrue(snapshot.meetsRequiredPercentage(player2, 30.0));
        assertFalse(snapshot.meetsRequiredPercentage(player3, 25.0));

        // Eligible players above 25% threshold
        List<UUID> eligible = snapshot.getEligiblePlayers(25.0);
        assertEquals(2, eligible.size());
        assertEquals(player1, eligible.get(0));
        assertEquals(player2, eligible.get(1));
    }

    @Test
    void snapshotHandlesZeroOrNullDamageSafely() {
        UUID mobId = UUID.randomUUID();
        DamageContributionSnapshot empty = DamageContributionSnapshot.create(mobId, Map.of(), null);

        assertEquals(0.0, empty.totalDamage());
        assertNull(empty.topDamager());
        assertEquals(0.0, empty.topDamage());
        assertNull(empty.killer());
        assertEquals(0.0, empty.getDamage(UUID.randomUUID()));
        assertEquals(0.0, empty.getPercentage(UUID.randomUUID()));
        assertFalse(empty.meetsRequiredPercentage(UUID.randomUUID(), 1.0));
        assertTrue(empty.getEligiblePlayers(5.0).isEmpty());
    }

    @Test
    void damageTrackerAccumulatesMultipleHitsAndFinalizes() {
        DamageTracker tracker = new DamageTracker();
        UUID mobId = UUID.randomUUID();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        tracker.recordDamage(mobId, playerA, 15.5);
        tracker.recordDamage(mobId, playerA, 14.5); // Total 30
        tracker.recordDamage(mobId, playerB, 70.0); // Total 70

        Map<UUID, Double> raw = tracker.getRawDamage(mobId);
        assertEquals(30.0, raw.get(playerA));
        assertEquals(70.0, raw.get(playerB));

        // Finalize mob
        DamageContributionSnapshot snapshot = tracker.finalizeMob(mobId, playerA);
        assertEquals(100.0, snapshot.totalDamage());
        assertEquals(playerB, snapshot.topDamager());
        assertEquals(playerA, snapshot.killer());

        // Raw records should be cleared after finalize
        assertTrue(tracker.getRawDamage(mobId).isEmpty());

        // Final snapshot cached
        assertTrue(tracker.getFinalSnapshot(mobId).isPresent());
        assertEquals(snapshot, tracker.getFinalSnapshot(mobId).get());

        // Cleanup
        tracker.cleanup(mobId);
        assertFalse(tracker.getFinalSnapshot(mobId).isPresent());
    }

    @Test
    void testFourPlayersDamageContributionAndLeaderboard() {
        DamageTracker tracker = new DamageTracker();
        UUID mobId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();
        UUID p4 = UUID.randomUUID();

        tracker.recordDamage(mobId, p1, 400.0); // 40%
        tracker.recordDamage(mobId, p2, 300.0); // 30%
        tracker.recordDamage(mobId, p3, 200.0); // 20%
        tracker.recordDamage(mobId, p4, 100.0); // 10%

        List<DamageTracker.TopDamagerEntry> leaderboard = tracker.getLeaderboard(mobId);
        assertEquals(4, leaderboard.size());

        assertEquals(p1, leaderboard.get(0).playerId());
        assertEquals(40.0, leaderboard.get(0).percentage(), 0.01);
        assertEquals(1, leaderboard.get(0).rank());

        assertEquals(p2, leaderboard.get(1).playerId());
        assertEquals(30.0, leaderboard.get(1).percentage(), 0.01);

        assertEquals(p3, leaderboard.get(2).playerId());
        assertEquals(20.0, leaderboard.get(2).percentage(), 0.01);

        assertEquals(p4, leaderboard.get(3).playerId());
        assertEquals(10.0, leaderboard.get(3).percentage(), 0.01);

        // Reward tiers
        assertEquals(RewardTier.TIER_1_MVP, tracker.getRewardTier(mobId, p1));
        assertEquals(RewardTier.TIER_2_MAJOR, tracker.getRewardTier(mobId, p2));
        assertEquals(RewardTier.TIER_2_MAJOR, tracker.getRewardTier(mobId, p3));
        assertEquals(RewardTier.TIER_2_MAJOR, tracker.getRewardTier(mobId, p4));

        UUID p5 = UUID.randomUUID();
        tracker.recordDamage(mobId, p5, 5.0); // < 10%
        assertEquals(RewardTier.TIER_3_PARTICIPATION, tracker.getRewardTier(mobId, p5));
    }

    @Test
    void testVictorySummaryAndAsciiBanner() {
        DamageTracker tracker = new DamageTracker();
        UUID mobId = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID();

        tracker.recordDamage(mobId, p1, 425.0);
        tracker.recordDamage(mobId, p2, 281.0);
        tracker.recordDamage(mobId, p3, 154.0);

        Map<UUID, String> names = Map.of(p1, "PlayerA", p2, "PlayerB", p3, "PlayerC");
        BossVictorySummary summary = tracker.generateVictorySummary(mobId, "LUNAR WARDEN", names::get);

        assertNotNull(summary);
        assertEquals("LUNAR WARDEN", summary.bossName());
        assertEquals(3, summary.topDamagers().size());
        assertEquals("PlayerA", summary.topDamagers().get(0).playerName());

        String banner = summary.renderAsciiBox();
        assertTrue(banner.contains("CHIẾN THẮNG: LUNAR WARDEN"));
        assertTrue(banner.contains("MVP Sát Thương: PlayerA"));
        assertTrue(banner.contains("Hạng 2: PlayerB"));
        assertTrue(banner.contains("Hạng 3: PlayerC"));
    }

    @Test
    void damagerResolverDetectsDirectAndIndirectPlayers() {
        Player player = mockPlayer();

        // Direct player
        assertEquals(player, DamageTracker.resolvePlayerDamager(player));

        // Projectile (Arrow) shot by player
        Arrow arrow = mockArrow(player);
        assertEquals(player, DamageTracker.resolvePlayerDamager(arrow));

        // TNT primed by player
        TNTPrimed tnt = mockTNT(player);
        assertEquals(player, DamageTracker.resolvePlayerDamager(tnt));

        // AreaEffectCloud created by player
        AreaEffectCloud aec = mockAEC(player);
        assertEquals(player, DamageTracker.resolvePlayerDamager(aec));

        // Tameable pet owned by player
        Tameable pet = mockTameable(player);
        assertEquals(player, DamageTracker.resolvePlayerDamager(pet));

        // Non-player projectile
        LivingEntity skeleton = mockLivingEntity();
        Arrow skeletonArrow = mockArrow(skeleton);
        assertNull(DamageTracker.resolvePlayerDamager(skeletonArrow));

        // Null damager
        assertNull(DamageTracker.resolvePlayerDamager(null));
    }

    // --- Helpers ---

    private static Player mockPlayer() {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> "Player_" + uuid.toString().substring(0, 4);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
    }

    private static LivingEntity mockLivingEntity() {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
    }

    private static Tameable mockTameable(AnimalTamer owner) {
        return (Tameable) Proxy.newProxyInstance(Tameable.class.getClassLoader(), new Class<?>[]{Tameable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOwner" -> owner;
                    case "isTamed" -> owner != null;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static Arrow mockArrow(Object shooter) {
        return (Arrow) Proxy.newProxyInstance(Arrow.class.getClassLoader(), new Class<?>[]{Arrow.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static TNTPrimed mockTNT(LivingEntity source) {
        return (TNTPrimed) Proxy.newProxyInstance(TNTPrimed.class.getClassLoader(), new Class<?>[]{TNTPrimed.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSource" -> source;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static AreaEffectCloud mockAEC(LivingEntity source) {
        return (AreaEffectCloud) Proxy.newProxyInstance(AreaEffectCloud.class.getClassLoader(), new Class<?>[]{AreaEffectCloud.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSource" -> source;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
