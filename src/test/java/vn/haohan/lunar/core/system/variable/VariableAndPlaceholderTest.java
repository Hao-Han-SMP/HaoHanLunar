package vn.haohan.lunar.core.system.variable;

import vn.haohan.lunar.api.integration.bridge.placeholder.PlaceholderResolver;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableAndPlaceholderTest {

    private World world;
    private VariableManager variableManager;

    @BeforeEach
    void setUp() {
        world = mockWorld();
        variableManager = new VariableManager();
    }

    @Test
    void variableValueHandlesTypedConversionsAndParsing() {
        VariableValue intVal = VariableValue.of(42);
        assertEquals(42, intVal.asInt());
        assertEquals("42", intVal.asString());
        assertEquals(42.0f, intVal.asFloat());
        assertEquals(42.0, intVal.asDouble());

        VariableValue boolVal = VariableValue.of(true);
        assertTrue(boolVal.asBoolean());
        assertEquals("true", boolVal.asString());

        VariableValue doubleVal = VariableValue.of(123.456);
        assertEquals(123.456, doubleVal.asDouble(), 0.0001);
        assertEquals(VariableType.DOUBLE, doubleVal.type());

        VariableValue parsedInt = VariableValue.parse("100");
        assertEquals(100, parsedInt.asInt());
        assertEquals(VariableType.INT, parsedInt.type());

        VariableValue parsedBool = VariableValue.parse("false");
        assertFalse(parsedBool.asBoolean());
        assertEquals(VariableType.BOOLEAN, parsedBool.type());

        VariableValue parsedDouble = VariableValue.parse("3.14");
        assertEquals(3.14, parsedDouble.asDouble(), 0.001);
        assertEquals(VariableType.DOUBLE, parsedDouble.type());

        VariableValue parsedStr = VariableValue.parse("hello_lunar");
        assertEquals("hello_lunar", parsedStr.asString());
        assertEquals(VariableType.STRING, parsedStr.type());
    }

    @Test
    void variableHolderManagesNormalizedKeysAndSnapshots() {
        VariableHolder holder = new VariableHolder();
        holder.set("My_Var", 10);
        holder.set("flag", true);

        assertTrue(holder.has("my_var"));
        assertTrue(holder.has("MY_VAR"));
        assertEquals(10, holder.get("my_var").orElseThrow().asInt());

        var snapshot = holder.snapshot();
        assertEquals(2, snapshot.size());

        holder.remove("my_var");
        assertFalse(holder.has("my_var"));
        assertEquals(1, holder.size());
    }

    @Test
    void pdcVariablePersistenceWorksSafely() {
        Map<NamespacedKey, Object> pdcStorage = new HashMap<>();
        PersistentDataContainer mockPdc = (PersistentDataContainer) Proxy.newProxyInstance(
                PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("set")) {
                        pdcStorage.put((NamespacedKey) args[0], args[2]);
                        return null;
                    }
                    if (method.getName().equals("get")) {
                        return pdcStorage.get((NamespacedKey) args[0]);
                    }
                    return null;
                }
        );

        UUID uuid = UUID.randomUUID();
        LivingEntity mockEntity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPersistentDataContainer")) return mockPdc;
                    return null;
                }
        );

        variableManager.saveToPdc(mockEntity, "custom_kill_count", VariableValue.of(15));
        var loaded = variableManager.loadFromPdc(mockEntity, "custom_kill_count");
        assertTrue(loaded.isPresent());
        assertEquals(15, loaded.get().asInt());
        assertEquals(VariableType.INT, loaded.get().type());
    }

    @Test
    void variableManagerCleansUpOnEvents() {
        UUID playerUuid = UUID.randomUUID();
        UUID mobUuid = UUID.randomUUID();

        variableManager.getPlayer(playerUuid).set("score", 500);
        variableManager.getCaster(mobUuid).set("rage", 100);

        assertEquals(500, variableManager.getPlayer(playerUuid).get("score").orElseThrow().asInt());
        assertEquals(100, variableManager.getCaster(mobUuid).get("rage").orElseThrow().asInt());

        // PlayerQuitEvent
        Player mockPlayer = mockPlayer(playerUuid, "TestPlayer");
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(mockPlayer, (net.kyori.adventure.text.Component) null);
        variableManager.onPlayerQuit(quitEvent);

        assertEquals(0, variableManager.getPlayer(playerUuid).size(), "Player variables should be empty after quit");

        // EntityDeathEvent
        LivingEntity mobEntity = mockEntity(mobUuid, 0, 0, 0);
        EntityDeathEvent deathEvent = new EntityDeathEvent(mobEntity, (org.bukkit.damage.DamageSource) null, Collections.emptyList());
        variableManager.onEntityDeath(deathEvent);

        assertEquals(0, variableManager.getCaster(mobUuid).size(), "Caster variables should be empty after death");
    }

    @Test
    void placeholderResolverInterpolatesBuiltinsAndVariables() {
        UUID mobUuid = UUID.randomUUID();
        LivingEntity mobEntity = mockEntity(mobUuid, 0, 64, 0);
        MobDefinition mobDef = testDefinition("lunar_boss");
        ActiveLunarMob mob = new ActiveLunarMob(mobEntity, mobDef, new LunarMobIdentity("lunar_boss", "1.0"));
        mob.setStance("enraged");

        UUID playerUuid = UUID.randomUUID();
        Player targetPlayer = mockPlayer(playerUuid, "HeroPlayer");

        // Context with cast variables
        SkillDefinition skill = testSkill("slam");
        SkillCastContext context = new SkillCastContext(mob, skill, SkillTrigger.ON_ATTACK, 1L);
        context.castVariables().set("combo", 3);

        // Variables
        variableManager.getGlobal().set("blood_moon", "ACTIVE");
        variableManager.getCaster(mobUuid).set("rage", 99);
        variableManager.getPlayer(playerUuid).set("bounty", 1000);

        String template = "Boss <mob.name> (ID: <mob.id>) stance=<mob.stance> HP=<mob.health>/<mob.max_health>. " +
                "Target <target.name> distance=<target.distance>. " +
                "Vars: Global=<global.var.blood_moon>, Caster=<caster.var.rage>, Target=<target.var.bounty>, Cast=<cast.var.combo>. " +
                "Math: <skill.calc.10 * 5 + 2>";

        String result = PlaceholderResolver.resolve(template, context, targetPlayer, variableManager);

        assertTrue(result.contains("Boss Lunar Boss (ID: lunar_boss) stance=enraged HP=500/500"));
        assertTrue(result.contains("Target HeroPlayer distance=10.0"));
        assertTrue(result.contains("Vars: Global=ACTIVE, Caster=99, Target=1000, Cast=3"));
        assertTrue(result.contains("Math: 52"));
    }

    @Test
    void placeholderResolverHandlesRecursionGracefully() {
        variableManager.getGlobal().set("a", "<global.var.b>");
        variableManager.getGlobal().set("b", "<global.var.a>");

        String template = "Loop: <global.var.a>";
        String result = PlaceholderResolver.resolve(template, (ActiveLunarMob) null, null, variableManager);
        assertFalse(result.isEmpty());
    }

    // --- Helpers ---

    private MobDefinition testDefinition(String id) {
        return new MobDefinition(
                new MobDefinitionId(id),
                EntityType.IRON_GOLEM,
                "Lunar Boss",
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of("boss")
        );
    }

    private SkillDefinition testSkill(String id) {
        return new SkillDefinition(id, Set.of(SkillTrigger.ON_ATTACK), 0L, List.of());
    }

    private World mockWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "haohan:lunar";
                    case "getKey" -> org.bukkit.NamespacedKey.minecraft("lunar");
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> 1;
                    default -> null;
                });
    }

    private LivingEntity mockEntity(UUID uuid, double x, double y, double z) {
        Location loc = new Location(world, x, y, z);
        return (LivingEntity) Proxy.newProxyInstance(
                VariableAndPlaceholderTest.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getLocation" -> loc;
                    case "getWorld" -> world;
                    case "getHealth" -> 500.0;
                    case "getMaxHealth" -> 500.0;
                    case "getCustomName" -> "Lunar Boss";
                    case "getType" -> EntityType.IRON_GOLEM;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }

    private Player mockPlayer(UUID uuid, String name) {
        Location loc = new Location(world, 10, 64, 0);
        return (Player) Proxy.newProxyInstance(
                VariableAndPlaceholderTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "getLocation" -> loc;
                    case "getWorld" -> world;
                    case "getHealth" -> 20.0;
                    case "getMaxHealth" -> 20.0;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> uuid.hashCode();
                    default -> null;
                }
        );
    }
}
