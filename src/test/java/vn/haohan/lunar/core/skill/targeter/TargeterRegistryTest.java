package vn.haohan.lunar.core.skill.targeter;

import vn.haohan.lunar.api.system.combat.skill.targeter.*;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargeterRegistryTest {

    @Test
    void selfTargeterResolvesCasterAndIgnoresDeadCaster() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        LivingEntity aliveCaster = mockLivingEntity(world, 0, 64, 0, true, false);
        ActiveLunarMob mob = activeMob(aliveCaster);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 1);

        Collection<LivingEntity> targets = registry.resolveEntities("@self", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(aliveCaster));

        // Dead caster
        LivingEntity deadCaster = mockLivingEntity(world, 0, 64, 0, true, true);
        SkillCastContext deadContext = new SkillCastContext(activeMob(deadCaster), skill, SkillTrigger.ON_COMBAT, 1);
        Collection<LivingEntity> deadTargets = registry.resolveEntities("@self", deadContext);
        assertTrue(deadTargets.isEmpty());
    }

    @Test
    void targetTargeterResolvesMobTargetSafely() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        LivingEntity enemy = mockLivingEntity(world, 10, 64, 0, true, false);
        Mob casterMob = mockMob(world, 0, 64, 0, enemy);
        ActiveLunarMob mob = activeMob(casterMob);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 1);

        Collection<LivingEntity> targets = registry.resolveEntities("@target", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(enemy));

        // Target in different world -> ignored safely
        World otherWorld = mockWorld("overworld");
        LivingEntity crossWorldEnemy = mockLivingEntity(otherWorld, 10, 64, 0, true, false);
        Mob crossWorldCaster = mockMob(world, 0, 64, 0, crossWorldEnemy);
        SkillCastContext crossWorldContext = new SkillCastContext(activeMob(crossWorldCaster), skill, SkillTrigger.ON_COMBAT, 1);
        assertTrue(registry.resolveEntities("@target", crossWorldContext).isEmpty());
    }

    @Test
    void triggerTargeterResolvesLivingTriggerEntity() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        LivingEntity attacker = mockLivingEntity(world, 5, 64, 0, true, false);
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_DAMAGED), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_DAMAGED, 1,
                attacker, caster.getLocation());

        Collection<LivingEntity> targets = registry.resolveEntities("@trigger", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(attacker));
    }

    @Test
    void originTargeterReturnsClonedOriginLocation() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        Location origin = new Location(world, 15, 64, -20);
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, origin);

        Collection<Location> locations = registry.resolveLocations("@origin", context);
        assertEquals(1, locations.size());
        Location resolved = locations.iterator().next();
        assertEquals(15.0, resolved.getX());
        assertEquals(64.0, resolved.getY());
        assertEquals(-20.0, resolved.getZ());
    }

    @Test
    void playersInRadiusAppliesFilterRadiusCapAndLimit() {
        TargeterRegistry registry = new TargeterRegistry();
        List<Player> playersInWorld = new ArrayList<>();
        World world = mockWorldWithPlayers("lunar", playersInWorld);

        Player survivalNear = mockPlayer(world, 10, 64, 0, GameMode.SURVIVAL, true, false);
        Player survivalFar = mockPlayer(world, 50, 64, 0, GameMode.SURVIVAL, true, false);
        Player spectator = mockPlayer(world, 5, 64, 0, GameMode.SPECTATOR, true, false);
        Player creative = mockPlayer(world, 5, 64, 0, GameMode.CREATIVE, true, false);
        Player dead = mockPlayer(world, 5, 64, 0, GameMode.SURVIVAL, true, true);

        playersInWorld.addAll(List.of(survivalNear, survivalFar, spectator, creative, dead));

        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, new Location(world, 0, 64, 0));

        // Test with radius 20: only survivalNear should match (spectator, creative, dead filtered out)
        Collection<LivingEntity> targets = registry.resolveEntities("@PlayersInRadius{r=20}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(survivalNear));

        // Test radius cap: r=100 is capped at 64.0, both survivalNear (10) and survivalFar (50) match
        Collection<LivingEntity> targetsCapped = registry.resolveEntities("@PlayersInRadius{r=100}", context);
        assertEquals(2, targetsCapped.size());

        // Test limit=1: only the closest player (survivalNear at 10m) should be returned
        Collection<LivingEntity> targetsLimited = registry.resolveEntities("@PlayersInRadius{r=100;limit=1}", context);
        assertEquals(1, targetsLimited.size());
        assertEquals(survivalNear, targetsLimited.iterator().next());
    }

    @Test
    void livingEntitiesInRadiusExcludesCasterAndDeadEntities() {
        TargeterRegistry registry = new TargeterRegistry();
        List<LivingEntity> entitiesInWorld = new ArrayList<>();
        World world = mockWorldWithLivingEntities("lunar", entitiesInWorld);

        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        LivingEntity ally = mockLivingEntity(world, 8, 64, 0, true, false);
        LivingEntity deadMob = mockLivingEntity(world, 4, 64, 0, true, true);

        entitiesInWorld.addAll(List.of(caster, ally, deadMob));

        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, new Location(world, 0, 64, 0));

        Collection<LivingEntity> targets = registry.resolveEntities("@LivingEntitiesInRadius{r=16}", context);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(ally));
        assertFalse(targets.contains(caster));
        assertFalse(targets.contains(deadMob));
    }

    @Test
    void locationTargeterHandlesAbsoluteAndRelativeCoordinates() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        LivingEntity caster = mockLivingEntity(world, 100, 70, -50, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, new Location(world, 100, 70, -50));

        // Absolute
        Collection<Location> absolute = registry.resolveLocations("@Location{x=10;y=65;z=20}", context);
        assertEquals(1, absolute.size());
        Location absLoc = absolute.iterator().next();
        assertEquals(10.0, absLoc.getX());
        assertEquals(65.0, absLoc.getY());
        assertEquals(20.0, absLoc.getZ());

        // Relative (~offset)
        Collection<Location> relative = registry.resolveLocations("@Location{x=~5;y=~-10;z=~2}", context);
        assertEquals(1, relative.size());
        Location relLoc = relative.iterator().next();
        assertEquals(105.0, relLoc.getX());
        assertEquals(60.0, relLoc.getY());
        assertEquals(-48.0, relLoc.getZ());
    }

    @Test
    void randomPlayerPicksOnePlayerFromRadius() {
        TargeterRegistry registry = new TargeterRegistry();
        List<Player> players = new ArrayList<>();
        World world = mockWorldWithPlayers("lunar", players);
        Player p1 = mockPlayer(world, 5, 64, 0, GameMode.SURVIVAL, true, false);
        Player p2 = mockPlayer(world, 10, 64, 0, GameMode.SURVIVAL, true, false);
        players.addAll(List.of(p1, p2));

        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1,
                null, new Location(world, 0, 64, 0));

        Collection<LivingEntity> result = registry.resolveEntities("@RandomPlayer{r=20}", context);
        assertEquals(1, result.size());
        assertTrue(result.contains(p1) || result.contains(p2));
    }

    @Test
    void returnsImmutableSnapshot() {
        TargeterRegistry registry = new TargeterRegistry();
        World world = mockWorld("lunar");
        LivingEntity caster = mockLivingEntity(world, 0, 64, 0, true, false);
        SkillDefinition skill = new SkillDefinition("slam", Set.of(SkillTrigger.ON_COMBAT), 20);
        SkillCastContext context = new SkillCastContext(activeMob(caster), skill, SkillTrigger.ON_COMBAT, 1);

        Collection<LivingEntity> targets = registry.resolveEntities("@self", context);
        assertThrows(UnsupportedOperationException.class, () -> targets.add(caster));
    }

    @Test
    void normalizesTargeterNamesAndHandlesParameters() {
        TargeterRegistry registry = new TargeterRegistry();
        TargeterRegistry.ParsedTargeterCall call1 = registry.parse("@PlayersInRadius{r=25;limit=3}");
        assertEquals("playersinradius", call1.targeterName());
        assertEquals(25L, call1.parameters().get("r"));
        assertEquals(3L, call1.parameters().get("limit"));

        TargeterRegistry.ParsedTargeterCall call2 = registry.parse("players_in_radius");
        assertEquals("playersinradius", call2.targeterName());

        TargeterRegistry.ParsedTargeterCall call3 = registry.parse("@self");
        assertEquals("self", call3.targeterName());
        assertTrue(call3.parameters().isEmpty());
    }

    // --- Mock Helpers ---

    private static ActiveLunarMob activeMob(LivingEntity entity) {
        MobDefinition definition = new MobDefinition(new MobDefinitionId("warden"), EntityType.IRON_GOLEM,
                "Warden", null, Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, definition, new LunarMobIdentity("warden", "1"));
    }

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "getPlayers" -> List.of();
                    case "getLivingEntities" -> List.of();
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World mockWorldWithPlayers(String name, List<Player> playerList) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "getPlayers" -> playerList;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static World mockWorldWithLivingEntities(String name, List<LivingEntity> entityList) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft(name);
                    case "getLivingEntities" -> entityList;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> name.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity mockLivingEntity(World world, double x, double y, double z, boolean valid, boolean dead) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> valid;
                    case "isDead" -> dead;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Mob mockMob(World world, double x, double y, double z, LivingEntity target) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> true;
                    case "isDead" -> false;
                    case "getTarget" -> target;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player mockPlayer(World world, double x, double y, double z, GameMode gameMode, boolean online, boolean dead) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(world, x, y, z);
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getWorld" -> world;
                    case "getLocation" -> loc;
                    case "isValid" -> true;
                    case "isOnline" -> online;
                    case "isDead" -> dead;
                    case "getGameMode" -> gameMode;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
