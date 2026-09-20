package vn.haohan.lunar.core.spawner.fixed;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.mob.MobAttributeDefinition;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FixedSpawnerLeashTest {

    @Test
    void testMobExceedingLeashRangeIsTeleportedAndReset() {
        World mockWorld = mockWorld("lunar_world");
        Location spawnerLoc = new Location(mockWorld, 0.0, 64.0, 0.0);

        SpawnerDefinition spawnerDef = SpawnerDefinition.builder("boss_spawner", "lunar_golem", spawnerLoc)
                .leashRange(30.0)
                .healOnLeash(true)
                .resetThreatOnLeash(true)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(spawnerDef);

        // Create mob initially pulled far away (e.g. 50 blocks away at x=50, z=0)
        AtomicReference<Location> currentLoc = new AtomicReference<>(new Location(mockWorld, 50.0, 64.0, 0.0));
        AtomicReference<Double> health = new AtomicReference<>(25.0);
        AtomicReference<LivingEntity> target = new AtomicReference<>();

        LivingEntity entity = mockMob(currentLoc, health, 100.0, target);
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("lunar_golem"), EntityType.IRON_GOLEM, "Lunar Golem", null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 100.0)),
                Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("lunar_golem", "1.0.0"));

        // Add some threat to player
        UUID playerUuid = UUID.randomUUID();
        mob.threatTable().addThreat(playerUuid, 500.0);
        assertEquals(500.0, mob.threatTable().getThreat(playerUuid));

        // Track mob on spawner
        spawner.attachTrackedMob(mob.entityId());

        LunarMobManager mobManager = new LunarMobManager(e -> {});
        mobManager.register(mob);

        // Tick spawner with mob out of leash
        spawner.tick(1L, mobManager, null, (loc, range) -> true);

        // Assert teleported back to spawner
        assertEquals(0.0, currentLoc.get().getX(), 0.001);
        assertEquals(64.0, currentLoc.get().getY(), 0.001);
        assertEquals(0.0, currentLoc.get().getZ(), 0.001);

        // Assert full heal
        assertEquals(100.0, health.get(), 0.001);

        // Assert threat cleared
        assertEquals(0.0, mob.threatTable().getThreat(playerUuid));
        assertFalse(mob.isSoftLeashed());
    }

    @Test
    void testSoftBoundaryActivatesSoftLeashedState() {
        World mockWorld = mockWorld("lunar_world");
        Location spawnerLoc = new Location(mockWorld, 0.0, 64.0, 0.0);

        SpawnerDefinition spawnerDef = SpawnerDefinition.builder("boss_spawner", "lunar_golem", spawnerLoc)
                .softLeashRadius(20.0)
                .hardLeashRadius(50.0)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(spawnerDef);

        // Place mob at 35 blocks away (exceeds soft 20, but within hard 50)
        AtomicReference<Location> currentLoc = new AtomicReference<>(new Location(mockWorld, 35.0, 64.0, 0.0));
        AtomicReference<Double> health = new AtomicReference<>(50.0);
        AtomicReference<LivingEntity> target = new AtomicReference<>();

        LivingEntity entity = mockMob(currentLoc, health, 100.0, target);
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("lunar_golem"), EntityType.IRON_GOLEM, "Lunar Golem", null,
                Map.of(), Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("lunar_golem", "1.0.0"));

        spawner.attachTrackedMob(mob.entityId());
        LunarMobManager mobManager = new LunarMobManager(e -> {});
        mobManager.register(mob);

        // Tick
        spawner.tick(1L, mobManager, null, (loc, range) -> true);

        // Should NOT have teleported back yet
        assertEquals(35.0, currentLoc.get().getX(), 0.001);
        // But should be marked softLeashed (90% damage mitigation)
        assertTrue(mob.isSoftLeashed());

        // Now move mob back within 15 blocks
        currentLoc.set(new Location(mockWorld, 15.0, 64.0, 0.0));
        spawner.tick(2L, mobManager, null, (loc, range) -> true);
        assertFalse(mob.isSoftLeashed());
    }

    @Test
    void testDynamicRespawnWavesSequence() {
        World mockWorld = mockWorld("lunar_world");
        Location spawnerLoc = new Location(mockWorld, 0.0, 64.0, 0.0);

        List<SpawnerWave> waves = List.of(
                new SpawnerWave(1, List.of(new SpawnerWave.WaveEntry("wave1_mob", 2))),
                new SpawnerWave(2, List.of(new SpawnerWave.WaveEntry("boss_mob", 1)))
        );

        SpawnerDefinition spawnerDef = SpawnerDefinition.builder("wave_spawner", "wave1_mob", spawnerLoc)
                .waves(waves)
                .cooldownSeconds(0)
                .warmupSeconds(0)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(spawnerDef);
        spawner.setCooldownTicks(0);

        List<String> spawned = new ArrayList<>();
        LunarFixedSpawner.SpawnerCallback callback = (mobId, loc, spawnerId) -> {
            spawned.add(mobId);
            return Optional.of(UUID.randomUUID());
        };

        LunarMobManager mobManager = new LunarMobManager(e -> {});

        // Tick 1: Spawns Wave 1 (2 wave1_mobs)
        spawner.tick(1L, mobManager, callback, (loc, range) -> true);
        assertEquals(List.of("wave1_mob", "wave1_mob"), spawned);
        assertEquals(0, spawner.currentWaveIndex());
        assertEquals(2, spawner.activeMobCount());

        // Clear wave 1 mobs (simulating dead)
        List<UUID> wave1MobIds = new ArrayList<>(spawner.trackedMobs());
        for (UUID id : wave1MobIds) {
            spawner.untrackMob(id);
        }

        // Tick 2: Wave 1 dead -> advances to Wave 2
        spawner.tick(2L, mobManager, callback, (loc, range) -> true);
        assertEquals(1, spawner.currentWaveIndex());

        // Tick 3: Spawns Wave 2 (1 boss_mob)
        spawner.tick(3L, mobManager, callback, (loc, range) -> true);
        assertEquals(List.of("wave1_mob", "wave1_mob", "boss_mob"), spawned);
        assertEquals(1, spawner.activeMobCount());

        // Clear boss
        spawner.untrackMob(spawner.trackedMobs().iterator().next());

        // Tick 4: Boss dead -> all waves cleared -> resets sequence to wave 0
        spawner.tick(4L, mobManager, callback, (loc, range) -> true);
        assertEquals(0, spawner.currentWaveIndex());
    }

    @Test
    void testSpawnConditionsPausesCountdown() {
        World mockWorld = mockWorld("lunar_world");
        Location spawnerLoc = new Location(mockWorld, 0.0, 64.0, 0.0);

        SpawnerDefinition spawnerDef = SpawnerDefinition.builder("cond_spawner", "boss", spawnerLoc)
                .cooldownSeconds(10)
                .warmupSeconds(0)
                .conditions(List.of("lunarphase FULL_MOON"))
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(spawnerDef);
        AtomicBoolean conditionMet = new AtomicBoolean(false);
        spawner.setConditionEvaluator(def -> conditionMet.get());

        int initialCd = spawner.currentCooldownTicks();

        // Tick with condition false -> cooldown tick does NOT decrease
        spawner.tick(1L, null, null, (loc, r) -> true);
        assertEquals(initialCd, spawner.currentCooldownTicks());

        // Set condition true -> cooldown tick decreases
        conditionMet.set(true);
        spawner.tick(2L, null, null, (loc, r) -> true);
        assertEquals(initialCd - 1, spawner.currentCooldownTicks());
    }

    @Test
    void testMobWithinLeashRangeRemainsUnaffected() {
        World mockWorld = mockWorld("lunar_world");
        Location spawnerLoc = new Location(mockWorld, 0.0, 64.0, 0.0);

        SpawnerDefinition spawnerDef = SpawnerDefinition.builder("guard_spawner", "guard", spawnerLoc)
                .leashRange(30.0)
                .healOnLeash(true)
                .resetThreatOnLeash(true)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(spawnerDef);

        // Mob is only 10 blocks away (within leash range)
        AtomicReference<Location> currentLoc = new AtomicReference<>(new Location(mockWorld, 10.0, 64.0, 0.0));
        AtomicReference<Double> health = new AtomicReference<>(50.0);
        LivingEntity mockTarget = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (p, m, a) -> null);
        AtomicReference<LivingEntity> target = new AtomicReference<>(mockTarget);

        LivingEntity entity = mockMob(currentLoc, health, 100.0, target);
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("guard"), EntityType.ZOMBIE, "Guard", null,
                Map.of("max_health", new MobAttributeDefinition("max_health", 100.0)),
                Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("guard", "1.0.0"));

        UUID playerUuid = UUID.randomUUID();
        mob.threatTable().addThreat(playerUuid, 300.0);
        spawner.attachTrackedMob(mob.entityId());

        LunarMobManager mobManager = new LunarMobManager(e -> {});
        mobManager.register(mob);

        // Tick spawner
        spawner.tick(1L, mobManager, null, (loc, range) -> true);

        // Still at (10, 64, 0), health still 50.0, threat still 300.0
        assertEquals(10.0, currentLoc.get().getX(), 0.001);
        assertEquals(50.0, health.get(), 0.001);
        assertEquals(300.0, mob.threatTable().getThreat(playerUuid));
        assertNotNull(target.get());
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

    private static LivingEntity mockMob(
            AtomicReference<Location> locRef,
            AtomicReference<Double> healthRef,
            double maxHealth,
            AtomicReference<LivingEntity> targetRef
    ) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class, Mob.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> locRef.get();
                    case "teleport" -> {
                        locRef.set((Location) args[0]);
                        yield true;
                    }
                    case "getHealth" -> healthRef.get();
                    case "setHealth" -> {
                        healthRef.set((Double) args[0]);
                        yield null;
                    }
                    case "getMaxHealth" -> maxHealth;
                    case "getTarget" -> targetRef.get();
                    case "setTarget" -> {
                        targetRef.set(args.length > 0 ? (LivingEntity) args[0] : null);
                        yield null;
                    }
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getUniqueId" -> UUID.nameUUIDFromBytes(locRef.get().toString().getBytes());
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 42;
                    default -> null;
                });
    }
}
