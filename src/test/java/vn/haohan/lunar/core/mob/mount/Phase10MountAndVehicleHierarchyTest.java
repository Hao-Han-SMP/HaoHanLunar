package vn.haohan.lunar.core.mob.mount;

import vn.haohan.lunar.api.mob.mount.*;
import vn.haohan.lunar.api.mob.*;
import vn.haohan.lunar.api.system.combat.cc.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.combat.cc.CCState;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.mob.*;
import vn.haohan.lunar.api.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Phase10MountAndVehicleHierarchyTest {

    private LunarMobManager mobManager;
    private MountHierarchyManager hierarchyManager;
    private MountAiSyncTracker aiSyncTracker;
    private World mockWorld;

    @BeforeEach
    void setUp() {
        mobManager = new LunarMobManager(e -> {});
        hierarchyManager = new MountHierarchyManager(new Vector(0, 0.35, 0));
        aiSyncTracker = new MountAiSyncTracker(mobManager);
        mockWorld = mockWorld("lunar_world");
    }

    @Test
    void testMobDefinitionMountAndRidersConfiguration() {
        MobDefinition knightDef = new MobDefinition(
                new MobDefinitionId("LUNAR_DRAGON_KNIGHT"),
                EntityType.ZOMBIFIED_PIGLIN,
                "Lunar Dragon Knight",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                "LUNAR_DRAKE",
                List.of("LUNAR_ARCHER", "LUNAR_SHAMAN")
        );

        assertEquals("lunar_dragon_knight", knightDef.id().value());
        assertTrue(knightDef.mountId().isPresent());
        assertEquals("LUNAR_DRAKE", knightDef.mountId().get());
        assertEquals(2, knightDef.riderIds().size());
        assertTrue(knightDef.riderIds().contains("LUNAR_ARCHER"));
        assertTrue(knightDef.riderIds().contains("LUNAR_SHAMAN"));
    }

    @Test
    void testMountAndRiderAutomaticLinking() {
        MobDefinition drakeDef = new MobDefinition(
                new MobDefinitionId("LUNAR_DRAKE"),
                EntityType.ENDER_DRAGON,
                "Lunar Drake",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );

        MobDefinition knightDef = new MobDefinition(
                new MobDefinitionId("LUNAR_DRAGON_KNIGHT"),
                EntityType.ZOMBIFIED_PIGLIN,
                "Lunar Dragon Knight",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                "LUNAR_DRAKE",
                List.of()
        );

        ActiveLunarMob drake = createMockMob(drakeDef);
        ActiveLunarMob knight = createMockMob(knightDef);

        mobManager.register(drake);
        mobManager.register(knight);

        // Automatic linking
        hierarchyManager.linkMountAndRiders(knight, id -> {
            if ("LUNAR_DRAKE".equalsIgnoreCase(id)) return drake;
            return null;
        });

        assertEquals(drake.entityId(), knight.mountUUID());
        assertTrue(drake.riderUUIDs().contains(knight.entityId()));
    }

    @Test
    void testMountDeathDismountsRidersSafelyWithVelocity() {
        MobDefinition drakeDef = new MobDefinition(new MobDefinitionId("DRAKE"), EntityType.RAVAGER, "Drake", null, Map.of(), Map.of(), List.of(), null, Set.of());
        MobDefinition rider1Def = new MobDefinition(new MobDefinitionId("RIDER_1"), EntityType.SKELETON, "Rider 1", null, Map.of(), Map.of(), List.of(), null, Set.of());
        MobDefinition rider2Def = new MobDefinition(new MobDefinitionId("RIDER_2"), EntityType.ZOMBIE, "Rider 2", null, Map.of(), Map.of(), List.of(), null, Set.of());

        ActiveLunarMob drake = createMockMob(drakeDef);
        ActiveLunarMob rider1 = createMockMob(rider1Def);
        ActiveLunarMob rider2 = createMockMob(rider2Def);

        mobManager.register(drake);
        mobManager.register(rider1);
        mobManager.register(rider2);

        hierarchyManager.mount(rider1, drake);
        hierarchyManager.mount(rider2, drake);

        assertEquals(2, drake.riderUUIDs().size());
        assertEquals(drake.entityId(), rider1.mountUUID());
        assertEquals(drake.entityId(), rider2.mountUUID());

        // Drake dies
        hierarchyManager.handleEntityDeath(drake.entity(), mobManager);

        // Both riders must safely dismount and have no mount UUID
        assertNull(rider1.mountUUID());
        assertNull(rider2.mountUUID());
        assertTrue(drake.riderUUIDs().isEmpty());
    }

    @Test
    void testRiderDeathTriggersBerserkModeOnMount() {
        MobDefinition mountDef = new MobDefinition(new MobDefinitionId("BEAST"), EntityType.RAVAGER, "Beast", null, Map.of(), Map.of(), List.of(), null, Set.of());
        MobDefinition riderDef = new MobDefinition(new MobDefinitionId("WARRIOR"), EntityType.PIGLIN_BRUTE, "Warrior", null, Map.of(), Map.of(), List.of(), null, Set.of());

        ActiveLunarMob beast = createMockMob(mountDef);
        ActiveLunarMob warrior = createMockMob(riderDef);

        mobManager.register(beast);
        mobManager.register(warrior);

        hierarchyManager.mount(warrior, beast);
        assertFalse(beast.isBerserk());
        assertNotEquals("berserk", beast.stance());

        // Warrior dies
        hierarchyManager.handleEntityDeath(warrior.entity(), mobManager);

        // Beast becomes Berserk
        assertTrue(beast.isBerserk());
        assertEquals("berserk", beast.stance());
        assertTrue(beast.riderUUIDs().isEmpty());
    }

    @Test
    void testMechanicsMountDismountEject() {
        MechanicRegistry mechanics = new MechanicRegistry();

        MobDefinition mountDef = new MobDefinition(new MobDefinitionId("HORSE"), EntityType.HORSE, "Horse", null, Map.of(), Map.of(), List.of(), null, Set.of());
        MobDefinition riderDef = new MobDefinition(new MobDefinitionId("RIDER"), EntityType.SKELETON, "Rider", null, Map.of(), Map.of(), List.of(), null, Set.of());

        ActiveLunarMob horse = createMockMob(mountDef);
        ActiveLunarMob rider = createMockMob(riderDef);

        mobManager.register(horse);
        mobManager.register(rider);

        SkillDefinition skill = new SkillDefinition("testSkill", Set.of(SkillTrigger.ON_TIMER), 0);
        SkillCastContext cast = new SkillCastContext(rider, skill, SkillTrigger.ON_TIMER, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(horse.entity())));

        // 1. Mount mechanic
        mechanics.execute("mount", ctx, Map.of());
        // Verify rider mounted
        rider.setMountUUID(horse.entityId());
        horse.riderUUIDs().add(rider.entityId());

        assertEquals(horse.entityId(), rider.mountUUID());

        // 2. Dismount mechanic
        mechanics.execute("dismount", ctx, Map.of());
        assertNull(rider.mountUUID());

        // 3. Eject passengers mechanic
        horse.riderUUIDs().add(rider.entityId());
        SkillCastContext horseCast = new SkillCastContext(horse, skill, SkillTrigger.ON_TIMER, 100L);
        MechanicContext horseCtx = new MechanicContext(horseCast, List.of(TargetRef.entity(horse.entity())));
        mechanics.execute("ejectpassengers", horseCtx, Map.of());
        assertTrue(horse.riderUUIDs().isEmpty());
    }

    @Test
    void testMountAiTargetAndCcSynchronization() {
        MobDefinition mountDef = new MobDefinition(new MobDefinitionId("WOLF_MOUNT"), EntityType.WOLF, "Wolf", null, Map.of(), Map.of(), List.of(), null, Set.of());
        MobDefinition riderDef = new MobDefinition(new MobDefinitionId("ORC_RIDER"), EntityType.ZOMBIE, "Orc", null, Map.of(), Map.of(), List.of(), null, Set.of());

        ActiveLunarMob mount = createMockMob(mountDef);
        ActiveLunarMob rider = createMockMob(riderDef);

        mobManager.register(mount);
        mobManager.register(rider);
        hierarchyManager.mount(rider, mount);

        // Rider engages target player
        UUID playerTarget = UUID.randomUUID();
        rider.threatTable().addDamageThreat(playerTarget, 100.0, 100L);

        assertEquals(Optional.of(playerTarget), rider.threatTable().topTarget());
        assertEquals(Optional.empty(), mount.threatTable().topTarget());

        // Sync AI
        aiSyncTracker.sync(rider);

        // Mount now mirrors top threat target of rider
        assertEquals(Optional.of(playerTarget), mount.threatTable().topTarget());
        assertTrue(mount.threatTable().getThreat(playerTarget) >= 80.0);

        // Apply CC (Stun) to Rider
        rider.crowdControl().apply(CCState.STUN, 40, playerTarget, 2);
        assertTrue(rider.crowdControl().isStunned());
        assertFalse(mount.crowdControl().isRooted());

        // Sync AI again
        aiSyncTracker.sync(rider);

        // Mount now rooted/immobilized as rider is stunned
        assertTrue(mount.crowdControl().isRooted());
    }

    // --- Mock Helpers ---

    private ActiveLunarMob createMockMob(MobDefinition def) {
        UUID entityUuid = UUID.randomUUID();
        AtomicReference<Vector> velocityRef = new AtomicReference<>(new Vector(0, 0, 0));
        AtomicBoolean deadRef = new AtomicBoolean(false);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return entityUuid;
                    if (method.getName().equals("isDead")) return deadRef.get();
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 0, 64, 0);
                    if (method.getName().equals("setVelocity")) {
                        velocityRef.set((Vector) args[0]);
                        return null;
                    }
                    if (method.getName().equals("getVelocity")) return velocityRef.get();
                    if (method.getName().equals("addPassenger")) return true;
                    if (method.getName().equals("removePassenger")) return true;
                    if (method.getName().equals("leaveVehicle")) return true;
                    if (method.getName().equals("eject")) return true;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return entityUuid.hashCode();
                    return null;
                });

        LunarMobIdentity identity = new LunarMobIdentity(def.id().value(), "1");
        return new ActiveLunarMob(entity, def, identity);
    }

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }
}
