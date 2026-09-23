package vn.haohan.lunar.core.skill.mechanic;

import vn.haohan.lunar.api.system.combat.skill.mechanic.*;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicRegistryTest {

    @Test
    void registryContainsSafeMvpMechanics() {
        MechanicRegistry registry = new MechanicRegistry();
        assertTrue(registry.snapshot().keySet().containsAll(Set.of("damage", "heal", "potion", "knockback",
                "teleport", "leap", "particle", "sound", "message", "bossbar", "animation", "summon", "remove")));
    }

    @Test
    void invalidAmountsReturnValidationResultAndDoNotThrow() {
        MechanicRegistry registry = new MechanicRegistry();
        MechanicContext context = context();

        MechanicResult result = registry.execute("damage", context, Map.of("amount", 5000));
        MechanicResult unknown = registry.execute("missing", context, Map.of());

        assertFalse(result.valid());
        assertFalse(unknown.valid());
    }

    @Test
    void commandExecutionIsNotRegistered() {
        assertFalse(new MechanicRegistry().snapshot().containsKey("command"));
    }

    private static MechanicContext context() {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        ActiveLunarMob mob = new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
        SkillDefinition skill = new SkillDefinition("test", Set.of(SkillTrigger.ON_COMBAT), 0);
        return new MechanicContext(new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 0), List.of());
    }
}
