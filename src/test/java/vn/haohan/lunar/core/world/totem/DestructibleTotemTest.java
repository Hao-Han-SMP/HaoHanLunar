package vn.haohan.lunar.core.world.totem;

import vn.haohan.lunar.api.system.world.totem.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructibleTotemTest {

    @Test
    void totemSpawnsAndPulsesPeriodically() {
        TotemManager manager = new TotemManager();
        MockEntity mockStand = new MockEntity();
        manager.setEntitySpawner((loc, def) -> mockStand.proxy());

        World mockWorld = mockWorld();
        Location loc = new Location(mockWorld, 10, 64, 10);
        UUID casterId = UUID.randomUUID();

        TotemDefinition def = new TotemDefinition(
                "LUNAR_CRYSTAL", 100.0, 400L, 40L, "HealPulse", "ShatterBurst", "crystal_model"
        );

        ActiveTotem totem = manager.spawnTotem(casterId, loc, def, 100L);
        assertNotNull(totem);
        assertTrue(totem.isActive());
        assertEquals(100.0, totem.currentHealth());

        List<String> dispatchedSkills = new ArrayList<>();
        // Tick at 139 (39 ticks later, interval is 40)
        manager.tick(139L, (skill, target) -> dispatchedSkills.add(skill));
        assertTrue(dispatchedSkills.isEmpty(), "Should not pulse before interval");

        // Tick at 140 (exactly 40 ticks later)
        manager.tick(140L, (skill, target) -> dispatchedSkills.add(skill));
        assertEquals(List.of("HealPulse"), dispatchedSkills, "Should pulse once at interval");

        // Tick at 180 (second interval)
        manager.tick(180L, (skill, target) -> dispatchedSkills.add(skill));
        assertEquals(List.of("HealPulse", "HealPulse"), dispatchedSkills, "Should pulse twice");
    }

    @Test
    void totemReceivesDamageAndDestroysOnZeroHealth() {
        TotemManager manager = new TotemManager();
        MockEntity mockStand = new MockEntity();
        manager.setEntitySpawner((loc, def) -> mockStand.proxy());

        World mockWorld = mockWorld();
        Location loc = new Location(mockWorld, 0, 64, 0);
        TotemDefinition def = new TotemDefinition(
                "CRYSTAL", 100.0, 400L, 40L, null, "ShatterBurst", null
        );

        ActiveTotem totem = manager.spawnTotem(UUID.randomUUID(), loc, def, 0L);
        MockLivingEntity killer = new MockLivingEntity();

        List<String> destroyedCalls = new ArrayList<>();
        // Deal 40 damage
        boolean killed = manager.damageTotem(totem.totemId(), 40.0, killer.proxy(), (s, t) -> destroyedCalls.add(s));
        assertFalse(killed);
        assertEquals(60.0, totem.currentHealth());
        assertTrue(totem.isActive());
        assertFalse(mockStand.removed);

        // Deal 60 damage (total 100) -> destruction
        killed = manager.damageTotem(totem.totemId(), 60.0, killer.proxy(), (s, t) -> destroyedCalls.add(s));
        assertTrue(killed);
        assertTrue(totem.isDestroyed());
        assertFalse(totem.isActive());
        assertTrue(mockStand.removed, "Underlying entity should be removed");
        assertEquals(List.of("ShatterBurst"), destroyedCalls);
        assertEquals(0, manager.activeTotems().size(), "Should be unmounted from manager");
    }

    @Test
    void totemExpiresAndCleansUpWhenDurationExceeded() {
        TotemManager manager = new TotemManager();
        MockEntity mockStand = new MockEntity();
        manager.setEntitySpawner((loc, def) -> mockStand.proxy());

        World mockWorld = mockWorld();
        Location loc = new Location(mockWorld, 0, 64, 0);
        TotemDefinition def = new TotemDefinition(
                "CRYSTAL", 100.0, 200L, 40L, null, null, null
        );

        ActiveTotem totem = manager.spawnTotem(UUID.randomUUID(), loc, def, 50L);
        assertTrue(totem.isActive());

        // Tick before expiration (e.g. tick 200, spawn was 50, duration is 200 -> expires at 250)
        manager.tick(200L, (s, t) -> {});
        assertTrue(totem.isActive());

        // Tick at 250 -> expires
        manager.tick(250L, (s, t) -> {});
        assertTrue(totem.isExpired());
        assertFalse(totem.isActive());
        assertTrue(mockStand.removed);
        assertEquals(0, manager.activeTotems().size());
    }

    @Test
    void totemMechanicExecutionViaRegistry() {
        MechanicRegistry registry = new MechanicRegistry();
        TotemManager manager = new TotemManager();
        MockEntity mockStand = new MockEntity();
        manager.setEntitySpawner((loc, def) -> mockStand.proxy());
        registry.setTotemManager(manager);

        World mockWorld = mockWorld();
        Location loc = new Location(mockWorld, 5, 64, 5);
        ActiveLunarMob mob = createMockMob();
        SkillDefinition skillDef = new SkillDefinition("summon_totem", Set.of(SkillTrigger.ON_COMBAT), 10L);
        SkillCastContext castContext = new SkillCastContext(mob, skillDef, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext context = new MechanicContext(castContext, List.of(TargetRef.of(loc)));

        var result = registry.execute("totem", context, Map.of(
                "type", "GUARDIAN_PYLON",
                "health", 250.0,
                "duration", 300,
                "interval", 50,
                "onPulseSkill", "PylonZap"
        ));

        assertTrue(result.isSuccess());
        assertEquals(1, manager.activeTotems().size());
        ActiveTotem spawned = manager.activeTotems().iterator().next();
        assertEquals("GUARDIAN_PYLON", spawned.definition().type());
        assertEquals(250.0, spawned.maxHealth());
        assertEquals(300L, spawned.definition().durationTicks());
        assertEquals(50L, spawned.definition().intervalTicks());
        assertEquals("PylonZap", spawned.definition().onPulseSkill());
    }

    private static World mockWorld() {
        UUID worldId = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> worldId;
                    case "getName" -> "mock_world";
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> worldId.hashCode();
                    default -> null;
                }
        );
    }

    private static ActiveLunarMob createMockMob() {
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> UUID.randomUUID();
                    case "isValid" -> true;
                    case "isDead" -> false;
                    default -> null;
                }
        );
        MobDefinition def = new MobDefinition(new MobDefinitionId("boss"), EntityType.IRON_GOLEM, "Boss", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity("boss", "1"));
    }

    private static class MockEntity {
        boolean removed = false;
        String name = "";
        final UUID uuid = UUID.randomUUID();

        Entity proxy() {
            return (Entity) Proxy.newProxyInstance(
                    Entity.class.getClassLoader(),
                    new Class<?>[]{Entity.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "remove" -> { removed = true; yield null; }
                        case "isValid" -> !removed;
                        case "isDead" -> removed;
                        case "setCustomName" -> { name = (String) args[0]; yield null; }
                        case "setCustomNameVisible" -> null;
                        default -> null;
                    }
            );
        }
    }

    private static class MockLivingEntity {
        final UUID uuid = UUID.randomUUID();

        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "isValid" -> true;
                        case "isDead" -> false;
                        default -> null;
                    }
            );
        }
    }
}
