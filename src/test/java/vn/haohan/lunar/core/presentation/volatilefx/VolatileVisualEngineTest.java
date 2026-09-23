package vn.haohan.lunar.core.presentation.volatilefx;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.presentation.volatilefx.VolatileVisualEngine;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicResult;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class VolatileVisualEngineTest {

    private World mockWorld;
    private MechanicRegistry mechanicRegistry;
    private final UUID worldUid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        VolatileVisualEngine.clearAll();
        mechanicRegistry = new MechanicRegistry();

        BlockData mockBlockData = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class}, (p, m, a) -> null);

        mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "lunar_world";
                    if (method.getName().equals("getUID")) return worldUid;
                    if (method.getName().equals("getPlayers")) return List.of();
                    if (method.getName().equals("getBlockAt")) {
                        return createMockBlock((Integer) args[0], (Integer) args[1], (Integer) args[2], mockBlockData);
                    }
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return worldUid.hashCode();
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        VolatileVisualEngine.clearAll();
    }

    private Block createMockBlock(int x, int y, int z, BlockData blockData) {
        Location blockLoc = new Location(mockWorld, x, y, z);
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
                new Class<?>[]{Block.class}, (p, m, a) -> {
                    if (m.getName().equals("getType")) return Material.STONE;
                    if (m.getName().equals("getLocation")) return blockLoc.clone();
                    if (m.getName().equals("getBlockData")) return blockData;
                    if (m.getName().equals("getX")) return x;
                    if (m.getName().equals("getY")) return y;
                    if (m.getName().equals("getZ")) return z;
                    return null;
                });
    }

    @Test
    @DisplayName("playGroundCrack records active cracked blocks and tick cleans them up on expiry")
    void testGroundCrackTrackingAndTickCleanup() {
        Location center = new Location(mockWorld, 0, 64, 0);
        AtomicInteger packetsSent = new AtomicInteger(0);

        Player mockPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("getLocation")) return center.clone();
                    if (method.getName().equals("sendBlockDamage")) {
                        packetsSent.incrementAndGet();
                        return null;
                    }
                    return null;
                });

        VolatileVisualEngine.playGroundCrack(center, 3.0, 30, 0.85f, List.of(mockPlayer));

        assertTrue(VolatileVisualEngine.activeCrackCount() > 0);
        assertTrue(packetsSent.get() > 0);

        // Advance ticks beyond expiration (e.g. 50 ticks)
        long currentTick = System.currentTimeMillis() / 50L + 50L;
        VolatileVisualEngine.tick(currentTick);

        assertEquals(0, VolatileVisualEngine.activeCrackCount());
    }

    @Test
    @DisplayName("playCameraShake sends hurt animation tilt and micro kinetic recoil to nearby players")
    void testCameraShakeRecoilAndHurtAnimation() {
        Location center = new Location(mockWorld, 10, 64, 10);
        AtomicBoolean hurtAnimationSent = new AtomicBoolean(false);
        AtomicBoolean velocityApplied = new AtomicBoolean(false);

        Player mockPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 12, 64, 10);
                    if (method.getName().equals("sendHurtAnimation")) {
                        hurtAnimationSent.set(true);
                        return null;
                    }
                    if (method.getName().equals("getVelocity")) return new Vector(0, 0, 0);
                    if (method.getName().equals("setVelocity")) {
                        velocityApplied.set(true);
                        return null;
                    }
                    return null;
                });

        VolatileVisualEngine.playCameraShake(center, 12.0, 1.0f, List.of(mockPlayer));

        assertTrue(hurtAnimationSent.get());
        assertTrue(velocityApplied.get());
    }

    @Test
    @DisplayName("MechanicRegistry executes fake_block_crack, camera_shake, and ground_slam_fx successfully")
    void testVolatileMechanicsInRegistry() {
        Location loc = new Location(mockWorld, 0, 64, 0);
        ActiveLunarMob mob = createDummyMob("lunar_warden", loc);

        SkillDefinition skill = new SkillDefinition("slam_combo", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 0);
        MechanicContext mechContext = new MechanicContext(castContext, List.of(TargetRef.of(loc)));

        MechanicResult r1 = mechanicRegistry.execute("fake_block_crack", mechContext, Map.of("radius", 4.0, "duration", 25));
        assertTrue(r1.isSuccess());

        MechanicResult r2 = mechanicRegistry.execute("camera_shake", mechContext, Map.of("radius", 10.0, "intensity", 1.2));
        assertTrue(r2.isSuccess());

        MechanicResult r3 = mechanicRegistry.execute("ground_slam_fx", mechContext, Map.of("radius", 5.0, "duration", 30));
        assertTrue(r3.isSuccess());
    }

    private ActiveLunarMob createDummyMob(String id, Location loc) {
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return mockWorld;
                    return null;
                });

        MobDefinition def = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.IRON_GOLEM,
                "Lunar Warden",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );

        return new ActiveLunarMob(entity, def, new LunarMobIdentity(id, "1"));
    }
}
