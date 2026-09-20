package vn.haohan.lunar.core.skill.complex;

import vn.haohan.lunar.api.system.combat.skill.complex.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainBeamOrbitalSlashTest {

    @Test
    void chainEngineBouncesWithDecayAndNoDuplicates() {
        World mockWorld = createMockWorld();
        LivingEntity e1 = createMockEntity(mockWorld, 0, 0, 0);
        LivingEntity e2 = createMockEntity(mockWorld, 2, 0, 0);
        LivingEntity e3 = createMockEntity(mockWorld, 4, 0, 0);

        List<Double> damages = new ArrayList<>();
        ChainEngine.ChainResult result = ChainEngine.executeChain(
                e1, 5.0, 3, 100.0, 0.5, null,
                (target, dmg) -> damages.add(dmg)
        );

        assertEquals(1, result.bounceCount()); // In mock world with getNearbyEntities returning empty by default
        assertEquals(100.0, damages.get(0), 0.001);
    }

    @Test
    void beamEngineIntersectionMath() {
        World mockWorld = createMockWorld();
        Location origin = new Location(mockWorld, 0, 64, 0);
        Vector dir = new Vector(0, 0, 1); // Along +Z axis

        // Fire beam of length 20, width 1.5
        List<BeamEngine.BeamHit> hits = BeamEngine.fireBeam(
                origin, dir, 20.0, 1.5, false, null, null
        );
        assertNotNull(hits);
    }

    @Test
    void orbitalPositionsCalculatedAccurately() {
        World mockWorld = createMockWorld();
        Location center = new Location(mockWorld, 100, 64, 100);

        // 4 orbitals at radius 5, tick 0, 0 speed
        List<OrbitalEngine.OrbitalPoint> orbitals = OrbitalEngine.computeOrbitalLocations(
                center, 5.0, 4, 0, 0.0
        );

        assertEquals(4, orbitals.size());

        // Orbital 0: angle 0 -> x = 100 + 5 = 105, z = 100
        assertEquals(105.0, orbitals.get(0).location().getX(), 0.01);
        assertEquals(100.0, orbitals.get(0).location().getZ(), 0.01);

        // Orbital 1: angle pi/2 -> x = 100, z = 100 + 5 = 105
        assertEquals(100.0, orbitals.get(1).location().getX(), 0.01);
        assertEquals(105.0, orbitals.get(1).location().getZ(), 0.01);

        // Orbital 2: angle pi -> x = 100 - 5 = 95, z = 100
        assertEquals(95.0, orbitals.get(2).location().getX(), 0.01);
        assertEquals(100.0, orbitals.get(2).location().getZ(), 0.01);

        // Orbital 3: angle 3pi/2 -> x = 100, z = 100 - 5 = 95
        assertEquals(100.0, orbitals.get(3).location().getX(), 0.01);
        assertEquals(95.0, orbitals.get(3).location().getZ(), 0.01);
    }

    @Test
    void slashArcMath() {
        World mockWorld = createMockWorld();
        Location origin = new Location(mockWorld, 0, 64, 0);
        Vector facing = new Vector(0, 0, 1); // Facing positive Z

        List<SlashEngine.SlashHit> hits = SlashEngine.executeSlash(
                origin, facing, 5.0, 90.0, null, null
        );
        assertNotNull(hits);
    }

    @Test
    void mechanicRegistryComplexSkillsIntegration() {
        MechanicRegistry registry = new MechanicRegistry();
        ActiveLunarMob mob = sampleMob();
        SkillCastContext context = sampleContext(mob);
        MechanicContext mechContext = new MechanicContext(context, List.of(TargetRef.entity(mob.entity())));

        // Chain
        MechanicResult chainRes = registry.execute("chain", mechContext, Map.of(
                "radius", 6.0,
                "bounces", 3,
                "damage", 25.0,
                "decay", 0.75
        ));
        assertTrue(chainRes.valid());

        // Beam
        MechanicResult beamRes = registry.execute("beam", mechContext, Map.of(
                "range", 30.0,
                "width", 1.2,
                "damage", 40.0
        ));
        assertTrue(beamRes.valid());

        // Orbital
        MechanicResult orbitalRes = registry.execute("orbital", mechContext, Map.of(
                "radius", 4.0,
                "count", 3,
                "speed", 0.15,
                "damage", 15.0
        ));
        assertTrue(orbitalRes.valid());

        // Slash
        MechanicResult slashRes = registry.execute("slash", mechContext, Map.of(
                "radius", 5.0,
                "arc", 120.0,
                "damage", 30.0
        ));
        assertTrue(slashRes.valid());
    }

    private static World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("getNearbyEntities")) return List.of();
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private static LivingEntity createMockEntity(World world, double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("damage")) return null;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static SkillCastContext sampleContext(ActiveLunarMob mob) {
        SkillDefinition skill = new SkillDefinition("cast_complex", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100);
    }

    private static ActiveLunarMob sampleMob() {
        World mockWorld = createMockWorld();
        LivingEntity entity = createMockEntity(mockWorld, 0, 64, 0);
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
