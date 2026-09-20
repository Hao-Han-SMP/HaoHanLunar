package vn.haohan.lunar.core.lunar.boss.warden;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelEngineMobAdapterTest {

    @Test
    void attachesAndDestroysThroughBridge() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = entity(uuid);
        FakeBridge bridge = new FakeBridge();
        ModelEngineMobAdapter adapter = new ModelEngineMobAdapter(bridge, ignored -> { });
        ActiveLunarMob mob = mob(entity);

        assertTrue(adapter.attach(mob, "warden_model"));
        assertTrue(adapter.isAttached(uuid));
        adapter.destroy(mob);

        assertFalse(adapter.isAttached(uuid));
        assertEquals(1, bridge.destroyCalls.get());
    }

    @Test
    void missingModelAndBridgeFailureAreReportedWithoutThrowing() {
        List<String> errors = new java.util.ArrayList<>();
        ModelEngineMobAdapter adapter = new ModelEngineMobAdapter(new FakeBridge() {
            @Override public boolean attach(LivingEntity entity, String modelId) { return false; }
        }, errors::add);

        assertFalse(adapter.attach(mob(entity(UUID.randomUUID())), "missing"));
        assertFalse(adapter.attach(mob(entity(UUID.randomUUID())), " "));
        assertEquals(2, errors.size());
    }

    private static ActiveLunarMob mob(LivingEntity entity) {
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }

    private static LivingEntity entity(UUID uuid) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static class FakeBridge implements ModelEngineMobAdapter.ModelEngineBridge {
        private final AtomicInteger destroyCalls = new AtomicInteger();
        @Override public boolean attach(LivingEntity entity, String modelId) { return true; }
        @Override public void destroy(LivingEntity entity) { destroyCalls.incrementAndGet(); }
    }
}
