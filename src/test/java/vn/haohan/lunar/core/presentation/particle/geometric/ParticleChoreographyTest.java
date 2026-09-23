package vn.haohan.lunar.api.presentation.particle.geometric;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.core.system.util.FastMath;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ParticleChoreographyTest {

    private World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private ActiveLunarMob createDummyMob(World world) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, 0, 64, 0);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getType")) return EntityType.ZOMBIE;
                    if (method.getName().equals("getHealth")) return 100.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });

        MobDefinition def = new MobDefinition(new MobDefinitionId("dummy"), EntityType.ZOMBIE,
                "Dummy", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity("dummy", "1"));
    }

    @Test
    @DisplayName("FastMath trigonometric LUT accurately approximates Math.sin and Math.cos")
    void testFastMathAccuracy() {
        for (double angle = -Math.PI * 4; angle <= Math.PI * 4; angle += 0.05) {
            double expectedSin = Math.sin(angle);
            double actualSin = FastMath.sin(angle);
            assertEquals(expectedSin, actualSin, 0.005, "Sin deviated too much at angle: " + angle);

            double expectedCos = Math.cos(angle);
            double actualCos = FastMath.cos(angle);
            assertEquals(expectedCos, actualCos, 0.005, "Cos deviated too much at angle: " + angle);
        }
    }

    @Test
    @DisplayName("CurveMath generates correct geometry for helix, ring, polygon, line, and arc")
    void testCurveMathShapes() {
        // 1. Helix: verify bounds and progression
        List<Vector> helixPoints = CurveMath.helix(5.0, 10.0, 50, 2.0);
        assertEquals(50, helixPoints.size());
        assertEquals(0.0, helixPoints.get(0).getY(), 0.001);
        assertEquals(10.0, helixPoints.get(helixPoints.size() - 1).getY(), 0.001);
        for (Vector p : helixPoints) {
            double horizontalDist = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            assertEquals(5.0, horizontalDist, 0.02, "Point distance from center must match radius");
        }

        // 2. Ring: planar Y=0 and fixed radius
        List<Vector> ringPoints = CurveMath.ring(4.0, 36);
        assertEquals(36, ringPoints.size());
        for (Vector p : ringPoints) {
            assertEquals(0.0, p.getY(), 0.001);
            double dist = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            assertEquals(4.0, dist, 0.02);
        }

        // 3. Polygon: 4 sides (square), 5 points per side -> 20 points
        List<Vector> polyPoints = CurveMath.polygon(4, 5.0, 5);
        assertEquals(20, polyPoints.size());

        // 4. Line: direction along Z, length 10
        List<Vector> linePoints = CurveMath.line(new Vector(0, 0, 1), 10.0, 11);
        assertEquals(11, linePoints.size());
        assertEquals(0.0, linePoints.get(0).getZ(), 0.001);
        assertEquals(10.0, linePoints.get(10).getZ(), 0.001);

        // 5. Arc: apex height at midpoint
        List<Vector> arcPoints = CurveMath.arc(new Vector(0, 0, 20), 8.0, 21);
        assertEquals(21, arcPoints.size());
        assertEquals(0.0, arcPoints.get(0).getY(), 0.001);
        assertEquals(0.0, arcPoints.get(20).getY(), 0.001);
        // Midpoint at index 10 (t = 0.5)
        assertEquals(8.0, arcPoints.get(10).getY(), 0.001);
    }

    @Test
    @DisplayName("ParticleChoreographer enforces hard maxParticlesPerTick budget")
    void testParticleBudgetCap() {
        ParticleChoreographer choreographer = new ParticleChoreographer(100);
        AtomicInteger actualSpawned = new AtomicInteger(0);
        choreographer.setParticleSpawner((loc, p, count, ox, oy, oz, extra) -> actualSpawned.addAndGet(count));

        World world = createMockWorld();
        Location origin = new Location(world, 0, 64, 0);

        // Request 150 points in tick 1 -> must be capped to 100
        int granted = choreographer.spawnRing(origin, Particle.FLAME, 5.0, 150, 1L);
        assertEquals(100, granted);
        assertEquals(100, actualSpawned.get());

        // Subsequent request in same tick 1 -> 0 granted because budget exhausted
        int granted2 = choreographer.spawnHelix(origin, Particle.END_ROD, 2.0, 5.0, 50, 1.0, 1L);
        assertEquals(0, granted2);
        assertEquals(100, actualSpawned.get());

        // New tick 2 -> budget replenished
        int granted3 = choreographer.spawnLine(origin, new Vector(1, 0, 0), Particle.CRIT, 10.0, 60, 2L);
        assertEquals(60, granted3);
        assertEquals(160, actualSpawned.get());
    }

    @Test
    @DisplayName("MechanicRegistry executes geometric particle mechanics properly")
    void testGeometricMechanicsInRegistry() {
        World world = createMockWorld();
        ActiveLunarMob mob = createDummyMob(world);
        MechanicRegistry registry = new MechanicRegistry();

        AtomicInteger particleCount = new AtomicInteger(0);
        registry.particleChoreographer().setParticleSpawner((loc, p, count, ox, oy, oz, extra) -> particleCount.addAndGet(count));

        SkillDefinition skill = new SkillDefinition("fx_test", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of());

        // Helix
        MechanicResult helixResult = registry.execute("effect:helix", mechContext, Map.of(
                "particle", "FLAME",
                "radius", 3.0,
                "height", 6.0,
                "points", 30
        ));
        assertTrue(helixResult.isSuccess(), "Helix failed: " + helixResult.error());
        assertEquals(30, particleCount.get());

        // Ring
        MechanicResult ringResult = registry.execute("effect:ring", mechContext, Map.of(
                "particle", "END_ROD",
                "radius", 4.0,
                "points", 20
        ));
        assertTrue(ringResult.isSuccess(), "Ring failed: " + ringResult.error());
        assertEquals(50, particleCount.get());

        // Polygon
        MechanicResult polyResult = registry.execute("effect:polygon", mechContext, Map.of(
                "particle", "CRIT",
                "sides", 6,
                "radius", 3.0,
                "points", 4
        ));
        assertTrue(polyResult.isSuccess(), "Polygon failed: " + polyResult.error());
        assertEquals(74, particleCount.get());

        // Line
        MechanicResult lineResult = registry.execute("effect:line", mechContext, Map.of(
                "particle", "ELECTRIC_SPARK",
                "length", 8.0,
                "points", 15
        ));
        assertTrue(lineResult.isSuccess(), "Line failed: " + lineResult.error());
        assertEquals(89, particleCount.get());

        // Arc
        MechanicResult arcResult = registry.execute("effect:arc", mechContext, Map.of(
                "particle", "SOUL_FIRE_FLAME",
                "height", 4.0,
                "points", 10
        ));
        assertTrue(arcResult.isSuccess(), "Arc failed: " + arcResult.error());
        assertEquals(99, particleCount.get());
    }
}
