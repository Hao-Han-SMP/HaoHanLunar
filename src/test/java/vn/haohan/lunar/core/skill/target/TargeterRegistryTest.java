package vn.haohan.lunar.core.skill.target;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargeterRegistryTest {

    @Test
    void builtinsResolveSelfTargetAndLocation() {
        World world = mockWorld();
        LivingEntity caster = mockLiving(world, true, false);
        LivingEntity target = mockLiving(world, true, false);
        TargeterContext context = new TargeterContext(caster, target, new Location(world, 0, 64, 0), 10, 10);
        TargeterRegistry registry = new TargeterRegistry();

        assertEquals(caster, registry.resolve("SELF", context).getFirst().entity());
        assertEquals(target, registry.resolve("target", context).getFirst().entity());
        assertTrue(registry.resolve("location", context).getFirst().locationOptional().isPresent());
    }

    @Test
    void deadOrInvalidCasterAndTargetReturnEmpty() {
        World world = mockWorld();
        LivingEntity deadCaster = mockLiving(world, true, true);
        LivingEntity deadTarget = mockLiving(world, true, true);
        TargeterContext context = new TargeterContext(deadCaster, deadTarget, new Location(world, 0, 64, 0), 10, 10);
        TargeterRegistry registry = new TargeterRegistry();

        assertTrue(registry.resolve("self", context).isEmpty(), "Dead caster should resolve to no target");
        assertTrue(registry.resolve("target", context).isEmpty(), "Dead target should resolve to no target");

        // Target in different world
        World otherWorld = mockWorld();
        LivingEntity otherWorldTarget = mockLiving(otherWorld, true, false);
        LivingEntity aliveCaster = mockLiving(world, true, false);
        TargeterContext crossWorldContext = new TargeterContext(aliveCaster, otherWorldTarget, new Location(world, 0, 64, 0), 10, 10);
        assertTrue(registry.resolve("target", crossWorldContext).isEmpty(), "Target in different world should resolve to empty");
    }

    @Test
    void radiusAndResultLimitsAndWorldFilteringAreEnforced() {
        World world = mockWorld();
        LivingEntity caster = mockLiving(world, true, false);
        assertThrows(IllegalArgumentException.class, () -> new TargeterContext(caster, null, 129, 1));
        assertThrows(IllegalArgumentException.class, () -> new TargeterContext(caster, null, 10, 129));
        assertThrows(IllegalArgumentException.class, () -> new TargeterContext(caster, null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> new TargeterContext(caster, null, 10, 0));
    }

    @Test
    void registryReturnsImmutableSnapshotsAndFiltersSpectators() {
        List<Entity> worldEntities = new ArrayList<>();
        World world = mockWorld(worldEntities);
        LivingEntity caster = mockLiving(world, true, false);
        Player spectator = mockPlayer(world, GameMode.SPECTATOR, true, false);
        Player survival = mockPlayer(world, GameMode.SURVIVAL, true, false);
        worldEntities.add(spectator);
        worldEntities.add(survival);

        TargeterContext context = new TargeterContext(caster, null, new Location(world, 0, 64, 0), 20, 10);
        TargeterRegistry registry = new TargeterRegistry();

        assertEquals(6, registry.snapshot().size());

        List<TargetRef> resolved = registry.resolve("players_in_radius", context);
        assertEquals(1, resolved.size(), "Spectator player must be filtered out");
        assertEquals(survival, resolved.getFirst().entity());

        assertThrows(UnsupportedOperationException.class, () -> registry.resolve("self", context).add(null));
    }

    @Test
    void livingEntitiesInRadiusFiltersDeadAndHonorsMaxResults() {
        List<Entity> worldEntities = new ArrayList<>();
        World world = mockWorld(worldEntities);
        LivingEntity caster = mockLiving(world, true, false);

        LivingEntity alive1 = mockLiving(world, true, false);
        LivingEntity alive2 = mockLiving(world, true, false);
        LivingEntity dead1 = mockLiving(world, true, true);
        LivingEntity invalid1 = mockLiving(world, false, false);

        worldEntities.add(alive1);
        worldEntities.add(alive2);
        worldEntities.add(dead1);
        worldEntities.add(invalid1);

        TargeterRegistry registry = new TargeterRegistry();

        // Limit to 1 max result
        TargeterContext contextMax1 = new TargeterContext(caster, null, new Location(world, 0, 64, 0), 20, 1);
        List<TargetRef> resolvedMax1 = registry.resolve("living_entities_in_radius", contextMax1);
        assertEquals(1, resolvedMax1.size(), "Must truncate at maxResults");
        assertEquals(alive1, resolvedMax1.getFirst().entity());

        // Max 5 results
        TargeterContext contextMax5 = new TargeterContext(caster, null, new Location(world, 0, 64, 0), 20, 5);
        List<TargetRef> resolvedMax5 = registry.resolve("living_entities_in_radius", contextMax5);
        assertEquals(2, resolvedMax5.size(), "Should filter out dead and invalid entities");
        assertTrue(resolvedMax5.stream().anyMatch(ref -> ref.entity().equals(alive1)));
        assertTrue(resolvedMax5.stream().anyMatch(ref -> ref.entity().equals(alive2)));
    }

    @Test
    void randomPlayerPicksFromValidPlayersOrReturnsEmpty() {
        List<Entity> worldEntities = new ArrayList<>();
        World world = mockWorld(worldEntities);
        LivingEntity caster = mockLiving(world, true, false);

        TargeterRegistry registry = new TargeterRegistry();
        TargeterContext context = new TargeterContext(caster, null, new Location(world, 0, 64, 0), 20, 5);

        // When no players
        assertTrue(registry.resolve("random_player", context).isEmpty());

        // When 1 survival player exists
        Player player = mockPlayer(world, GameMode.SURVIVAL, true, false);
        worldEntities.add(player);
        List<TargetRef> result = registry.resolve("random_player", context);
        assertEquals(1, result.size());
        assertEquals(player, result.getFirst().entity());
    }

    @Test
    void customTargeterRegistrationAndValidation() {
        TargeterRegistry registry = new TargeterRegistry();

        // Custom targeter
        registry.register("custom_forward", ctx -> List.of(TargetRef.location(ctx.origin().add(1, 0, 0))));
        assertTrue(registry.get("custom_forward").isPresent());
        assertTrue(registry.get("CUSTOM_FORWARD").isPresent());

        // Duplicate registration rejected
        assertThrows(IllegalArgumentException.class, () ->
                registry.register("custom_forward", ctx -> List.of()));

        // Blank or null ID rejected
        assertThrows(NullPointerException.class, () -> registry.register(null, ctx -> List.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("   ", ctx -> List.of()));
        assertThrows(NullPointerException.class, () -> registry.register("valid", null));

        // Unknown targeter returns empty list
        assertTrue(registry.resolve("non_existent_targeter", new TargeterContext(mockLiving(mockWorld(), true, false), null, 10, 10)).isEmpty());
    }

    @Test
    void targetRefAndContextContracts() {
        World world = mockWorld();
        LivingEntity living = mockLiving(world, true, false);
        Location loc = new Location(world, 10, 64, 20);

        TargetRef entityRef = TargetRef.entity(living);
        assertTrue(entityRef.entityOptional().isPresent());
        assertTrue(entityRef.locationOptional().isEmpty());

        TargetRef locRef = TargetRef.location(loc);
        assertTrue(locRef.entityOptional().isEmpty());
        assertTrue(locRef.locationOptional().isPresent());

        // Location clone immutability
        Location extracted = locRef.locationOptional().get();
        extracted.setX(999);
        assertEquals(10.0, locRef.location().getX(), 0.001, "TargetRef internal location must not mutate");

        // Invalid TargetRef
        assertThrows(IllegalArgumentException.class, () -> new TargetRef(null, null));

        // TargeterContext origin clone immutability
        TargeterContext ctx = new TargeterContext(living, null, loc, 15, 10);
        loc.setX(888);
        assertEquals(10.0, ctx.origin().getX(), 0.001, "TargeterContext origin must be cloned on construction");
    }

    // --- Mock Helpers ---

    private static World mockWorld() {
        return mockWorld(new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static World mockWorld(List<Entity> entityStore) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("toString")) return "FakeWorld@" + Integer.toHexString(System.identityHashCode(proxy));
                    if (method.getName().equals("getNearbyEntities")) {
                        Predicate<Entity> predicate = args.length > 4 && args[4] instanceof Predicate<?>
                                ? (Predicate<Entity>) args[4] : e -> true;
                        return entityStore.stream().filter(predicate).toList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity mockLiving(World world, boolean valid, boolean dead) {
        UUID uuid = UUID.randomUUID();
        Location location = new Location(world, 0, 64, 0);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> location.clone();
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    case "toString" -> "FakeLivingEntity[" + uuid + "]";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player mockPlayer(World world, GameMode gameMode, boolean valid, boolean dead) {
        UUID uuid = UUID.randomUUID();
        Location location = new Location(world, 0, 64, 0);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> location.clone();
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    case "getGameMode" -> gameMode;
                    case "toString" -> "FakePlayer[" + uuid + "]";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
