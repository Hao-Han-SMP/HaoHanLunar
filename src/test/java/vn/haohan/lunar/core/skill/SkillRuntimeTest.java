package vn.haohan.lunar.core.skill;

import vn.haohan.lunar.api.system.combat.skill.*;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeTest {

    @Test
    void skillDefinitionNormalizesDefaultsAndValidatesTrigger() {
        SkillDefinition skill = new SkillDefinition("Ground_Slam", Set.of(SkillTrigger.ON_COMBAT), 20,
                List.of("damage"));
        assertEquals("ground_slam", skill.id());
        assertEquals(20, skill.cooldownTicks());
        assertThrows(IllegalArgumentException.class,
                () -> new SkillDefinition("empty", Set.of(), 0));
    }

    @Test
    void cooldownBlocksUntilTickExpiresAndCanBeCancelled() {
        CooldownRegistry cooldowns = new CooldownRegistry();
        UUID entity = UUID.randomUUID();
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 5);

        assertTrue(cooldowns.tryAcquire(entity, skill, 10));
        assertFalse(cooldowns.tryAcquire(entity, skill, 14));
        assertTrue(cooldowns.isReady(entity, skill.id(), 15));
        assertTrue(cooldowns.tryAcquire(entity, skill, 15));
        cooldowns.cancel(entity, skill.id());
        assertTrue(cooldowns.tryAcquire(entity, skill, 15));
    }

    @Test
    void clearResetsAllSkillsWhenEntityDies() {
        CooldownRegistry cooldowns = new CooldownRegistry();
        UUID entity = UUID.randomUUID();
        SkillDefinition first = new SkillDefinition("first", Set.of(SkillTrigger.ON_DEATH), 100);
        SkillDefinition second = new SkillDefinition("second", Set.of(SkillTrigger.ON_DEATH), 100);
        cooldowns.tryAcquire(entity, first, 1);
        cooldowns.tryAcquire(entity, second, 1);

        cooldowns.clear(entity);

        assertEquals(0, cooldowns.size());
        assertTrue(cooldowns.tryAcquire(entity, first, 2));
        assertTrue(cooldowns.tryAcquire(entity, second, 2));
    }

    @Test
    void cancelledCastIsObservableAndDoesNotPreventCooldownCancellation() {
        ActiveLunarMob mob = activeMob();
        SkillDefinition skill = new SkillDefinition("signal", Set.of(SkillTrigger.ON_SIGNAL), 20);
        SkillCastContext context = new SkillCastContext(mob, skill, SkillTrigger.ON_SIGNAL, 30);
        CooldownRegistry cooldowns = new CooldownRegistry();

        assertTrue(cooldowns.tryAcquire(context.casterId(), skill, context.startedAtTick()));
        context.cancel();
        if (context.isCancelled()) {
            cooldowns.cancel(context.casterId(), skill.id());
        }

        assertTrue(context.isCancelled());
        assertTrue(cooldowns.tryAcquire(context.casterId(), skill, 30));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillCastContext(mob, skill, SkillTrigger.ON_DEATH, 30));
    }

    private static ActiveLunarMob activeMob() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
