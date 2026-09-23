package vn.haohan.lunar.core.lunar.boss.warden;

import vn.haohan.lunar.core.features.boss.warden.*;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardenSkillRegistryTest {

    @Test
    void registersAllTenLegacySkillNames() {
        WardenSkillRegistry registry = new WardenSkillRegistry((id, context, parameters) -> { });
        assertEquals(10, registry.snapshot().size());
        assertTrue(registry.snapshot().keySet().containsAll(WardenSkillRegistry.skillIds()));
    }

    @Test
    void dispatchesToJavaBackedInvokerWithoutChangingParameters() {
        AtomicReference<String> invoked = new AtomicReference<>();
        AtomicReference<Map<String, Object>> received = new AtomicReference<>();
        WardenSkillRegistry registry = new WardenSkillRegistry((id, context, parameters) -> {
            invoked.set(id);
            received.set(parameters);
        });
        SkillCastContext context = context();

        assertTrue(registry.cast("GROUND_SLAM", context, Map.of("damage", 12)));
        assertEquals("ground_slam", invoked.get());
        assertEquals(12, received.get().get("damage"));
    }

    @Test
    void cancelledOrUnknownSkillDoesNotDispatch() {
        AtomicReference<String> invoked = new AtomicReference<>();
        WardenSkillRegistry registry = new WardenSkillRegistry((id, context, parameters) -> invoked.set(id));
        SkillCastContext context = context();
        context.cancel();

        assertFalse(registry.cast("pursuit", context, Map.of()));
        assertFalse(registry.cast("unknown", context, Map.of()));
        assertEquals(null, invoked.get());
    }

    private static SkillCastContext context() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
        return new SkillCastContext(mob, new SkillDefinition("test", Set.of(SkillTrigger.ON_COMBAT), 0),
                SkillTrigger.ON_COMBAT, 0);
    }
}
