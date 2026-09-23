package vn.haohan.lunar.api.presentation.display.orchestration;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.joml.Vector3f;
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

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DisplayOrchestrationTest {

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
        Location loc = new Location(world, 10, 64, 10);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getType")) return EntityType.BLAZE;
                    if (method.getName().equals("getHealth")) return 100.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });

        MobDefinition def = new MobDefinition(new MobDefinitionId("meteor_blaze"), EntityType.BLAZE,
                "Meteor Blaze", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity("meteor_blaze", "1"));
    }

    @Test
    @DisplayName("DisplayEntityManager tracks and auto-expires display entity sessions")
    void testDisplaySessionLifecycle() {
        World world = createMockWorld();
        DisplayEntityManager manager = new DisplayEntityManager();
        AtomicInteger removedCount = new AtomicInteger(0);

        manager.setEntityHandler(new DisplayEntityManager.DisplayEntityHandler() {
            @Override
            public Display spawn(Location location, DisplaySpawnOptions options) {
                return null;
            }

            @Override
            public void transform(Display display, DisplayTransformOptions options) {}

            @Override
            public void remove(Display display) {
                removedCount.incrementAndGet();
            }
        });

        Location loc = new Location(world, 0, 64, 0);
        DisplaySpawnOptions options = DisplaySpawnOptions.builder(DisplayType.BLOCK)
                .block(Material.CRYING_OBSIDIAN)
                .scale(2.0f, 2.0f, 2.0f)
                .translation(0, 5, 0)
                .duration(50)
                .build();

        UUID casterId = UUID.randomUUID();
        UUID sessionId = manager.spawnDisplay(loc, options, 100L, casterId);

        assertNotNull(sessionId);
        assertEquals(1, manager.activeCount());
        assertTrue(manager.getSession(sessionId).isPresent());
        assertEquals(150L, manager.getSession(sessionId).get().getExpireTick());

        // Transform display
        DisplayTransformOptions transform = DisplayTransformOptions.builder()
                .scale(3.0f, 3.0f, 3.0f)
                .translation(0, 0, 0)
                .interpolation(20, 0)
                .build();
        assertTrue(manager.transformDisplay(sessionId, transform));
        assertEquals(transform, manager.getSession(sessionId).get().getCurrentTransform());

        // Tick before expiration: still active
        manager.tick(140L);
        assertEquals(1, manager.activeCount());

        // Tick at/after expiration: auto-removed
        manager.tick(150L);
        assertEquals(0, manager.activeCount());
        assertFalse(manager.getSession(sessionId).isPresent());
    }

    @Test
    @DisplayName("cleanupMob and cleanupAll properly purge tracked display entities")
    void testDisplayCleanup() {
        World world = createMockWorld();
        DisplayEntityManager manager = new DisplayEntityManager();

        UUID mobA = UUID.randomUUID();
        UUID mobB = UUID.randomUUID();
        Location loc = new Location(world, 0, 64, 0);
        DisplaySpawnOptions opts = DisplaySpawnOptions.builder(DisplayType.ITEM)
                .duration(200)
                .build();

        UUID s1 = manager.spawnDisplay(loc, opts, 0L, mobA);
        UUID s2 = manager.spawnDisplay(loc, opts, 0L, mobA);
        UUID s3 = manager.spawnDisplay(loc, opts, 0L, mobB);

        assertEquals(3, manager.activeCount());

        manager.cleanupMob(mobA);
        assertEquals(1, manager.activeCount());
        assertFalse(manager.getSession(s1).isPresent());
        assertFalse(manager.getSession(s2).isPresent());
        assertTrue(manager.getSession(s3).isPresent());

        manager.cleanupAll();
        assertEquals(0, manager.activeCount());
    }

    @Test
    @DisplayName("MechanicRegistry executes spawndisplay and displaytransform correctly")
    void testDisplayMechanicsIntegration() {
        World world = createMockWorld();
        ActiveLunarMob mob = createDummyMob(world);
        MechanicRegistry registry = new MechanicRegistry();

        SkillDefinition skill = new SkillDefinition("meteor_strike", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 10L);
        MechanicContext mechContext = new MechanicContext(castContext, List.of());

        // Execute spawndisplay
        MechanicResult spawnResult = registry.execute("spawndisplay", mechContext, Map.of(
                "type", "BLOCK",
                "block", "MAGMA_BLOCK",
                "scale", "2.5,2.5,2.5",
                "translation", "0,10,0",
                "rotation", "0,45,0",
                "duration", 80,
                "interpolation", 10
        ));

        assertTrue(spawnResult.isSuccess(), "Spawn display mechanic failed: " + spawnResult.error());
        assertEquals(1, registry.displayEntityManager().activeCount());

        // Execute displaytransform
        MechanicResult transformResult = registry.execute("displaytransform", mechContext, Map.of(
                "scale", "1.0,1.0,1.0",
                "translation", "0,0,0",
                "rotation", "0,90,0",
                "interpolation", 30
        ));

        assertTrue(transformResult.isSuccess(), "Display transform mechanic failed: " + transformResult.error());
        ActiveDisplaySession session = registry.displayEntityManager().getActiveSessions().values().iterator().next();
        assertNotNull(session.getCurrentTransform());
        assertEquals(30, session.getCurrentTransform().interpolationDuration());
        assertEquals(new Vector3f(1.0f, 1.0f, 1.0f), session.getCurrentTransform().scale());
        assertEquals(new Vector3f(0.0f, 0.0f, 0.0f), session.getCurrentTransform().translation());
    }
}
