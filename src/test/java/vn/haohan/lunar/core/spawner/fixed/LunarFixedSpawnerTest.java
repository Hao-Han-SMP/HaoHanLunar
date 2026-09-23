package vn.haohan.lunar.core.spawner.fixed;

import vn.haohan.lunar.api.system.spawner.fixed.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.MobDefinitionRegistry;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunarFixedSpawnerTest {

    private World world;
    private Location spawnerLoc;
    private MobDefinitionRegistry definitions;
    private LunarMobManager mobManager;

    @BeforeEach
    void setUp() {
        world = mockWorld("lunar");
        spawnerLoc = new Location(world, 100, 64, 100);
        definitions = new MobDefinitionRegistry();
        MobDefinition mobDef = new MobDefinition(new MobDefinitionId("lunar_warden"), EntityType.IRON_GOLEM,
                "The Lunar Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        definitions.register(mobDef);
        mobManager = new LunarMobManager();
    }

    @Test
    void spawnerDefinitionValidatesParameters() {
        SpawnerDefinition def = SpawnerDefinition.builder("warden_spawner", "lunar_warden", spawnerLoc)
                .maxMobs(2)
                .mobsPerSpawn(1)
                .cooldownSeconds(10)
                .warmupSeconds(5)
                .activationRange(30.0)
                .leashRange(50.0)
                .healOnLeash(true)
                .resetThreatOnLeash(true)
                .build();

        assertEquals("warden_spawner", def.id());
        assertEquals("lunar_warden", def.mobId());
        assertEquals(2, def.maxMobs());
        assertEquals(10, def.cooldownSeconds());
        assertEquals(5, def.warmupSeconds());
        assertEquals(30.0, def.activationRange());
        assertEquals(50.0, def.leashRange());
        assertTrue(def.healOnLeash());
        assertTrue(def.resetThreatOnLeash());

        assertThrows(IllegalArgumentException.class, () ->
                SpawnerDefinition.builder("", "mob", spawnerLoc).build());
        assertThrows(IllegalArgumentException.class, () ->
                SpawnerDefinition.builder("id", "", spawnerLoc).build());
    }

    @Test
    void warmupAndCooldownProgressCorrectlyAndSpawnMob() {
        SpawnerDefinition def = SpawnerDefinition.builder("test_spawner", "lunar_warden", spawnerLoc)
                .maxMobs(1)
                .mobsPerSpawn(1)
                .warmupSeconds(1) // 20 ticks warmup
                .cooldownSeconds(2) // 40 ticks cooldown
                .activationRange(0.0) // always active
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);
        AtomicInteger spawnCalls = new AtomicInteger(0);
        UUID dummyMobId = UUID.randomUUID();

        LunarFixedSpawner.SpawnerCallback callback = (mobId, loc, sId) -> {
            spawnCalls.incrementAndGet();
            return java.util.Optional.of(dummyMobId);
        };

        // Initially 20 ticks warmup
        assertEquals(20, spawner.currentWarmupTicks());

        // Tick 20 times to clear warmup
        for (int i = 0; i < 20; i++) {
            spawner.tick(i, mobManager, callback, (loc, r) -> true);
        }
        assertEquals(0, spawner.currentWarmupTicks());
        assertEquals(0, spawnCalls.get()); // Warmup finished, cooldown not started ticking down yet

        // Current cooldown is 40 ticks
        assertEquals(40, spawner.currentCooldownTicks());

        // Tick 40 times to countdown cooldown from 40 to 0
        for (int i = 0; i < 40; i++) {
            spawner.tick(i + 20, mobManager, callback, (loc, r) -> true);
        }
        assertEquals(0, spawner.currentCooldownTicks());
        assertEquals(0, spawnCalls.get());

        // Tick 1 more time -> cooldown drops from 0 to trigger spawn and resets to 40
        spawner.tick(60, mobManager, callback, (loc, r) -> true);
        assertEquals(1, spawnCalls.get());
        assertTrue(spawner.isTracked(dummyMobId));
        assertEquals(1, spawner.activeMobCount());
        assertEquals(40, spawner.currentCooldownTicks());
    }

    @Test
    void spawnerEnforcesMaxMobsConstraint() {
        SpawnerDefinition def = SpawnerDefinition.builder("test_max", "lunar_warden", spawnerLoc)
                .maxMobs(2)
                .mobsPerSpawn(2)
                .warmupSeconds(0)
                .cooldownSeconds(1)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);
        spawner.setCooldownTicks(0);

        List<UUID> spawned = new ArrayList<>();
        LunarFixedSpawner.SpawnerCallback callback = (mobId, loc, sId) -> {
            UUID id = UUID.randomUUID();
            spawned.add(id);
            return java.util.Optional.of(id);
        };

        // First tick spawns 2 mobs (hits maxMobs = 2)
        spawner.tick(1, mobManager, callback, (loc, r) -> true);
        assertEquals(2, spawned.size());
        assertEquals(2, spawner.activeMobCount());

        // Force cooldown to 0 again
        spawner.setCooldownTicks(0);

        // Next tick should NOT spawn because capacity is full (2 >= 2)
        spawner.tick(2, mobManager, callback, (loc, r) -> true);
        assertEquals(2, spawned.size());
    }

    @Test
    void spawnerDoesNotTickWhenNoPlayersInActivationRange() {
        SpawnerDefinition def = SpawnerDefinition.builder("inactive_spawner", "lunar_warden", spawnerLoc)
                .activationRange(30.0)
                .warmupSeconds(0)
                .cooldownSeconds(10)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);
        int initialCooldown = spawner.currentCooldownTicks();

        // Proximity checker returns false (no player within 30 blocks)
        spawner.tick(1, mobManager, null, (loc, r) -> false);

        assertFalse(spawner.isActive());
        // Cooldown ticks should NOT have decremented
        assertEquals(initialCooldown, spawner.currentCooldownTicks());
    }

    @Test
    void leashPullsMobBackWhenExceedingLeashRange() {
        SpawnerDefinition def = SpawnerDefinition.builder("leash_spawner", "lunar_warden", spawnerLoc)
                .leashRange(50.0)
                .healOnLeash(true)
                .resetThreatOnLeash(true)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);

        // Mob entity placed 80 blocks away (exceeds 50 block leash range)
        AtomicBoolean teleported = new AtomicBoolean(false);
        AtomicBoolean healed = new AtomicBoolean(false);
        AtomicBoolean threatCleared = new AtomicBoolean(false);

        Location farLoc = new Location(world, 180, 64, 100);
        LivingEntity entity = mockLivingMob(world, farLoc, 5.0, 100.0, teleported, healed, threatCleared);

        MobDefinition mobDef = definitions.get("lunar_warden").orElseThrow();
        ActiveLunarMob activeMob = new ActiveLunarMob(entity, mobDef, new LunarMobIdentity("lunar_warden", "1"));
        mobManager.register(activeMob);

        spawner.attachTrackedMob(activeMob.entityId());
        assertEquals(1, spawner.activeMobCount());

        // Run spawner tick
        spawner.tick(1, mobManager, null, (loc, r) -> true);

        // Assert mob was pulled back, healed, and target cleared
        assertTrue(teleported.get(), "Mob should have been teleported back to spawner location");
        assertTrue(healed.get(), "Mob should have been healed to max health");
        assertTrue(threatCleared.get(), "Mob target should have been cleared");
    }

    @Test
    void fixedSpawnerManagerRegistersAndTicksCentrally() {
        FixedSpawnerManager manager = new FixedSpawnerManager(definitions, mobManager);
        SpawnerDefinition def = SpawnerDefinition.builder("central_spawner", "lunar_warden", spawnerLoc)
                .warmupSeconds(0)
                .cooldownSeconds(1)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);
        spawner.setCooldownTicks(0);
        manager.register(spawner);

        assertTrue(manager.hasSpawner("central_spawner"));
        assertEquals(spawner, manager.get("central_spawner").orElse(null));

        AtomicBoolean spawned = new AtomicBoolean(false);
        manager.setCustomSpawnerCallback((mId, loc, sId) -> {
            spawned.set(true);
            return java.util.Optional.of(UUID.randomUUID());
        });
        manager.setCustomProximityChecker((loc, r) -> true);

        // Tick centrally
        manager.tickAll(1);
        assertTrue(spawned.get());
        assertEquals(1, spawner.activeMobCount());

        // Unregister
        manager.unregister("central_spawner");
        assertFalse(manager.hasSpawner("central_spawner"));
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> null;
                });
    }

    private static LivingEntity mockLivingMob(World world,
                                              Location loc,
                                              double health,
                                              double maxHealth,
                                              AtomicBoolean teleported,
                                              AtomicBoolean healed,
                                              AtomicBoolean threatCleared) {
        UUID uuid = UUID.randomUUID();

        return (LivingEntity) Proxy.newProxyInstance(
                LunarFixedSpawnerTest.class.getClassLoader(),
                new Class<?>[]{Mob.class, LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getHealth" -> health;
                    case "getMaxHealth" -> maxHealth;
                    case "setHealth" -> {
                        healed.set(true);
                        yield null;
                    }
                    case "teleport" -> {
                        teleported.set(true);
                        yield true;
                    }
                    case "setTarget" -> {
                        threatCleared.set(true);
                        yield null;
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }
}
