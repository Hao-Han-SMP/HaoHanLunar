package vn.haohan.lunar.core.skill.targeter;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;
import vn.haohan.lunar.core.skill.SkillCastContext;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometricTargeterTest {

    @Test
    void targetFilterSortingAndLimits() {
        World mockWorld = createMockWorld();
        ActiveLunarMob mob = sampleMob(mockWorld, 0, 0, 0);
        SkillCastContext context = sampleContext(mob);

        LivingEntity e1 = createMockLivingEntity(mockWorld, 5, 0, 0, 50.0, EntityType.ZOMBIE);
        LivingEntity e2 = createMockLivingEntity(mockWorld, 10, 0, 0, 100.0, EntityType.ZOMBIE);
        LivingEntity e3 = createMockLivingEntity(mockWorld, 2, 0, 0, 20.0, EntityType.ZOMBIE);

        // Sort NEAREST
        List<LivingEntity> nearest = TargetFilter.filterAndSort(
                List.of(e1, e2, e3), context, Map.of("sort", "NEAREST")
        );
        assertEquals(3, nearest.size());
        assertEquals(e3, nearest.get(0)); // 2 blocks away
        assertEquals(e1, nearest.get(1)); // 5 blocks away
        assertEquals(e2, nearest.get(2)); // 10 blocks away

        // Sort FURTHEST with limit 1
        List<LivingEntity> furthest = TargetFilter.filterAndSort(
                List.of(e1, e2, e3), context, Map.of("sort", "FURTHEST", "limit", 1)
        );
        assertEquals(1, furthest.size());
        assertEquals(e2, furthest.get(0)); // 10 blocks away

        // Sort HIGHEST_HEALTH
        List<LivingEntity> health = TargetFilter.filterAndSort(
                List.of(e1, e2, e3), context, Map.of("sort", "HIGHEST_HEALTH")
        );
        assertEquals(e2, health.get(0)); // 100 hp
        assertEquals(e1, health.get(1)); // 50 hp
        assertEquals(e3, health.get(2)); // 20 hp
    }

    @Test
    void targetFilterIgnoresCasterAndCreative() {
        World mockWorld = createMockWorld();
        ActiveLunarMob mob = sampleMob(mockWorld, 0, 0, 0);
        SkillCastContext context = sampleContext(mob);

        Player creativePlayer = createMockPlayer(mockWorld, 2, 0, 0, GameMode.CREATIVE);
        Player survivalPlayer = createMockPlayer(mockWorld, 3, 0, 0, GameMode.SURVIVAL);

        List<LivingEntity> result = TargetFilter.filterAndSort(
                List.of(mob.entity(), creativePlayer, survivalPlayer),
                context,
                Map.of()
        );

        assertEquals(1, result.size());
        assertEquals(survivalPlayer, result.get(0));
    }

    @Test
    void targeterRegistryParsesInlineGeometricSyntax() {
        TargeterRegistry registry = new TargeterRegistry();

        TargeterRegistry.ParsedTargeterCall cone = registry.parse("@Cone{angle=90;radius=12}");
        assertEquals("cone", cone.targeterName());
        assertEquals(90.0, ((Number) cone.parameters().get("angle")).doubleValue());
        assertEquals(12.0, ((Number) cone.parameters().get("radius")).doubleValue());

        TargeterRegistry.ParsedTargeterCall ring = registry.parse("@Ring{radius=15;width=4}");
        assertEquals("ring", ring.targeterName());
        assertEquals(15.0, ((Number) ring.parameters().get("radius")).doubleValue());
        assertEquals(4.0, ((Number) ring.parameters().get("width")).doubleValue());

        TargeterRegistry.ParsedTargeterCall line = registry.parse("@Line{length=25;width=3}");
        assertEquals("line", line.targeterName());
        assertEquals(25.0, ((Number) line.parameters().get("length")).doubleValue());
        assertEquals(3.0, ((Number) line.parameters().get("width")).doubleValue());

        TargeterRegistry.ParsedTargeterCall sphere = registry.parse("@Sphere{radius=18}");
        assertEquals("sphere", sphere.targeterName());
        assertEquals(18.0, ((Number) sphere.parameters().get("radius")).doubleValue());

        TargeterRegistry.ParsedTargeterCall cyl = registry.parse("@Cylinder{radius=10;height=6}");
        assertEquals("cylinder", cyl.targeterName());
        assertEquals(10.0, ((Number) cyl.parameters().get("radius")).doubleValue());
        assertEquals(6.0, ((Number) cyl.parameters().get("height")).doubleValue());

        TargeterRegistry.ParsedTargeterCall threat = registry.parse("@ThreatTableTargets{sort=HIGHEST_THREAT;limit=3}");
        assertEquals("threattabletargets", threat.targeterName());
        assertEquals("HIGHEST_THREAT", threat.parameters().get("sort"));
        assertEquals(3L, ((Number) threat.parameters().get("limit")).longValue());
    }

    @Test
    void geometricTargetersRegisteredAndResolvable() {
        TargeterRegistry registry = new TargeterRegistry();
        World mockWorld = createMockWorld();
        ActiveLunarMob mob = sampleMob(mockWorld, 0, 64, 0);
        SkillCastContext context = sampleContext(mob);

        assertTrue(registry.hasTargeter("cone"));
        assertTrue(registry.hasTargeter("ring"));
        assertTrue(registry.hasTargeter("line"));
        assertTrue(registry.hasTargeter("sphere"));
        assertTrue(registry.hasTargeter("cylinder"));
        assertTrue(registry.hasTargeter("threattabletargets"));

        Collection<LivingEntity> coneTargets = registry.resolveEntities("@Cone{angle=90;radius=12}", context);
        assertNotNull(coneTargets);

        Collection<LivingEntity> ringTargets = registry.resolveEntities("@Ring{radius=10;width=3}", context);
        assertNotNull(ringTargets);

        Collection<LivingEntity> lineTargets = registry.resolveEntities("@Line{length=10;width=2}", context);
        assertNotNull(lineTargets);

        Collection<LivingEntity> sphereTargets = registry.resolveEntities("@Sphere{radius=8}", context);
        assertNotNull(sphereTargets);

        Collection<LivingEntity> cylTargets = registry.resolveEntities("@Cylinder{radius=8;height=4}", context);
        assertNotNull(cylTargets);

        Collection<LivingEntity> threatTargets = registry.resolveEntities("@ThreatTableTargets", context);
        assertNotNull(threatTargets);
    }

    private static World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("getNearbyEntities")) return List.of();
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private static LivingEntity createMockLivingEntity(World world, double x, double y, double z, double health, EntityType type) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getHealth")) return health;
                    if (method.getName().equals("getType")) return type;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player createMockPlayer(World world, double x, double y, double z, GameMode mode) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getHealth")) return 20.0;
                    if (method.getName().equals("getType")) return EntityType.PLAYER;
                    if (method.getName().equals("getGameMode")) return mode;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static SkillCastContext sampleContext(ActiveLunarMob mob) {
        SkillDefinition skill = new SkillDefinition("geom_skill", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100);
    }

    private static ActiveLunarMob sampleMob(World mockWorld, double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    Location loc = new Location(mockWorld, x, y, z);
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getEyeLocation")) return loc.clone().add(0, 1.8, 0);
                    if (method.getName().equals("getWorld")) return mockWorld;
                    if (method.getName().equals("getType")) return EntityType.IRON_GOLEM;
                    if (method.getName().equals("getHealth")) return 500.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
