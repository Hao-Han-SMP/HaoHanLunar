package vn.haohan.lunar.core.skill.projectile;

import vn.haohan.lunar.api.system.combat.skill.projectile.*;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileTest {

    @Test
    void straightFlightAndGravity() {
        World mockWorld = createMockWorld();
        Location start = new Location(mockWorld, 0, 100, 0);
        Vector dir = new Vector(1, 0, 0);

        AtomicInteger ticks = new AtomicInteger();
        ProjectileCallback callback = new ProjectileCallback() {
            @Override
            public void onTick(ActiveProjectile projectile) {
                ticks.incrementAndGet();
            }
        };

        ProjectileDefinition def = ProjectileDefinition.builder("arrow_bolt")
                .velocity(2.0)
                .gravity(0.1)
                .maxTicks(10)
                .callback(callback)
                .build();

        ActiveProjectile p = new ActiveProjectile(def, start, dir, UUID.randomUUID(), null);

        // Tick 1
        p.tick();
        assertEquals(1, ticks.get());
        assertEquals(2.0, p.currentLocation().getX(), 0.001);
        // After tick 1, gravity reduces Y velocity: initial vy was 0, now -0.1
        assertEquals(-0.1, p.currentVelocity().getY(), 0.001);

        // Advance to maxTicks (10)
        for (int i = 0; i < 15; i++) {
            p.tick();
        }
        assertTrue(p.isDead());
    }

    @Test
    void homingSteersTowardTarget() {
        World mockWorld = createMockWorld();
        Location start = new Location(mockWorld, 0, 50, 0);
        Vector initialDir = new Vector(1, 0, 0); // Moving East

        // Target is directly North (0, 50, 20)
        Location targetLoc = new Location(mockWorld, 0, 50, 20);
        TargetRef target = TargetRef.location(targetLoc);

        ProjectileDefinition def = ProjectileDefinition.builder("seeking_missile")
                .velocity(1.0)
                .homing(true)
                .turnRateDegrees(45.0) // Can turn up to 45 deg per tick
                .maxTicks(20)
                .build();

        ActiveProjectile p = new ActiveProjectile(def, start, initialDir, UUID.randomUUID(), target);

        p.tick();
        // Velocity should have turned towards positive Z
        assertTrue(p.currentVelocity().getZ() > 0.0);
    }

    @Test
    void projectileTrackerCentralizedTicking() {
        ProjectileTracker tracker = new ProjectileTracker();
        World mockWorld = createMockWorld();
        Location start = new Location(mockWorld, 0, 50, 0);

        AtomicBoolean ended = new AtomicBoolean(false);
        ProjectileDefinition def = ProjectileDefinition.builder("short_lived")
                .velocity(1.0)
                .maxTicks(3)
                .callback(new ProjectileCallback() {
                    @Override
                    public void onEnd(ActiveProjectile projectile) {
                        ended.set(true);
                    }
                })
                .build();

        ActiveProjectile p = new ActiveProjectile(def, start, new Vector(0, 1, 0), UUID.randomUUID(), null);
        tracker.spawn(p);
        assertEquals(1, tracker.size());

        tracker.tick(1);
        tracker.tick(2);
        tracker.tick(3);
        tracker.tick(4);

        assertTrue(p.isDead());
        assertTrue(ended.get());
        assertEquals(0, tracker.size());
    }

    @Test
    void mechanicRegistryProjectileIntegration() {
        MechanicRegistry registry = new MechanicRegistry();
        ProjectileTracker tracker = new ProjectileTracker();
        registry.setProjectileTracker(tracker);

        List<String> hitSubskills = new ArrayList<>();
        registry.setSubskillInvoker((skillId, ctx) -> {
            hitSubskills.add(skillId);
            return true;
        });

        ActiveLunarMob mob = sampleMob();
        SkillCastContext context = sampleContext(mob);
        MechanicContext mechContext = new MechanicContext(context, List.of(TargetRef.location(new Location(mob.entity().getWorld(), 10, 50, 10))));

        MechanicResult result = registry.execute("projectile", mechContext, Map.of(
                "velocity", 2.0,
                "max-ticks", 50,
                "surface-mode", "BOUNCE"
        ));

        assertTrue(result.valid());
        assertEquals(1, tracker.size());
    }

    private static World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private static SkillCastContext sampleContext(ActiveLunarMob mob) {
        SkillDefinition skill = new SkillDefinition("shoot_fireball", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100);
    }

    private static ActiveLunarMob sampleMob() {
        UUID uuid = UUID.randomUUID();
        World mockWorld = createMockWorld();
        Location loc = new Location(mockWorld, 0, 50, 0);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return mockWorld;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }
}
