package vn.haohan.lunar.core.integration.modelengine;

import vn.haohan.lunar.api.integration.bridge.modelengine.BoneLocationResolver;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry;
import vn.haohan.lunar.api.system.combat.skill.targeter.impl.BoneLocationTargeter;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BoneLocationTargeterTest {

    private World createMockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });
    }

    private LivingEntity createMockLivingEntity(World world, double x, double y, double z) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        Location eyeLoc = new Location(world, x, y + 1.8, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getEyeLocation")) return eyeLoc.clone();
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("getType")) return EntityType.IRON_GOLEM;
                    if (method.getName().equals("getHealth")) return 500.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private ActiveLunarMob createActiveMob(World world, double x, double y, double z) {
        LivingEntity entity = createMockLivingEntity(world, x, y, z);
        MobDefinition def = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity("warden", "1"));
    }

    private SkillCastContext createContext(ActiveLunarMob mob) {
        SkillDefinition skill = new SkillDefinition("bone_skill", Set.of(SkillTrigger.ON_COMBAT), 20);
        return new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100L);
    }

    @Test
    @DisplayName("BoneLocationTargeter falls back gracefully to eye location when ModelEngine is not present")
    void testBoneFallbackToEyeLocation() {
        World world = createMockWorld();
        ActiveLunarMob caster = createActiveMob(world, 10.0, 64.0, 20.0);
        SkillCastContext context = createContext(caster);

        BoneLocationTargeter targeter = new BoneLocationTargeter();

        Collection<Location> result = targeter.resolve(context, Map.of("bone", "head"));
        assertNotNull(result);
        assertEquals(1, result.size());
        Location resolved = result.iterator().next();
        assertEquals(10.0, resolved.getX());
        assertEquals(65.8, resolved.getY(), 0.001);
        assertEquals(20.0, resolved.getZ());
    }

    @Test
    @DisplayName("BoneLocationTargeter resolves custom bone with custom BoneLocationResolver")
    void testCustomBoneResolver() {
        World world = createMockWorld();
        Location customBoneLoc = new Location(world, 100.0, 70.0, -50.0);

        BoneLocationResolver customResolver = (entity, boneName) -> {
            if ("right_hand".equalsIgnoreCase(boneName)) {
                return customBoneLoc;
            }
            return entity.getLocation();
        };

        ActiveLunarMob caster = createActiveMob(world, 0.0, 0.0, 0.0);
        SkillCastContext context = createContext(caster);

        BoneLocationTargeter targeter = new BoneLocationTargeter(customResolver);

        Collection<Location> result = targeter.resolve(context, Map.of("bone", "right_hand"));
        assertEquals(1, result.size());
        assertEquals(customBoneLoc, result.iterator().next());
    }

    @Test
    @DisplayName("TargeterRegistry parses and executes @BoneLocation and @Bone inline expressions")
    void testTargeterRegistryIntegration() {
        TargeterRegistry registry = new TargeterRegistry();
        assertTrue(registry.isLocationTargeter("bonelocation"));
        assertTrue(registry.isLocationTargeter("bone"));

        World world = createMockWorld();
        ActiveLunarMob caster = createActiveMob(world, 5.0, 60.0, 5.0);
        SkillCastContext context = createContext(caster);

        Collection<Location> locs = registry.resolveLocations("@BoneLocation{bone=mouth}", context);
        assertEquals(1, locs.size());
        assertEquals(61.8, locs.iterator().next().getY(), 0.001);

        Collection<Location> locsBone = registry.resolveLocations("@Bone{bone=head}", context);
        assertEquals(1, locsBone.size());
        assertEquals(61.8, locsBone.iterator().next().getY(), 0.001);
    }
}
