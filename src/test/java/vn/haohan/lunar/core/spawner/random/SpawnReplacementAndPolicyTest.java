package vn.haohan.lunar.core.spawner.random;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SpawnReplacementAndPolicyTest {

    private World lunarWorld;
    private World overworld;

    @BeforeEach
    void setUp() {
        lunarWorld = mockWorld("haohan:lunar");
        overworld = mockWorld("minecraft:overworld");
    }

    @Test
    void testDenyActionCancelsVanillaSpawn() {
        RandomSpawnManager manager = new RandomSpawnManager();
        manager.setGlobalEnabled(true);

        // Deny vanilla mobs from spawning in lunar dimension
        RandomSpawnRule denyRule = RandomSpawnRule.builder("deny_lunar_vanilla", "none")
                .worlds(Set.of("haohan:lunar"))
                .action(SpawnAction.DENY)
                .chance(1.0)
                .enabled(true)
                .build();
        manager.registerRule(denyRule);

        Location lunarLoc = new Location(lunarWorld, 0, 64, 0);
        LivingEntity vanillaEntity = mockLiving(lunarLoc);
        CreatureSpawnEvent event = new CreatureSpawnEvent(vanillaEntity, CreatureSpawnEvent.SpawnReason.NATURAL);

        manager.onCreatureSpawn(event);

        assertTrue(event.isCancelled(), "Deny rule should cancel vanilla spawn in lunar dimension");
        assertEquals(1, manager.getDeniedSpawns());
        assertEquals(0, manager.getSuccessfulSpawns());
    }

    @Test
    void testReasonFilteringIgnoresArtificialReasons() {
        RandomSpawnManager manager = new RandomSpawnManager();
        manager.setGlobalEnabled(true);

        RandomSpawnRule replaceRule = RandomSpawnRule.builder("replace_zombie", "lunar_zombie")
                .worlds(Set.of("haohan:lunar"))
                .action(SpawnAction.REPLACE)
                .chance(1.0)
                .enabled(true)
                .build();
        manager.registerRule(replaceRule);

        Location lunarLoc = new Location(lunarWorld, 0, 64, 0);
        LivingEntity vanillaEntity = mockLiving(lunarLoc);

        // Artificial spawn reason: SPAWNER_EGG
        CreatureSpawnEvent eggEvent = new CreatureSpawnEvent(vanillaEntity, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        manager.onCreatureSpawn(eggEvent);
        assertFalse(eggEvent.isCancelled(), "Artificial SPAWNER_EGG reason should be ignored");

        // Artificial spawn reason: COMMAND
        CreatureSpawnEvent cmdEvent = new CreatureSpawnEvent(vanillaEntity, CreatureSpawnEvent.SpawnReason.COMMAND);
        manager.onCreatureSpawn(cmdEvent);
        assertFalse(cmdEvent.isCancelled(), "Artificial COMMAND reason should be ignored");

        // Natural spawn reason: NATURAL
        AtomicBoolean customSpawned = new AtomicBoolean(false);
        manager.setCustomSpawner((id, loc) -> customSpawned.set(true));

        CreatureSpawnEvent naturalEvent = new CreatureSpawnEvent(vanillaEntity, CreatureSpawnEvent.SpawnReason.NATURAL);
        manager.onCreatureSpawn(naturalEvent);
        assertTrue(naturalEvent.isCancelled(), "Natural spawn should be replaced");
        assertTrue(customSpawned.get(), "Custom spawner should be invoked for NATURAL replacement");
    }

    @Test
    void testSpecificAllowedReasonsConfiguration() {
        RandomSpawnRule customReasonRule = RandomSpawnRule.builder("raid_reinforcement_rule", "lunar_raider")
                .worlds(Set.of("haohan:lunar"))
                .spawnReasons(Set.of(CreatureSpawnEvent.SpawnReason.RAID, CreatureSpawnEvent.SpawnReason.PATROL))
                .action(SpawnAction.REPLACE)
                .chance(1.0)
                .enabled(true)
                .build();

        assertTrue(customReasonRule.matchesReason(CreatureSpawnEvent.SpawnReason.RAID));
        assertTrue(customReasonRule.matchesReason(CreatureSpawnEvent.SpawnReason.PATROL));
        assertFalse(customReasonRule.matchesReason(CreatureSpawnEvent.SpawnReason.NATURAL));
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

    private static LivingEntity mockLiving(Location loc) {
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
}
