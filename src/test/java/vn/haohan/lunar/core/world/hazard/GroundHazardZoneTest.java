package vn.haohan.lunar.core.world.hazard;

import vn.haohan.lunar.api.system.world.hazard.*;

import org.bukkit.Location;
import org.bukkit.World;
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

class GroundHazardZoneTest {

    @Test
    void zoneDetectsEntityEnterTickAndExit() {
        HazardZoneTracker tracker = new HazardZoneTracker();
        World mockWorld = mockWorld();
        Location center = new Location(mockWorld, 0, 64, 0);

        HazardZoneDefinition def = new HazardZoneDefinition(
                5.0, 200L, 20L, "DUST_PLUME", "FrostSlow", "FrostDamage", "ClearSlow"
        );

        HazardZone zone = tracker.createZone(UUID.randomUUID(), center, def, 0L);
        assertNotNull(zone);
        assertEquals(1, tracker.activeZones().size());

        MockLivingEntity player = new MockLivingEntity(new Location(mockWorld, 15, 64, 15)); // outside
        List<LivingEntity> currentCandidates = new ArrayList<>(List.of(player.proxy()));
        tracker.setEntityProvider(z -> currentCandidates);

        List<String> dispatchedSkills = new ArrayList<>();

        // Tick 1: player is outside
        tracker.tick(1L, (skill, target) -> dispatchedSkills.add(skill));
        assertTrue(dispatchedSkills.isEmpty());
        assertFalse(zone.isInside(player.uuid));

        // Move player inside (x=2, y=64, z=2 -> distSq = 8 <= 25)
        player.location = new Location(mockWorld, 2, 64, 2);

        // Tick 2: player enters!
        tracker.tick(2L, (skill, target) -> dispatchedSkills.add(skill));
        assertEquals(List.of("FrostSlow"), dispatchedSkills);
        assertTrue(zone.isInside(player.uuid));

        // Tick 20: (20 - 0) % 20 == 0 -> onTickSkill triggered!
        dispatchedSkills.clear();
        tracker.tick(20L, (skill, target) -> dispatchedSkills.add(skill));
        assertEquals(List.of("FrostDamage"), dispatchedSkills);
        assertTrue(zone.isInside(player.uuid));

        // Player moves outside (x=30, y=64, z=30)
        player.location = new Location(mockWorld, 30, 64, 30);

        // Tick 21: player leaves!
        dispatchedSkills.clear();
        tracker.tick(21L, (skill, target) -> dispatchedSkills.add(skill));
        assertEquals(List.of("ClearSlow"), dispatchedSkills);
        assertFalse(zone.isInside(player.uuid));
    }

    @Test
    void zoneExpiresAndTriggersExitOnRemainingEntities() {
        HazardZoneTracker tracker = new HazardZoneTracker();
        World mockWorld = mockWorld();
        Location center = new Location(mockWorld, 0, 64, 0);

        HazardZoneDefinition def = new HazardZoneDefinition(
                10.0, 100L, 20L, "SMOKE", "Ignite", "BurnDamage", "Extinguish"
        );

        HazardZone zone = tracker.createZone(UUID.randomUUID(), center, def, 10L); // expires at 110L
        MockLivingEntity player = new MockLivingEntity(new Location(mockWorld, 0, 64, 0)); // inside center
        tracker.setEntityProvider(z -> List.of(player.proxy()));

        List<String> dispatchedSkills = new ArrayList<>();

        // Tick 15: player enters
        tracker.tick(15L, (s, t) -> dispatchedSkills.add(s));
        assertEquals(List.of("Ignite"), dispatchedSkills);
        assertTrue(zone.isInside(player.uuid));

        // Advance to expiration at tick 110
        dispatchedSkills.clear();
        tracker.tick(110L, (s, t) -> dispatchedSkills.add(s));

        // Remaining entity should receive onExitSkill
        assertEquals(0, tracker.activeZones().size(), "Expired zone should be pruned");
        assertTrue(zone.isExpired(110L));
    }

    @Test
    void hazardzoneMechanicExecutionViaRegistry() {
        MechanicRegistry registry = new MechanicRegistry();
        HazardZoneTracker tracker = new HazardZoneTracker();
        registry.setHazardZoneTracker(tracker);

        World mockWorld = mockWorld();
        Location loc = new Location(mockWorld, 10, 64, 10);
        ActiveLunarMob mob = createMockMob();
        SkillDefinition skillDef = new SkillDefinition("create_trap", Set.of(SkillTrigger.ON_COMBAT), 10L);
        SkillCastContext castContext = new SkillCastContext(mob, skillDef, SkillTrigger.ON_COMBAT, 50L);
        MechanicContext context = new MechanicContext(castContext, List.of(TargetRef.of(loc)));

        var result = registry.execute("hazardzone", context, Map.of(
                "radius", 8.5,
                "duration", 300,
                "interval", 25,
                "particle", "FLAME",
                "onEnterSkill", "FireSlow",
                "onTickSkill", "FireBurn",
                "onExitSkill", "ClearFire"
        ));

        assertTrue(result.isSuccess());
        assertEquals(1, tracker.activeZones().size());
        HazardZone created = tracker.activeZones().iterator().next();
        assertEquals(8.5, created.definition().radius());
        assertEquals(300L, created.definition().durationTicks());
        assertEquals(25L, created.definition().tickInterval());
        assertEquals("FLAME", created.definition().particle());
        assertEquals("FireSlow", created.definition().onEnterSkill());
        assertEquals("FireBurn", created.definition().onTickSkill());
        assertEquals("ClearFire", created.definition().onExitSkill());
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

    private static class MockLivingEntity {
        Location location;
        final UUID uuid = UUID.randomUUID();

        MockLivingEntity(Location location) {
            this.location = location;
        }

        LivingEntity proxy() {
            return (LivingEntity) Proxy.newProxyInstance(
                    LivingEntity.class.getClassLoader(),
                    new Class<?>[]{LivingEntity.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> uuid;
                        case "getLocation" -> location.clone();
                        case "isValid" -> true;
                        case "isDead" -> false;
                        default -> null;
                    }
            );
        }
    }
}
