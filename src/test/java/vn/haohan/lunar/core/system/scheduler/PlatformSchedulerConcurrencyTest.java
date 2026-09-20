package vn.haohan.lunar.core.system.scheduler;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.combat.threat.ThreatTable;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.core.mob.MobDefinition;
import vn.haohan.lunar.core.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PlatformSchedulerConcurrencyTest {

    @Test
    void testPlatformSchedulerFactoryDetection() {
        assertFalse(PlatformSchedulerFactory.isFoliaServer());
    }

    @Test
    void testTaskHandleState() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        TaskHandle handle = new TaskHandle() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        assertFalse(handle.isCancelled());
        handle.cancel();
        assertTrue(handle.isCancelled());
    }

    @Test
    void testMultiThreadedThreatTable20Threads() throws InterruptedException {
        ThreatTable threatTable = new ThreatTable(UUID.randomUUID());
        int threadCount = 20;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            players.add(UUID.randomUUID());
        }

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < operationsPerThread; i++) {
                        UUID p = players.get(random.nextInt(players.size()));
                        int op = random.nextInt(6);
                        switch (op) {
                            case 0 -> threatTable.addDamageThreat(p, random.nextDouble(10, 200), i);
                            case 1 -> threatTable.addHealThreat(p, random.nextDouble(5, 100), i);
                            case 2 -> threatTable.getThreat(p);
                            case 3 -> threatTable.evaluateTarget(i);
                            case 4 -> threatTable.snapshot();
                            case 5 -> threatTable.tickDecay(i, 20);
                        }
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Concurrent test timed out");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Concurrent operations threw errors: " + errors);
        assertTrue(threatTable.totalThreat() >= 0.0);
    }

    @Test
    void testMultiThreadedMobRegistry20Threads() throws InterruptedException {
        LunarMobManager mobManager = new LunarMobManager(e -> {});
        int threadCount = 20;
        int operationsPerThread = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < operationsPerThread; i++) {
                        UUID mobId = UUID.randomUUID();
                        LivingEntity mockEntity = createMockEntity(mobId);
                        MobDefinition def = new MobDefinition(new MobDefinitionId("boss_" + threadIdx),
                                EntityType.ZOMBIE, "Boss " + threadIdx, null, Map.of(), Map.of(), List.of(), null, Set.of());
                        ActiveLunarMob mob = new ActiveLunarMob(mockEntity, def, new LunarMobIdentity(def.id().value(), "1"));

                        mobManager.register(mob);
                        mobManager.get(mobId);
                        Collection<ActiveLunarMob> snapshot = mobManager.snapshot();
                        assertNotNull(snapshot);
                        if (random.nextBoolean()) {
                            mobManager.unregister(mobId);
                        }
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "Mob registry test timed out");
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Mob registry concurrency threw errors: " + errors);
    }

    private LivingEntity createMockEntity(UUID uuid) {
        World mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "hashCode" -> 123;
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    default -> null;
                }
        );

        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> new Location(mockWorld, 0, 64, 0);
                    case "getWorld" -> mockWorld;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getType" -> EntityType.ZOMBIE;
                    case "hashCode" -> uuid.hashCode();
                    case "equals" -> args.length > 0 && args[0] == proxy;
                    default -> null;
                }
        );
    }
}
