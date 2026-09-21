package vn.haohan.lunar.core.combat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.mob.MobAttributeDefinition;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.mob.options.MobOptions;
import vn.haohan.lunar.api.system.combat.DamageContext;
import vn.haohan.lunar.api.system.combat.DamagePipeline;
import vn.haohan.lunar.api.system.combat.DamageResult;
import vn.haohan.lunar.api.system.combat.DamageType;
import vn.haohan.lunar.api.system.combat.raycast.AABB;
import vn.haohan.lunar.api.system.combat.raycast.Ray;
import vn.haohan.lunar.api.system.combat.raycast.RaycastEngine;
import vn.haohan.lunar.api.system.combat.raycast.RaycastHit;
import vn.haohan.lunar.api.system.combat.skill.aura.*;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RaycastAuraAndCombatSafetyTest {

    private World mockWorld;

    @BeforeEach
    void setUp() {
        mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    return null;
                }
        );
    }

    private LivingEntity createMockLivingEntity(Location loc) {
        AtomicReference<Location> currentLoc = new AtomicReference<>(loc);
        UUID uuid = UUID.randomUUID();

        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("isInvulnerable")) return false;
                    if (method.getName().equals("getLocation")) return currentLoc.get().clone();
                    if (method.getName().equals("getWorld")) return currentLoc.get().getWorld();
                    if (method.getName().equals("getWidth")) return 0.6;
                    if (method.getName().equals("getHeight")) return 1.8;
                    if (method.getName().equals("damage")) return null;
                    if (method.getName().equals("equals")) return args[0] == proxy;
                    return null;
                }
        );
    }

    @Test
    void testAABBRayIntersectionAndContainment() {
        AABB box = AABB.of(0, 0, 0, 2, 2, 2);

        assertTrue(box.contains(new Vector(1, 1, 1)));
        assertFalse(box.contains(new Vector(3, 1, 1)));

        // Ray pointing directly into box
        Ray directHit = Ray.of(new Vector(-5, 1, 1), new Vector(1, 0, 0), 20.0);
        OptionalDouble hit = box.intersectsRay(directHit);
        assertTrue(hit.isPresent());
        assertEquals(5.0, hit.getAsDouble(), 0.001);

        // Ray pointing away from box
        Ray missRay = Ray.of(new Vector(-5, 1, 1), new Vector(-1, 0, 0), 20.0);
        assertFalse(box.intersectsRay(missRay).isPresent());

        // Ray too short to reach box
        Ray shortRay = Ray.of(new Vector(-5, 1, 1), new Vector(1, 0, 0), 3.0);
        assertFalse(box.intersectsRay(shortRay).isPresent());
    }

    @Test
    void testRaycastEngineEntityPiercingAndDistanceSorting() {
        LivingEntity ent1 = createMockLivingEntity(new Location(mockWorld, 10.0, 0.0, 0.0));
        LivingEntity ent2 = createMockLivingEntity(new Location(mockWorld, 5.0, 0.0, 0.0));
        LivingEntity ent3 = createMockLivingEntity(new Location(mockWorld, 15.0, 0.0, 0.0));

        List<LivingEntity> entities = List.of(ent1, ent2, ent3);

        Ray ray = Ray.of(new Vector(0, 0.5, 0), new Vector(1, 0, 0), 50.0);

        // Test with maxPierces = 2
        List<RaycastHit<LivingEntity>> hits = RaycastEngine.raycastEntities(ray, entities, null, 0.2, 2);

        assertEquals(2, hits.size());
        // Closest should be ent2 (at x=5), then ent1 (at x=10)
        assertEquals(ent2, hits.get(0).target());
        assertEquals(ent1, hits.get(1).target());
        assertTrue(hits.get(0).distance() < hits.get(1).distance());
    }

    @Test
    void testRaycastEngineVoxelRaymarcherDDA() {
        // Mock a 3D wall at x = 7
        Set<Vector> solidBlocks = Set.of(
                new Vector(7, 0, 0),
                new Vector(7, 1, 0),
                new Vector(7, 2, 0)
        );

        Ray ray = Ray.of(new Vector(0.5, 1.5, 0.5), new Vector(1, 0, 0), 20.0);

        Optional<RaycastHit<Vector>> hit = RaycastEngine.raymarchVoxels(ray, solidBlocks::contains);

        assertTrue(hit.isPresent());
        assertEquals(new Vector(7, 1, 0), hit.get().target());
        assertEquals(new Vector(-1, 0, 0), hit.get().normal());
        assertEquals(6.5, hit.get().distance(), 0.01);
    }

    @Test
    void testRaycastEngineVectorReflection() {
        Vector incident = new Vector(1, -1, 0).normalize();
        Vector normal = new Vector(0, 1, 0); // Flat ground

        Vector bounce = RaycastEngine.reflect(incident, normal, 0.8);

        // Y component should be reversed from negative to positive, and scaled by 0.8
        assertTrue(bounce.getY() > 0);
        assertTrue(bounce.getX() > 0);
        assertEquals(0.8, bounce.length(), 0.001);
    }

    @Test
    void testAntiRecursionGuardInDamagePipeline() {
        DamagePipeline pipeline = new DamagePipeline();

        AtomicInteger callCount = new AtomicInteger(0);
        LivingEntity victim = createMockLivingEntity(new Location(mockWorld, 0, 0, 0));

        // Register a post-handler that intentionally creates a recursion loop by re-executing damage
        pipeline.registerPostHandler((ctx, dmg) -> {
            callCount.incrementAndGet();
            pipeline.execute(ctx);
        });

        DamageContext ctx = DamageContext.builder()
                .victim(victim)
                .baseDamage(10.0)
                .damageType(DamageType.PHYSICAL)
                .cause(EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                .build();

        DamageResult initialResult = pipeline.execute(ctx);

        // Pipeline should execute without throwing StackOverflowError, and recursion should be cleanly capped
        assertTrue(initialResult.executed());
        // Max call depth is 6, so callCount should be bounded (<= 6)
        assertTrue(callCount.get() <= 6);
        assertTrue(callCount.get() >= 5);
    }

    @Test
    void testAuraStackingAndExpirationLifecycle() {
        AuraScheduler scheduler = new AuraScheduler();
        LivingEntity entity = createMockLivingEntity(new Location(mockWorld, 0, 0, 0));
        UUID entityId = entity.getUniqueId();
        AuraAttachment attachment = AuraAttachment.ofEntity(entity);

        AuraDefinition auraDef = AuraDefinition.builder("bleed")
                .durationTicks(100L)
                .intervalTicks(20L)
                .maxStacks(3)
                .stackMode(StackMode.ADD_STACK)
                .build();

        // 1. Initial application: 1 stack
        ActiveAura aura = scheduler.applyAura(auraDef, attachment, entityId, 10L);
        assertEquals(1, aura.currentStacks());
        assertEquals(110L, aura.endTick());

        // 2. Add stack: 2 stacks, refreshes duration
        scheduler.applyAura(auraDef, attachment, entityId, 30L);
        assertEquals(2, aura.currentStacks());
        assertEquals(130L, aura.endTick());

        // 3. Add stack: 3 stacks
        scheduler.applyAura(auraDef, attachment, entityId, 50L);
        assertEquals(3, aura.currentStacks());

        // 4. Add stack beyond maxStacks: capped at 3
        scheduler.applyAura(auraDef, attachment, entityId, 70L);
        assertEquals(3, aura.currentStacks());

        // 5. Tick before expiration
        scheduler.tick(100L);
        assertTrue(scheduler.hasAura(entityId, "bleed"));

        // 6. Tick past expiration (170L)
        scheduler.tick(180L);
        assertFalse(scheduler.hasAura(entityId, "bleed"));
    }

    @Test
    void testAuraSchedulerCancelAllOnEntityCleanup() {
        AuraScheduler scheduler = new AuraScheduler();
        LivingEntity entityAEnt = createMockLivingEntity(new Location(mockWorld, 0, 0, 0));
        LivingEntity entityBEnt = createMockLivingEntity(new Location(mockWorld, 0, 0, 0));
        UUID entityA = entityAEnt.getUniqueId();
        UUID entityB = entityBEnt.getUniqueId();

        AuraDefinition aura1 = AuraDefinition.builder("frostbite").durationTicks(200L).build();
        AuraDefinition aura2 = AuraDefinition.builder("ignite").durationTicks(200L).build();

        scheduler.applyAura(aura1, AuraAttachment.ofEntity(entityAEnt), entityA, 0L);
        scheduler.applyAura(aura2, AuraAttachment.ofEntity(entityAEnt), entityA, 0L);
        scheduler.applyAura(aura1, AuraAttachment.ofEntity(entityBEnt), entityB, 0L);

        assertEquals(3, scheduler.size());

        // Entity A dies/unregisters
        int cancelled = scheduler.cancelAll(entityA);
        assertEquals(2, cancelled);

        assertFalse(scheduler.hasAura(entityA, "frostbite"));
        assertFalse(scheduler.hasAura(entityA, "ignite"));
        assertTrue(scheduler.hasAura(entityB, "frostbite"));
        assertEquals(1, scheduler.size());
    }

    @Test
    void testConcurrentRaycastingUnderHighLoad() throws InterruptedException {
        int threads = 8;
        int raycastsPerThread = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        List<LivingEntity> entities = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            entities.add(createMockLivingEntity(new Location(mockWorld, i * 2.0, 0, 0)));
        }

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < raycastsPerThread; i++) {
                        Ray ray = Ray.of(new Vector(0, 0.5, 0), new Vector(1, 0, 0), 100.0);
                        List<RaycastHit<LivingEntity>> hits = RaycastEngine.raycastEntities(ray, entities, null, 0.2, 5);
                        assertFalse(hits.isEmpty());
                        assertTrue(hits.size() <= 5);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }
}
