package vn.haohan.lunar.core.combat.threat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatTableTest {

    private UUID mobUuid;
    private ThreatTable table;

    @BeforeEach
    void setUp() {
        mobUuid = UUID.randomUUID();
        table = new ThreatTable(mobUuid);
    }

    @Test
    void tracksAndAccumulatesThreatValues() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        table.addThreat(player1, 50.0, 100L);
        table.addThreat(player1, 30.0, 120L);
        table.addThreat(player2, 40.0, 100L);

        assertEquals(80.0, table.getThreat(player1), 0.001);
        assertEquals(40.0, table.getThreat(player2), 0.001);
        assertEquals(player1, table.getTopThreatTarget().orElseThrow());
    }

    @Test
    void targetSwitchThresholdPreventsFlinchingOscillation() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        // Player A deals 100 damage
        table.addThreat(playerA, 100.0, 100L);
        assertEquals(playerA, table.evaluateTarget().orElseThrow());

        // Player B deals 105 damage. Since 105 < 100 * 1.10 (110.0), target should remain Player A
        table.addThreat(playerB, 105.0, 110L);
        assertEquals(playerA, table.evaluateTarget().orElseThrow(),
                "Target must not switch when threat does not exceed the 110% switch threshold");

        // Player B deals another 10 damage (total 115.0 >= 110.0), now target should switch
        table.addThreat(playerB, 10.0, 120L);
        assertEquals(playerB, table.evaluateTarget().orElseThrow(),
                "Target should switch once the 110% threshold is exceeded");
    }

    @Test
    void decayReducesThreatAfterInactivity() {
        UUID player = UUID.randomUUID();
        table.addThreat(player, 100.0, 100L);
        table.setDecayDelayTicks(40L);
        table.setDecayFractionPerSecond(0.20); // 20% decay per second

        // Current tick 120 (diff = 20 < 40 delay) -> no decay
        table.tickDecay(120L, 20L);
        assertEquals(100.0, table.getThreat(player), 0.001);

        // Current tick 150 (diff = 50 >= 40 delay) -> decays 20%
        table.tickDecay(150L, 20L);
        assertEquals(80.0, table.getThreat(player), 0.001);
    }

    @Test
    void threatManagerTracksDamageEventsAndProjectiles() {
        LunarMobManager mobManager = new LunarMobManager();
        ThreatManager threatManager = new ThreatManager(mobManager);

        LivingEntity mobEntity = mockEntity(mobUuid);
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("boss"), EntityType.IRON_GOLEM, "Boss", null,
                Map.of(), Map.of(), List.of(), null, Set.of());
        mobManager.register(new ActiveLunarMob(mobEntity, mobDef, new LunarMobIdentity("boss", "1.0")));

        UUID shooterUuid = UUID.randomUUID();
        LivingEntity shooter = mockEntity(shooterUuid);
        Arrow projectile = mockArrow(shooter);

        // Record projectile damage
        threatManager.recordDamage(mobEntity, projectile, 25.0, 100L);

        ThreatTable mobTable = threatManager.get(mobUuid).orElseThrow();
        assertEquals(25.0, mobTable.getThreat(shooterUuid), 0.001);

        // Player quit removes target
        Player mockPlayer = mockPlayer(shooterUuid, "Sniper");
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(mockPlayer, (net.kyori.adventure.text.Component) null);
        threatManager.onPlayerQuit(quitEvent);

        assertEquals(0.0, mobTable.getThreat(shooterUuid), 0.001);

        // Mob death removes mob from manager and threat table
        EntityDeathEvent deathEvent = new EntityDeathEvent(mobEntity, (org.bukkit.damage.DamageSource) null, Collections.emptyList());
        mobManager.onEntityDeath(deathEvent);
        threatManager.onEntityDeath(deathEvent);

        assertTrue(threatManager.get(mobUuid).isEmpty());
    }

    // --- Helpers ---

    private LivingEntity mockEntity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(
                ThreatTableTest.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private Player mockPlayer(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
                ThreatTableTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private Arrow mockArrow(LivingEntity shooter) {
        return (Arrow) Proxy.newProxyInstance(
                ThreatTableTest.class.getClassLoader(),
                new Class<?>[]{Arrow.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "equals" -> proxy == args[0];
                    default -> null;
                }
        );
    }
}
