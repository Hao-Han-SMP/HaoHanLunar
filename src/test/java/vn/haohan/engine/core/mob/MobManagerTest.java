package vn.haohan.engine.core.mob;

import vn.haohan.engine.core.mob.MobManager;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.engine.api.system.mob.MobDefinition;
import vn.haohan.engine.api.system.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MobManagerTest {

    @Test
    void registersFindsAndUnregistersActiveMobWithModelCleanup() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = entity(uuid, true, false);
        MobDefinition definition = definition("warden");
        AtomicInteger cleanupCalls = new AtomicInteger();
        MobManager manager = new MobManager(ignored -> cleanupCalls.incrementAndGet());
        ActiveMob active = new ActiveMob(entity, definition,
                new MobIdentity("warden", "1"));

        manager.register(active);

        assertEquals(active, manager.get(uuid));
        assertEquals(1, manager.findByDefinitionId("WARDEN").size());
        assertEquals(active, manager.unregister(uuid));
        assertEquals(1, cleanupCalls.get());
        assertEquals(0, manager.snapshot().size());
    }

    @Test
    void duplicateRegistrationAndInvalidCleanupAreHandled() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        AtomicInteger cleanupCalls = new AtomicInteger();
        MobManager manager = new MobManager(ignored -> cleanupCalls.incrementAndGet());
        manager.register(new ActiveMob(entity(first, true, false), definition("one"),
                new MobIdentity("one", "1")));
        assertThrows(IllegalStateException.class, () -> manager.register(new ActiveMob(
                entity(first, true, false), definition("one"), new MobIdentity("one", "1"))));
        manager.register(new ActiveMob(entity(second, false, true), definition("two"),
                new MobIdentity("two", "1")));

        assertEquals(1, manager.cleanupInvalidEntities());
        assertEquals(1, cleanupCalls.get());
        assertEquals(1, manager.snapshot().size());
    }

    @Test
    void cleanupAllRemovesEveryTrackedEntry() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        MobManager manager = new MobManager(ignored -> cleanupCalls.incrementAndGet());
        manager.register(new ActiveMob(entity(UUID.randomUUID(), true, false), definition("one"),
                new MobIdentity("one", "1")));
        manager.register(new ActiveMob(entity(UUID.randomUUID(), true, false), definition("two"),
                new MobIdentity("two", "1")));

        assertEquals(2, manager.cleanupAll());
        assertEquals(2, cleanupCalls.get());
        assertEquals(0, manager.snapshot().size());
    }

    private static MobDefinition definition(String id) {
        return new MobDefinition(new MobDefinitionId(id), EntityType.IRON_GOLEM, id,
                                 null, Map.of(), Map.of(), List.of(), null, Set.of());
    }

    private static LivingEntity entity(UUID uuid, boolean valid, boolean dead) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    case "toString" -> "FakeLivingEntity[" + uuid + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

