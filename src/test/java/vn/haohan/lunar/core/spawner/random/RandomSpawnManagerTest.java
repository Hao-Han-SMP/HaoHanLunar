package vn.haohan.lunar.core.spawner.random;

import vn.haohan.lunar.api.system.spawner.random.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomSpawnManagerTest {

    private World lunarWorld;
    private World overworld;

    @BeforeEach
    void setUp() {
        lunarWorld = mockWorld("haohan:lunar");
        overworld = mockWorld("minecraft:overworld");
    }

    @Test
    void ruleMatchesWorldBiomeAndElevation() {
        RandomSpawnRule rule = RandomSpawnRule.builder("lunar_mob_rule", "lunar_crawler")
                .worlds(Set.of("haohan:lunar"))
                .biomes(Set.of("lunar_barrens", "lunar_crater"))
                .elevation(0.0, 120.0)
                .chance(0.5)
                .build();

        // World check
        assertTrue(rule.matchesWorld(lunarWorld));
        assertFalse(rule.matchesWorld(overworld));

        // Biome check
        assertTrue(rule.matchesBiome("lunar_barrens"));
        assertTrue(rule.matchesBiome("minecraft:lunar_barrens"));
        assertTrue(rule.matchesBiome("lunar_crater"));
        assertFalse(rule.matchesBiome("plains"));

        // Elevation check
        assertTrue(rule.matchesElevation(64.0));
        assertFalse(rule.matchesElevation(-10.0));
        assertFalse(rule.matchesElevation(150.0));

        // Seeded random roll
        Random seeded = new Random(12345);
        boolean firstRoll = rule.rollChance(seeded);
        boolean secondRoll = rule.rollChance(seeded);
        // Repeat with identical seed
        Random duplicate = new Random(12345);
        assertEquals(firstRoll, rule.rollChance(duplicate));
        assertEquals(secondRoll, rule.rollChance(duplicate));
    }

    @Test
    void managerRespectsGlobalDisabledSafetyByDefault() {
        RandomSpawnManager manager = new RandomSpawnManager();
        assertFalse(manager.isGlobalEnabled(), "Random spawner must default to disabled for safety");

        RandomSpawnRule rule = RandomSpawnRule.builder("rule_1", "lunar_crawler")
                .enabled(true)
                .chance(1.0)
                .build();
        manager.registerRule(rule);

        Location loc = new Location(lunarWorld, 0, 64, 0);
        LivingEntity dummy = mockEntity(lunarWorld);
        CreatureSpawnEvent event = new CreatureSpawnEvent(dummy, CreatureSpawnEvent.SpawnReason.NATURAL);

        manager.onCreatureSpawn(event);
        assertFalse(event.isCancelled(), "Event should NOT be cancelled when spawner is globally disabled");
        assertEquals(0, manager.getTotalAttempts());
        assertEquals(0, manager.getSuccessfulSpawns());
    }

    @Test
    void replaceActionCancelsNaturalSpawnAndSpawnsLunarMob() {
        RandomSpawnManager manager = new RandomSpawnManager();
        manager.setGlobalEnabled(true);
        manager.setRandom(new Random(42));

        AtomicBoolean mobSpawned = new AtomicBoolean(false);
        List<String> spawnedMobIds = new ArrayList<>();
        manager.setCustomSpawner((mobId, loc) -> {
            mobSpawned.set(true);
            spawnedMobIds.add(mobId);
        });

        RandomSpawnRule rule = RandomSpawnRule.builder("replace_zombie", "lunar_crawler")
                .worlds(Set.of("haohan:lunar"))
                .action(SpawnAction.REPLACE)
                .chance(1.0)
                .enabled(true)
                .build();
        manager.registerRule(rule);

        Location loc = new Location(lunarWorld, 50, 70, 50);
        LivingEntity dummy = mockEntity(lunarWorld);
        CreatureSpawnEvent naturalEvent = new CreatureSpawnEvent(dummy, CreatureSpawnEvent.SpawnReason.NATURAL);

        manager.onCreatureSpawn(naturalEvent);

        assertTrue(naturalEvent.isCancelled(), "Natural spawn event should be cancelled");
        assertTrue(mobSpawned.get(), "Custom lunar mob should have been spawned");
        assertEquals("lunar_crawler", spawnedMobIds.getFirst());
        assertEquals(1, manager.getSuccessfulSpawns());
        assertEquals(1, manager.getTotalAttempts());

        // Non-natural spawn reason should NOT be intercepted
        CreatureSpawnEvent spawnerEggEvent = new CreatureSpawnEvent(dummy, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        manager.onCreatureSpawn(spawnerEggEvent);
        assertFalse(spawnerEggEvent.isCancelled());
    }

    @Test
    void priorityOrderingEvaluatesHigherPriorityFirst() {
        RandomSpawnManager manager = new RandomSpawnManager();
        manager.setGlobalEnabled(true);

        RandomSpawnRule lowPriority = RandomSpawnRule.builder("low", "low_mob")
                .priority(10)
                .enabled(true)
                .build();

        RandomSpawnRule highPriority = RandomSpawnRule.builder("high", "high_mob")
                .priority(100)
                .enabled(true)
                .build();

        manager.registerRule(lowPriority);
        manager.registerRule(highPriority);

        List<RandomSpawnRule> sorted = manager.getActiveRules(SpawnAction.REPLACE);
        assertEquals(2, sorted.size());
        assertEquals("high", sorted.get(0).id());
        assertEquals("low", sorted.get(1).id());
    }

    @Test
    void mobCapEnforcementSkipsSpawnWhenCapReached() {
        RandomSpawnManager manager = new RandomSpawnManager();
        manager.setGlobalEnabled(true);
        manager.setMobCapLimit(1); // Very low cap for testing

        // Mock world with 2 entities (already exceeds limit 1)
        World crowdedWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "haohan:lunar";
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft("lunar");
                    case "getLivingEntities" -> List.of(mockEntity(null), mockEntity(null));
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> null;
                });

        RandomSpawnRule rule = RandomSpawnRule.builder("rule_cap", "lunar_crawler")
                .worlds(Set.of("haohan:lunar"))
                .action(SpawnAction.REPLACE)
                .chance(1.0)
                .enabled(true)
                .build();
        manager.registerRule(rule);

        LivingEntity dummy = mockEntity(crowdedWorld);
        CreatureSpawnEvent event = new CreatureSpawnEvent(dummy, CreatureSpawnEvent.SpawnReason.NATURAL);

        manager.onCreatureSpawn(event);

        assertFalse(event.isCancelled(), "Should NOT cancel vanilla spawn if mob cap was reached");
        assertEquals(1, manager.getMobCapSkips(), "Should record mob cap skip");
        assertEquals(0, manager.getSuccessfulSpawns());
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        String key = name.contains(":") ? name.substring(name.indexOf(':') + 1) : name;
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(key);
                    case "getLivingEntities" -> List.of();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> null;
                });
    }

    private static LivingEntity mockEntity(World world) {
        Location loc = new Location(world, 0, 64, 0);
        return (LivingEntity) Proxy.newProxyInstance(
                RandomSpawnManagerTest.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "getType" -> EntityType.ZOMBIE;
                    case "equals" -> proxy == args[0];
                    default -> null;
                }
        );
    }
}
