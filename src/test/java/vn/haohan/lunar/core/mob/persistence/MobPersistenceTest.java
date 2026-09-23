package vn.haohan.lunar.core.mob.persistence;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.persistence.MobPersistenceManager;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobPersistenceTest {

    @Test
    void saveAndRestoreStateViaPdc() {
        Map<NamespacedKey, Object> pdcStorage = new HashMap<>();

        PersistentDataContainer mockPdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("set")) {
                        pdcStorage.put((NamespacedKey) args[0], args[2]);
                        return null;
                    }
                    if (method.getName().equals("get")) {
                        return pdcStorage.get((NamespacedKey) args[0]);
                    }
                    if (method.getName().equals("has")) {
                        return pdcStorage.containsKey((NamespacedKey) args[0]);
                    }
                    return null;
                }
        );

        UUID uuid = UUID.randomUUID();
        double[] healthHolder = new double[]{45.0};
        LivingEntity mockEntity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getPersistentDataContainer")) return mockPdc;
                    if (method.getName().equals("getHealth")) return healthHolder[0];
                    if (method.getName().equals("setHealth")) {
                        healthHolder[0] = (double) args[0];
                        return null;
                    }
                    if (method.getName().equals("getAttribute")) return null;
                    return null;
                }
        );

        MobDefinition def = new MobDefinition(
                new MobDefinitionId("boss_golem"),
                EntityType.IRON_GOLEM,
                "Boss Golem",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );

        ActiveLunarMob mob = new ActiveLunarMob(mockEntity, def, new LunarMobIdentity("boss_golem", "1.0"));
        mob.setStance("enraged");

        // 1. Save state
        MobPersistenceManager.saveState(mob);
        assertTrue(MobPersistenceManager.isMarkedPersistent(mockEntity));

        // 2. Simulate entity reload with reset state
        healthHolder[0] = 100.0;
        ActiveLunarMob reloadedMob = new ActiveLunarMob(mockEntity, def, new LunarMobIdentity("boss_golem", "1.0"));
        assertEquals("default", reloadedMob.stance());

        // 3. Restore state
        MobPersistenceManager.restoreState(reloadedMob);
        assertEquals("enraged", reloadedMob.stance());
        assertEquals(45.0, healthHolder[0]);
    }
}
