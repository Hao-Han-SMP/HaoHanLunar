package vn.haohan.lunar.core.skill.complex;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.skill.complex.BeamEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.ChainEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.OrbitalEngine;
import vn.haohan.lunar.api.system.combat.skill.complex.SlashEngine;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexCombatEnginesTest {

    @Test
    void chainEngineBouncesWithoutDuplicatesAndDecaysDamage() {
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0);
        LivingEntity target1 = mockLiving(world, 5, 64, 0);
        LivingEntity target2 = mockLiving(world, 8, 64, 0);
        LivingEntity target3 = mockLiving(world, 11, 64, 0);
        LivingEntity farTarget = mockLiving(world, 50, 64, 0);

        worldEntities.addAll(List.of(caster, target1, target2, target3, farTarget));

        Map<LivingEntity, Double> damageDealt = new HashMap<>();
        ChainEngine.ChainResult result = ChainEngine.executeChain(
                target1,
                5.0,
                3,
                100.0,
                0.5,
                caster,
                damageDealt::put
        );

        assertEquals(3, result.bounceCount());
        assertEquals(List.of(target1, target2, target3), result.hitOrder());
        assertEquals(100.0, damageDealt.get(target1), 0.001);
        assertEquals(50.0, damageDealt.get(target2), 0.001);
        assertEquals(25.0, damageDealt.get(target3), 0.001);
        assertFalse(damageDealt.containsKey(caster), "Caster must not take chain damage");
        assertFalse(damageDealt.containsKey(farTarget), "Target out of bounce radius must not be hit");
    }

    @Test
    void beamEngineHitsEntitiesAlongRayAndExcludesOffAxis() {
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0);
        // On beam axis at dist 10
        LivingEntity onBeam = mockLiving(world, 10, 64, 0);
        // Off beam axis at (10, 64, 5) -> width is 1.5, distance is 5
        LivingEntity offBeam = mockLiving(world, 10, 64, 5);
        // Behind beam origin at (-5, 64, 0)
        LivingEntity behindBeam = mockLiving(world, -5, 64, 0);

        worldEntities.addAll(List.of(caster, onBeam, offBeam, behindBeam));

        Location origin = new Location(world, 0, 64, 0);
        Vector direction = new Vector(1, 0, 0);

        List<LivingEntity> hitList = new ArrayList<>();
        List<BeamEngine.BeamHit> hits = BeamEngine.fireBeam(
                origin,
                direction,
                30.0,
                1.5,
                false,
                caster.getUniqueId(),
                (entity, dist) -> hitList.add(entity)
        );

        assertEquals(1, hits.size());
        assertEquals(onBeam, hits.getFirst().entity());
        assertEquals(10.0, hits.getFirst().distanceAlongBeam(), 0.01);
        assertTrue(hitList.contains(onBeam));
        assertFalse(hitList.contains(offBeam));
        assertFalse(hitList.contains(behindBeam));
        assertFalse(hitList.contains(caster));
    }

    @Test
    void orbitalEngineComputesPointsAndDetectsCollisions() {
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0);
        // Target located at (3, 64, 0)
        LivingEntity targetNearOrbital = mockLiving(world, 3, 64, 0);
        // Target far away
        LivingEntity targetFar = mockLiving(world, 20, 64, 0);

        worldEntities.addAll(List.of(caster, targetNearOrbital, targetFar));

        Location center = new Location(world, 0, 64, 0);
        // 4 orbitals at radius 3, angle 0 aligns with (3, 64, 0)
        var points = OrbitalEngine.computeOrbitalLocations(center, 3.0, 4, 0L, 0.0);
        assertEquals(4, points.size());

        List<LivingEntity> collisions = OrbitalEngine.checkOrbitalCollisions(
                points,
                1.0,
                caster.getUniqueId(),
                (hit, pt) -> {}
        );

        assertTrue(collisions.contains(targetNearOrbital));
        assertFalse(collisions.contains(targetFar));
        assertFalse(collisions.contains(caster));
    }

    @Test
    void slashEngineHitsInFrontArcAndIgnoresRear() {
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0);
        // In front along +X
        LivingEntity frontTarget = mockLiving(world, 3, 64, 0);
        // Behind along -X
        LivingEntity rearTarget = mockLiving(world, -3, 64, 0);
        // Too far (dist 10 > radius 4)
        LivingEntity farTarget = mockLiving(world, 10, 64, 0);

        worldEntities.addAll(List.of(caster, frontTarget, rearTarget, farTarget));

        Location origin = new Location(world, 0, 64, 0);
        Vector facing = new Vector(1, 0, 0);

        List<SlashEngine.SlashHit> hits = SlashEngine.executeSlash(
                origin,
                facing,
                4.0,
                90.0,
                caster.getUniqueId(),
                (entity, hit) -> {}
        );

        assertEquals(1, hits.size());
        assertEquals(frontTarget, hits.getFirst().target());
        assertFalse(hits.stream().anyMatch(h -> h.target().equals(rearTarget)));
        assertFalse(hits.stream().anyMatch(h -> h.target().equals(farTarget)));
        assertFalse(hits.stream().anyMatch(h -> h.target().equals(caster)));
    }

    // --- Mock Helpers ---

    private static World mockWorldWithLiving(String name, List<LivingEntity> entities) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getNearbyEntities" -> {
                        Location center = (Location) args[0];
                        double rx = ((Number) args[1]).doubleValue();
                        double rz = ((Number) args[3]).doubleValue();
                        double maxR = Math.max(rx, rz);
                        yield entities.stream()
                                .filter(e -> center.distance(e.getLocation()) <= maxR)
                                .toList();
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> null;
                });
    }

    private static LivingEntity mockLiving(World world, double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
    }
}
