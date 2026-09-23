package vn.haohan.lunar.core.system;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.integration.itemcore.HaoHanItemBridge;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.CooldownRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionContext;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionRegistry;
import vn.haohan.lunar.api.system.combat.skill.condition.ConditionResult;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.api.system.combat.skill.targeter.TargeterRegistry;
import vn.haohan.lunar.api.system.mob.ai.AIGoalType;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.entity.Mob;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;
import vn.haohan.lunar.core.subsystem.mob.LunarMobManager;
import vn.haohan.lunar.api.system.loot.DropEntry;
import vn.haohan.lunar.api.system.loot.DropMetadata;
import vn.haohan.lunar.api.system.loot.DropRollResult;
import vn.haohan.lunar.api.system.loot.DropTableDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinition;
import vn.haohan.lunar.api.system.mob.MobOptionDefinition;
import vn.haohan.lunar.api.system.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.mob.options.MobOptions;
import vn.haohan.lunar.api.system.spawner.fixed.FixedSpawnerManager;
import vn.haohan.lunar.api.system.spawner.fixed.LunarFixedSpawner;
import vn.haohan.lunar.api.system.spawner.fixed.SpawnerDefinition;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BetterMobs Enhancements: Faction, Drop Tables, Raycast, & Spawner State")
class BetterMobsEnhancementsTest {

    @Test
    @DisplayName("Drop table rolls EXP and recursively rolls nested sub-tables")
    void testCompositeDropTableWithExpAndSubTables() {
        ConditionRegistry condRegistry = new ConditionRegistry();
        HaoHanItemBridge bridge = HaoHanItemBridge.get();
        bridge.setItemStackFactory(TestItemStack::new);

        // 1. Sub-table: drops 2 diamonds
        DropTableDefinition subTable = new DropTableDefinition("rare_gem_table", List.of(
                new DropEntry("DIAMOND", 1.0, 2, 2, List.of())
        ));

        // 2. Parent table: drops 50 exp and calls table:rare_gem_table
        DropTableDefinition parentTable = new DropTableDefinition("boss_table", List.of(
                new DropEntry("exp", 1.0, 50, 50, List.of()),
                new DropEntry("table:rare_gem_table", 1.0, 1, 1, List.of())
        ));

        Map<String, DropTableDefinition> registry = Map.of(
                "rare_gem_table", subTable,
                "boss_table", parentTable
        );

        Random random = new Random(42);
        DropRollResult result = parentTable.rollComposite(
                null,
                random,
                bridge,
                condRegistry,
                null,
                id -> Optional.ofNullable(registry.get(id)),
                0
        );

        assertNotNull(result);
        assertEquals(50, result.experience(), "EXP should be rolled as 50");
        assertTrue(result.subTables().contains("rare_gem_table"), "Sub-table list should contain rare_gem_table");
        assertFalse(result.items().isEmpty(), "Sub-table items should be included in result");
        assertEquals("DIAMOND", result.items().getFirst().getType().name());
        assertEquals(2, result.items().getFirst().getAmount());
    }

    @Test
    @DisplayName("TargeterRegistry parses and resolves @Raycast and @RaycastLocation")
    void testRaycastTargetersRegistration() {
        TargeterRegistry registry = new TargeterRegistry();

        assertTrue(registry.hasTargeter("raycast"));
        assertTrue(registry.hasTargeter("ray"));
        assertTrue(registry.hasTargeter("eyeraycast"));
        assertTrue(registry.isEntityTargeter("raycast"));

        assertTrue(registry.hasTargeter("raycast_location"));
        assertTrue(registry.hasTargeter("raycastlocation"));
        assertTrue(registry.hasTargeter("raylocation"));
        assertTrue(registry.isLocationTargeter("raycast_location"));

        TargeterRegistry.ParsedTargeterCall parsed = registry.parse("@Raycast{distance=32;pierce=true;raySize=0.5}");
        assertEquals("raycast", parsed.targeterName());
        assertEquals(32.0, ((Number) parsed.parameters().get("distance")).doubleValue(), 0.01);
        assertEquals(true, parsed.parameters().get("pierce"));
        assertEquals(0.5, ((Number) parsed.parameters().get("raysize")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("MobOptions parses faction and preventFriendlyFire cleanly")
    void testMobFactionOptions() {
        Map<String, Object> map = Map.of(
                "Faction", "UndeadLegion",
                "PreventFriendlyFire", true
        );
        MobOptions options = MobOptions.fromMap(map);
        assertEquals("UndeadLegion", options.faction());
        assertTrue(options.preventFriendlyFire());
    }

    @Test
    @DisplayName("Spawner state capture and restore works reliably")
    void testSpawnerStateCaptureAndRestore() {
        World mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (p, m, a) -> null);
        Location loc = new Location(mockWorld, 100, 64, 100);

        SpawnerDefinition def = SpawnerDefinition.builder("dungeon_spawner", "skeleton_archer", loc)
                .cooldownSeconds(60)
                .warmupSeconds(10)
                .build();

        LunarFixedSpawner spawner = new LunarFixedSpawner(def);
        UUID mob1 = UUID.randomUUID();
        UUID mob2 = UUID.randomUUID();
        spawner.attachTrackedMob(mob1);
        spawner.attachTrackedMob(mob2);
        spawner.setCooldownTicks(400);
        spawner.setWarmupTicks(50);

        LunarFixedSpawner.SpawnerState state = spawner.captureState();
        assertEquals(400, state.cooldownTicks());
        assertEquals(50, state.warmupTicks());
        assertEquals(2, state.trackedMobs().size());

        // Create new spawner and restore
        LunarFixedSpawner newSpawner = new LunarFixedSpawner(def);
        newSpawner.restoreState(state);

        assertEquals(400, newSpawner.currentCooldownTicks());
        assertEquals(50, newSpawner.currentWarmupTicks());
        assertEquals(2, newSpawner.activeMobCount());
        assertTrue(newSpawner.isTracked(mob1));
        assertTrue(newSpawner.isTracked(mob2));

        // Test FixedSpawnerManager bulk state capture/restore
        FixedSpawnerManager manager = new FixedSpawnerManager();
        manager.register(newSpawner);

        Map<String, LunarFixedSpawner.SpawnerState> captured = manager.captureAllStates();
        assertTrue(captured.containsKey("dungeon_spawner"));

        manager.restoreAllStates(captured);
        assertEquals(400, manager.get("dungeon_spawner").orElseThrow().currentCooldownTicks());
    }

    @Test
    @DisplayName("AIGoalType recognizes RESTRICT_SUN and FLEE_FACTION")
    void testAIGoalTypesRecognized() {
        assertEquals(AIGoalType.RESTRICT_SUN, AIGoalType.valueOf("RESTRICT_SUN"));
        assertEquals(AIGoalType.FLEE_FACTION, AIGoalType.valueOf("FLEE_FACTION"));
    }

    @Test
    @DisplayName("MechanicRegistry registers BetterMobs-inspired tactical combat mechanics")
    void testNewMechanicsRegistered() {
        MechanicRegistry registry = new MechanicRegistry();
        assertTrue(registry.get("cleartarget").isPresent(), "cleartarget should be registered");
        assertTrue(registry.get("clear_target").isPresent(), "clear_target should be registered");
        assertTrue(registry.get("modifythreat").isPresent(), "modifythreat should be registered");
        assertTrue(registry.get("blocksound").isPresent(), "blocksound should be registered");
        assertTrue(registry.get("playblocksound").isPresent(), "playblocksound should be registered");
        assertTrue(registry.get("goatram").isPresent(), "goatram should be registered");
        assertTrue(registry.get("ram").isPresent(), "ram should be registered");
        assertTrue(registry.get("disengage").isPresent(), "disengage should be registered");
    }

    @Test
    @DisplayName("ConditionRegistry evaluates blocking, baby, burning, crouching, and faction conditions")
    void testNewConditionsEvaluation() {
        ConditionRegistry registry = new ConditionRegistry();

        assertTrue(registry.get("blocking").isPresent());
        assertTrue(registry.get("isblocking").isPresent());
        assertTrue(registry.get("baby").isPresent());
        assertTrue(registry.get("isbaby").isPresent());
        assertTrue(registry.get("burning").isPresent());
        assertTrue(registry.get("crouching").isPresent());
        assertTrue(registry.get("faction").isPresent());
        assertTrue(registry.get("samefaction").isPresent());
        assertTrue(registry.get("distancefromspawn").isPresent());

        CooldownRegistry cd = new CooldownRegistry();
        UUID dummyId = UUID.randomUUID();
        LivingEntity casterEntity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getUniqueId")) return dummyId;
                    return null;
                }
        );

        // 1. Blocking evaluation with dynamic proxy
        UUID blockerId = UUID.randomUUID();
        org.bukkit.entity.HumanEntity blockingEntity = (org.bukkit.entity.HumanEntity) Proxy.newProxyInstance(
                org.bukkit.entity.HumanEntity.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.HumanEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isBlocking")) return true;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getUniqueId")) return blockerId;
                    return null;
                }
        );
        ConditionContext blockCtx = new ConditionContext(casterEntity, blockingEntity, "default", cd, Map.of(), 0L);
        ConditionResult blockResult = registry.evaluate("blocking", blockCtx, Map.of());
        assertTrue(blockResult.valid() && blockResult.matched(), "Blocking condition should match");

        // 2. Baby evaluation with dynamic proxy
        UUID babyId = UUID.randomUUID();
        Ageable babyEntity = (Ageable) Proxy.newProxyInstance(
                Ageable.class.getClassLoader(),
                new Class<?>[]{Ageable.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isAdult")) return false;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getUniqueId")) return babyId;
                    return null;
                }
        );
        ConditionContext babyCtx = new ConditionContext(casterEntity, babyEntity, "default", cd, Map.of(), 0L);
        ConditionResult babyResult = registry.evaluate("isbaby", babyCtx, Map.of());
        assertTrue(babyResult.valid() && babyResult.matched(), "Baby condition should match");

        // 3. Crouching evaluation with dynamic proxy
        UUID playerId = UUID.randomUUID();
        Player crouchingPlayer = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isSneaking")) return true;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getUniqueId")) return playerId;
                    return null;
                }
        );
        ConditionContext crouchCtx = new ConditionContext(casterEntity, crouchingPlayer, "default", cd, Map.of(), 0L);
        ConditionResult crouchResult = registry.evaluate("crouching", crouchCtx, Map.of());
        assertTrue(crouchResult.valid() && crouchResult.matched(), "Crouching condition should match");

        // 4. Burning evaluation
        UUID burnId = UUID.randomUUID();
        LivingEntity burningEntity = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getFireTicks")) return 100;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getUniqueId")) return burnId;
                    return null;
                }
        );
        ConditionContext burnCtx = new ConditionContext(casterEntity, burningEntity, "default", cd, Map.of(), 0L);
        ConditionResult burnResult = registry.evaluate("burning", burnCtx, Map.of());
        assertTrue(burnResult.valid() && burnResult.matched(), "Burning condition should match");
    }

    @Test
    @DisplayName("ConditionRegistry evaluates faction and samefaction conditions")
    void testFactionConditions() {
        ConditionRegistry registry = new ConditionRegistry();
        LunarMobManager mockMobManager = new LunarMobManager();
        registry.setMobManager(mockMobManager);

        World mockWorld = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> null
        );

        UUID mobAId = UUID.randomUUID();
        UUID mobBId = UUID.randomUUID();
        UUID mobCId = UUID.randomUUID();

        LivingEntity entityA = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return mobAId;
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 0, 0, 0);
                    return null;
                }
        );
        LivingEntity entityB = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return mobBId;
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 0, 0, 0);
                    return null;
                }
        );
        LivingEntity entityC = (LivingEntity) Proxy.newProxyInstance(
                LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return mobCId;
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 0, 0, 0);
                    return null;
                }
        );

        Map<String, MobOptionDefinition> optsUndead = Map.of("Faction", new MobOptionDefinition("Faction", "UndeadLegion"));
        Map<String, MobOptionDefinition> optsHuman = Map.of("Faction", new MobOptionDefinition("Faction", "HumanKingdom"));

        MobDefinition defUndeadA = new MobDefinition(new MobDefinitionId("undead_soldier_a"), EntityType.ZOMBIE, "Undead A", null, Map.of(), optsUndead, List.of(), null, Set.of());
        MobDefinition defUndeadB = new MobDefinition(new MobDefinitionId("undead_soldier_b"), EntityType.ZOMBIE, "Undead B", null, Map.of(), optsUndead, List.of(), null, Set.of());
        MobDefinition defHuman = new MobDefinition(new MobDefinitionId("human_guard"), EntityType.ZOMBIE, "Human Guard", null, Map.of(), optsHuman, List.of(), null, Set.of());

        ActiveMob mobA = new ActiveMob(entityA, defUndeadA, new LunarMobIdentity("undead_soldier_a", "1.0", Optional.empty()));
        ActiveMob mobB = new ActiveMob(entityB, defUndeadB, new LunarMobIdentity("undead_soldier_b", "1.0", Optional.empty()));
        ActiveMob mobC = new ActiveMob(entityC, defHuman, new LunarMobIdentity("human_guard", "1.0", Optional.empty()));

        mockMobManager.register(mobA);
        mockMobManager.register(mobB);
        mockMobManager.register(mobC);

        CooldownRegistry cd = new CooldownRegistry();

        // 1. Same faction: mobA and mobB
        ConditionContext ctxSame = new ConditionContext(entityA, entityB, "default", cd, Map.of(), 0L);
        assertTrue(registry.evaluate("samefaction", ctxSame, Map.of()).matched());
        assertTrue(registry.evaluate("faction", ctxSame, Map.of("faction", "UndeadLegion")).matched());
        assertFalse(registry.evaluate("faction", ctxSame, Map.of("faction", "HumanKingdom")).matched());

        // 2. Different faction: mobA and mobC
        ConditionContext ctxDiff = new ConditionContext(entityA, entityC, "default", cd, Map.of(), 0L);
        assertFalse(registry.evaluate("samefaction", ctxDiff, Map.of()).matched());
        assertTrue(registry.evaluate("faction", ctxDiff, Map.of("faction", "HumanKingdom")).matched());
    }

    private static class TestItemStack extends ItemStack {
        private final Material type;
        private int amount;

        public TestItemStack(Material type, int amount) {
            super();
            this.type = type;
            this.amount = amount;
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }
    }
}
