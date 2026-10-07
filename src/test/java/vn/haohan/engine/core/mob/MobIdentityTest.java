package vn.haohan.engine.core.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobIdentityTest {

    @Test
    void identityRoundTripsThroughPersistentData() {
        PersistentDataContainer container = fakeContainer();
        LivingEntity entity = fakeEntity(container);
        MobIdentity expected = new MobIdentity("Lunar_Warden", "1.0.0", Optional.of("spawn-42"));

        MobIdentity.write(entity, expected);

        assertEquals(expected, MobIdentity.read(entity).orElseThrow());
        assertTrue(MobIdentity.isLunarMob(entity));
    }

    @Test
    void changingEntityNameDoesNotAffectIdentityAndMissingDataIsRejected() {
        PersistentDataContainer container = fakeContainer();
        LivingEntity entity = fakeEntity(container);
        MobIdentity.write(entity, new MobIdentity("warden", "2"));

        assertEquals("warden", MobIdentity.read(entity).orElseThrow().mobId());
        container.remove(MobIdentity.mobIdKey());
        assertFalse(MobIdentity.isLunarMob(entity));
    }

    @Test
    void ownNamespaceIsStable() {
        assertEquals("haohanlunar", MobIdentity.mobIdKey().getNamespace());
        assertEquals("mob_id", MobIdentity.mobIdKey().getKey());
    }

    @SuppressWarnings("unchecked")
    private static PersistentDataContainer fakeContainer() {
        Map<Object, Object> values = new HashMap<>();
        return (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "set" -> { values.put(args[0], args[2]); yield null; }
                    case "get" -> values.get(args[0]);
                    case "has" -> values.containsKey(args[0]);
                    case "remove" -> { values.remove(args[0]); yield null; }
                    case "isEmpty" -> values.isEmpty();
                    case "getKeys" -> java.util.Set.copyOf(values.keySet());
                    case "getAdapterContext" -> null;
                    case "toString" -> "FakePersistentDataContainer";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity fakeEntity(PersistentDataContainer container) {
        return (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) {
                        return container;
                    }
                    if (method.getName().equals("toString")) {
                        return "FakeLivingEntity";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}

