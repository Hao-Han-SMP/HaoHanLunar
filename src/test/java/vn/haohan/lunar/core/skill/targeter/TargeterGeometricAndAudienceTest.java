package vn.haohan.lunar.core.skill.targeter;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargeterGeometricAndAudienceTest {

    @Test
    void coneCatchesInFrontAndRejectsBehind() {
        TargeterRegistry registry = new TargeterRegistry();
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        // Caster at (0, 64, 0) facing +Z (yaw 0)
        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(0, 0, 1));
        // Target 1: at (0, 64, 10) directly ahead -> within 60 deg cone
        LivingEntity targetAhead = mockLiving(world, 0, 64, 10, new Vector(0, 0, -1));
        // Target 2: at (0, 64, -10) directly behind -> outside cone
        LivingEntity targetBehind = mockLiving(world, 0, 64, -10, new Vector(0, 0, 1));
        // Target 3: at (20, 64, 0) strictly 90 deg to side -> outside 60 deg cone
        LivingEntity targetSide = mockLiving(world, 20, 64, 0, new Vector(-1, 0, 0));

        worldEntities.addAll(List.of(caster, targetAhead, targetBehind, targetSide));

        SkillDefinition skill = new SkillDefinition("breath", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> targets = registry.resolveEntities("@Cone{angle=60;r=15}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(targetAhead));
        assertFalse(targets.contains(targetBehind));
        assertFalse(targets.contains(targetSide));
        assertFalse(targets.contains(caster));
    }

    @Test
    void behindCatchesBehindAndRejectsInFront() {
        TargeterRegistry registry = new TargeterRegistry();
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        // Caster facing +Z
        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(0, 0, 1));
        // Directly behind at (0, 64, -8)
        LivingEntity targetBehind = mockLiving(world, 0, 64, -8, new Vector(0, 0, 1));
        // Directly ahead at (0, 64, 8)
        LivingEntity targetAhead = mockLiving(world, 0, 64, 8, new Vector(0, 0, -1));

        worldEntities.addAll(List.of(caster, targetBehind, targetAhead));

        SkillDefinition skill = new SkillDefinition("tail_whip", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> targets = registry.resolveEntities("@Behind{r=12;angle=120}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(targetBehind));
        assertFalse(targets.contains(targetAhead));
    }

    @Test
    void inFrontCatchesFrontAndRejectsRear() {
        TargeterRegistry registry = new TargeterRegistry();
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(1, 0, 0)); // facing +X
        LivingEntity frontEntity = mockLiving(world, 6, 64, 0, new Vector(-1, 0, 0));
        LivingEntity rearEntity = mockLiving(world, -6, 64, 0, new Vector(1, 0, 0));

        worldEntities.addAll(List.of(caster, frontEntity, rearEntity));

        SkillDefinition skill = new SkillDefinition("slash", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> targets = registry.resolveEntities("@InFront{r=10;angle=90}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(frontEntity));
        assertFalse(targets.contains(rearEntity));
    }

    @Test
    void ringTargeterSelectsEntitiesWithinRadiusDonut() {
        TargeterRegistry registry = new TargeterRegistry();
        List<LivingEntity> worldEntities = new ArrayList<>();
        World world = mockWorldWithLiving("world", worldEntities);

        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(0, 0, 1));
        // Inside inner hole: dist 3 -> should be rejected when innerRadius = 10 - 4 = 6
        LivingEntity insideHole = mockLiving(world, 3, 64, 0, new Vector(0, 0, 1));
        // Inside ring: dist 8 -> accepted (between 6 and 10)
        LivingEntity inRing = mockLiving(world, 8, 64, 0, new Vector(0, 0, 1));
        // Outside ring: dist 15 -> rejected (> 10)
        LivingEntity outsideRing = mockLiving(world, 15, 64, 0, new Vector(0, 0, 1));

        worldEntities.addAll(List.of(caster, insideHole, inRing, outsideRing));

        SkillDefinition skill = new SkillDefinition("ring_pulse", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> targets = registry.resolveEntities("@Ring{radius=10;width=4}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(inRing));
        assertFalse(targets.contains(insideHole));
        assertFalse(targets.contains(outsideRing));
    }

    @Test
    void audienceIncludesCreativeModeAndRespectsRadius() {
        TargeterRegistry registry = new TargeterRegistry();
        List<Player> worldPlayers = new ArrayList<>();
        World world = mockWorldWithPlayers("world", worldPlayers);

        Player creativeNear = mockPlayer(world, 10, 64, 0, GameMode.CREATIVE);
        Player survivalNear = mockPlayer(world, 20, 64, 0, GameMode.SURVIVAL);
        Player spectatorNear = mockPlayer(world, 5, 64, 0, GameMode.SPECTATOR);
        Player survivalFar = mockPlayer(world, 50, 64, 0, GameMode.SURVIVAL);

        worldPlayers.addAll(List.of(creativeNear, survivalNear, spectatorNear, survivalFar));

        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(0, 0, 1));
        SkillDefinition skill = new SkillDefinition("roar", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> audience = registry.resolveEntities("@Audience{r=32}", context);
        assertEquals(2, audience.size());
        assertTrue(audience.contains(creativeNear), "Creative mode player must receive audience VFX/sound");
        assertTrue(audience.contains(survivalNear));
        assertFalse(audience.contains(spectatorNear), "Spectator excluded by default");
        assertFalse(audience.contains(survivalFar), "Outside 32 block range");
    }

    @Test
    void highestBlockTargeterResolvesSurfaceY() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorldWithHighestBlock("world", 85);

        LivingEntity caster = mockLiving(world, 12, 64, -5, new Vector(0, 0, 1));
        SkillDefinition skill = new SkillDefinition("meteor", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<Location> locations = registry.resolveLocations("@HighestBlock{y_offset=1.5}", context);
        assertEquals(1, locations.size());
        Location loc = locations.iterator().next();
        assertEquals(12.0, loc.getX());
        assertEquals(86.5, loc.getY()); // 85 + 1.5
        assertEquals(-5.0, loc.getZ());
    }

    @Test
    void nearestPlayerPicksClosestValidPlayer() {
        TargeterRegistry registry = new TargeterRegistry();
        List<Player> worldPlayers = new ArrayList<>();
        World world = mockWorldWithPlayers("world", worldPlayers);

        Player near = mockPlayer(world, 5, 64, 0, GameMode.SURVIVAL);
        Player far = mockPlayer(world, 18, 64, 0, GameMode.SURVIVAL);
        worldPlayers.addAll(List.of(far, near));

        LivingEntity caster = mockLiving(world, 0, 64, 0, new Vector(0, 0, 1));
        SkillDefinition skill = new SkillDefinition("bite", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, caster.getLocation());

        Collection<LivingEntity> nearest = registry.resolveEntities("@NearestPlayer{r=25}", context);
        assertEquals(1, nearest.size());
        assertEquals(near, nearest.iterator().next());
    }

    @Test
    void aliasesAreProperlyRegistered() {
        TargeterRegistry registry = new TargeterRegistry();
        assertTrue(registry.hasTargeter("@PIR"));
        assertTrue(registry.hasTargeter("@EIR"));
        assertTrue(registry.hasTargeter("@ThreatTop"));
        assertTrue(registry.hasTargeter("@ThreatTableTop"));
        assertTrue(registry.hasTargeter("@Behind"));
        assertTrue(registry.hasTargeter("@InFront"));
        assertTrue(registry.hasTargeter("@Audience"));
        assertTrue(registry.hasTargeter("@HighestBlock"));
    }

    // --- Mock Helpers ---

    private static ActiveLunarMob activeMob(LivingEntity entity) {
        MobDefinition definition = new MobDefinition(new MobDefinitionId("boss"), EntityType.WITHER,
                "Boss", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("boss", "1"));
    }

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

    private static World mockWorldWithPlayers(String name, List<Player> players) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getNearbyEntities" -> {
                        Location center = (Location) args[0];
                        double rx = ((Number) args[1]).doubleValue();
                        yield players.stream()
                                .filter(p -> center.distance(p.getLocation()) <= rx)
                                .map(p -> (Entity) p)
                                .toList();
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> null;
                });
    }

    private static World mockWorldWithHighestBlock(String name, int highestY) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getHighestBlockYAt" -> highestY;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> null;
                });
    }

    private static LivingEntity mockLiving(World world, double x, double y, double z, Vector dir) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        loc.setDirection(dir);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "getEyeLocation" -> loc;
                    case "getType" -> EntityType.ZOMBIE;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
    }

    private static Player mockPlayer(World world, double x, double y, double z, GameMode gameMode) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "getEyeLocation" -> loc;
                    case "getType" -> EntityType.PLAYER;
                    case "isValid" -> true;
                    case "isOnline" -> true;
                    case "isDead" -> false;
                    case "getGameMode" -> gameMode;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                });
    }
}
