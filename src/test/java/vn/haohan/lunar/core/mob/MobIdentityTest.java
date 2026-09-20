package vn.haohan.lunar.core.mob;

import vn.haohan.lunar.api.mob.*;

import vn.haohan.lunar.core.mob.LunarMobIdentity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
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
        LunarMobIdentity expected = new LunarMobIdentity("Lunar_Warden", "1.0.0", Optional.of("spawn-42"));

        LunarMobIdentity.write(entity, expected);

        assertEquals(expected, LunarMobIdentity.read(entity).orElseThrow());
        assertTrue(LunarMobIdentity.isLunarMob(entity));
    }

    @Test
    void changingEntityNameDoesNotAffectIdentityAndMissingDataIsRejected() {
        PersistentDataContainer container = fakeContainer();
        LivingEntity entity = fakeEntity(container);
        LunarMobIdentity.write(entity, new LunarMobIdentity("warden", "2"));

        assertEquals("warden", LunarMobIdentity.read(entity).orElseThrow().mobId());
        container.remove(LunarMobIdentity.mobIdKey());
        assertFalse(LunarMobIdentity.isLunarMob(entity));
    }

    @Test
    void ownNamespaceIsStable() {
        assertEquals("haohanlunar", LunarMobIdentity.mobIdKey().getNamespace());
        assertEquals("mob_id", LunarMobIdentity.mobIdKey().getKey());
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

