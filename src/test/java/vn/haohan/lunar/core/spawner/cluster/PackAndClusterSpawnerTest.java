package vn.haohan.lunar.core.spawner.cluster;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobAttributeDefinition;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PackAndClusterSpawnerTest {

    @Test
    void testClusterDefinitionBuilderAndValidation() {
        ClusterDefinition def = ClusterDefinition.builder("wolf_pack", "alpha_wolf")
                .minionMobId("pack_wolf")
                .minMinions(3)
                .maxMinions(6)
                .radius(12.0)
                .chance(0.8)
                .enabled(true)
                .build();

        assertEquals("wolf_pack", def.id());
        assertEquals("alpha_wolf", def.leaderMobId());
        assertEquals("pack_wolf", def.minionMobId());
        assertEquals(3, def.minMinions());
        assertEquals(6, def.maxMinions());
        assertEquals(12.0, def.radius());
        assertEquals(0.8, def.chance());
        assertTrue(def.enabled());
    }

    @Test
    void testClusterGeneratorSpawnsLeaderAndMinionsWithParentLink() {
        ClusterDefinition def = ClusterDefinition.builder("goblin_patrol", "goblin_leader")
                .minionMobId("goblin_grunt")
                .minMinions(3)
                .maxMinions(3)
                .radius(6.0)
                .chance(1.0)
                .build();

        ClusterGenerator generator = new ClusterGenerator(new Random(42));
        World mockWorld = mockWorld("lunar_world");
        Location center = new Location(mockWorld, 100.0, 64.0, 100.0);

        List<ActiveLunarMob> allSpawned = new ArrayList<>();
        ClusterSpawnResult result = generator.generate(def, center, 50, 0, (mobId, loc) -> {
            LivingEntity entity = mockMobEntity(loc);
            MobDefinition mDef = new MobDefinition(new MobDefinitionId(mobId), EntityType.ZOMBIE, mobId, null,
                    Map.of("max_health", new MobAttributeDefinition("max_health", 100.0)),
                    Map.of(), List.of(), null, Set.of());
            ActiveLunarMob mob = new ActiveLunarMob(entity, mDef, new LunarMobIdentity(mobId, "1.0.0"));
            allSpawned.add(mob);
            return mob;
        });

        assertTrue(result.success());
        assertNotNull(result.leader());
        assertEquals("goblin_leader", result.leader().definitionId().value());
        assertEquals(3, result.minions().size());

        // Check parentUUID relationship
        UUID leaderId = result.leader().entityId();
        assertNull(result.leader().parentUUID());

        for (ActiveLunarMob minion : result.minions()) {
            assertEquals("goblin_grunt", minion.definitionId().value());
            assertEquals(leaderId, minion.parentUUID(), "Minion must reference leader UUID as parent");
        }
    }

    @Test
    void testClusterGeneratorRespectsMobCap() {
        ClusterDefinition def = ClusterDefinition.builder("swarm", "queen")
                .minionMobId("drone")
                .minMinions(5)
                .maxMinions(5)
                .build();

        ClusterGenerator generator = new ClusterGenerator(new Random(1));
        World mockWorld = mockWorld("lunar_world");
        Location center = new Location(mockWorld, 0, 64, 0);

        // Scenario 1: Cap reached completely (current = 10, cap = 10)
        ClusterSpawnResult failResult = generator.generate(def, center, 10, 10, (mobId, loc) -> {
            LivingEntity entity = mockMobEntity(loc);
            return new ActiveLunarMob(entity, new MobDefinition(new MobDefinitionId(mobId), EntityType.ZOMBIE, mobId, null,
                    Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity(mobId, "1.0.0"));
        });
        assertFalse(failResult.success());
        assertTrue(failResult.message().contains("mob cap reached"));

        // Scenario 2: Trims minions to fit remaining capacity (cap = 10, current = 7 -> room for leader + 2 minions)
        ClusterSpawnResult trimResult = generator.generate(def, center, 10, 7, (mobId, loc) -> {
            LivingEntity entity = mockMobEntity(loc);
            return new ActiveLunarMob(entity, new MobDefinition(new MobDefinitionId(mobId), EntityType.ZOMBIE, mobId, null,
                    Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity(mobId, "1.0.0"));
        });
        assertTrue(trimResult.success());
        assertNotNull(trimResult.leader());
        assertEquals(2, trimResult.minions().size(), "Minions should be trimmed to exactly fit remaining capacity");
    }

    @Test
    void testPackAggroBroadcastingWhenMemberAttacked() {
        World mockWorld = mockWorld("lunar_world");
        Location loc = new Location(mockWorld, 0, 64, 0);

        // Create leader
        AtomicReference<LivingEntity> leaderTarget = new AtomicReference<>();
        LivingEntity leaderEntity = mockMobWithTarget(loc, leaderTarget);
        ActiveLunarMob leader = new ActiveLunarMob(leaderEntity, new MobDefinition(new MobDefinitionId("alpha"), EntityType.WOLF, "Alpha", null,
                Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity("alpha", "1.0.0"));

        // Create 2 minions
        AtomicReference<LivingEntity> minion1Target = new AtomicReference<>();
        LivingEntity minion1Entity = mockMobWithTarget(loc, minion1Target);
        ActiveLunarMob minion1 = new ActiveLunarMob(minion1Entity, new MobDefinition(new MobDefinitionId("minion"), EntityType.WOLF, "Minion1", null,
                Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity("minion", "1.0.0"));
        minion1.setParentUUID(leader.entityId());

        AtomicReference<LivingEntity> minion2Target = new AtomicReference<>();
        LivingEntity minion2Entity = mockMobWithTarget(loc, minion2Target);
        ActiveLunarMob minion2 = new ActiveLunarMob(minion2Entity, new MobDefinition(new MobDefinitionId("minion"), EntityType.WOLF, "Minion2", null,
                Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity("minion", "1.0.0"));
        minion2.setParentUUID(leader.entityId());

        // Create unrelated mob
        AtomicReference<LivingEntity> strangerTarget = new AtomicReference<>();
        LivingEntity strangerEntity = mockMobWithTarget(loc, strangerTarget);
        ActiveLunarMob stranger = new ActiveLunarMob(strangerEntity, new MobDefinition(new MobDefinitionId("stranger"), EntityType.SHEEP, "Stranger", null,
                Map.of(), Map.of(), List.of(), null, Set.of()), new LunarMobIdentity("stranger", "1.0.0"));

        List<ActiveLunarMob> candidates = List.of(leader, minion1, minion2, stranger);

        // Hostile attacker attacks minion 1
        LivingEntity attacker = mockMobEntity(loc);
        PackAggroCoordinator coordinator = new PackAggroCoordinator(200.0);

        int alerted = coordinator.broadcastAggro(minion1, attacker, candidates);
        assertEquals(3, alerted, "All 3 pack members (leader, minion1, minion2) should be alerted");

        // Verify leader and minions acquired target and threat
        assertEquals(attacker, leaderTarget.get());
        assertEquals(attacker, minion1Target.get());
        assertEquals(attacker, minion2Target.get());
        assertNull(strangerTarget.get(), "Stranger mob should not receive pack aggro");

        assertEquals(200.0, leader.threatTable().getThreat(attacker.getUniqueId()));
        assertEquals(200.0, minion1.threatTable().getThreat(attacker.getUniqueId()));
        assertEquals(200.0, minion2.threatTable().getThreat(attacker.getUniqueId()));
        assertEquals(0.0, stranger.threatTable().getThreat(attacker.getUniqueId()));
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }

    private static LivingEntity mockMobEntity(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static LivingEntity mockMobWithTarget(Location loc, AtomicReference<LivingEntity> targetRef) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class, Mob.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("setTarget")) {
                        targetRef.set((LivingEntity) args[0]);
                        return null;
                    }
                    if (method.getName().equals("getTarget")) return targetRef.get();
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }
}
