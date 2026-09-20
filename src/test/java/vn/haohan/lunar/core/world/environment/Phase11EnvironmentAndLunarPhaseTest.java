package vn.haohan.lunar.core.world.environment;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.core.skill.condition.ConditionContext;
import vn.haohan.lunar.core.skill.condition.ConditionRegistry;
import vn.haohan.lunar.core.skill.condition.ConditionResult;
import vn.haohan.lunar.core.skill.mechanic.MechanicContext;
import vn.haohan.lunar.core.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.mob.*;
import vn.haohan.lunar.core.mob.equipment.MobEquipmentDefinition;
import vn.haohan.lunar.core.skill.CooldownRegistry;
import vn.haohan.lunar.core.skill.SkillCastContext;
import vn.haohan.lunar.core.skill.SkillDefinition;
import vn.haohan.lunar.core.skill.SkillTrigger;
import vn.haohan.lunar.core.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class Phase11EnvironmentAndLunarPhaseTest {

    private ConditionRegistry conditionRegistry;
    private MechanicRegistry mechanicRegistry;
    private EnvironmentalFieldTracker fieldTracker;
    private LunarEnvironmentTracker environmentTracker;
    private LunarMobManager mobManager;

    @BeforeEach
    void setUp() {
        conditionRegistry = new ConditionRegistry();
        fieldTracker = new EnvironmentalFieldTracker();
        mechanicRegistry = new MechanicRegistry();
        mechanicRegistry.setFieldTracker(fieldTracker);
        environmentTracker = new LunarEnvironmentTracker();
        mobManager = new LunarMobManager(e -> {});
    }

    @Test
    void testVanillaEightLunarPhasesCalculationAndConditions() {
        // Vanilla Minecraft 8 phases cycle every 24000 ticks
        assertEquals(LunarPhase.FULL_MOON, LunarPhase.fromFullTime(0L));
        assertEquals(LunarPhase.WANING_GIBBOUS, LunarPhase.fromFullTime(24000L));
        assertEquals(LunarPhase.LAST_QUARTER, LunarPhase.fromFullTime(48000L));
        assertEquals(LunarPhase.WANING_CRESCENT, LunarPhase.fromFullTime(72000L));
        assertEquals(LunarPhase.NEW_MOON, LunarPhase.fromFullTime(96000L));
        assertEquals(LunarPhase.WAXING_CRESCENT, LunarPhase.fromFullTime(120000L));
        assertEquals(LunarPhase.FIRST_QUARTER, LunarPhase.fromFullTime(144000L));
        assertEquals(LunarPhase.WAXING_GIBBOUS, LunarPhase.fromFullTime(168000L));
        assertEquals(LunarPhase.FULL_MOON, LunarPhase.fromFullTime(192000L));

        // Test ConditionRegistry with full moon (time = 0)
        World mockWorld = createMockWorld("lunar_world", 0L, 0L, 64, 15);
        ActiveLunarMob mob = createMockMob("lunar_werewolf", mockWorld, new Location(mockWorld, 0, 64, 0));

        ConditionContext fullMoonCtx = new ConditionContext(
                mob.entity(), null, "default", new CooldownRegistry(), Map.of(), 0L);

        ConditionResult matchFull = conditionRegistry.evaluate("lunarphase", fullMoonCtx, Map.of("phase", "FULL_MOON"));
        assertTrue(matchFull.valid() && matchFull.matched(), "Should match FULL_MOON at fullTime 0");

        ConditionResult noMatchNew = conditionRegistry.evaluate("lunarphase", fullMoonCtx, Map.of("phase", "NEW_MOON"));
        assertTrue(noMatchNew.valid() && !noMatchNew.matched(), "Should NOT match NEW_MOON at fullTime 0");

        // Test alias THIRD_QUARTER / LAST_QUARTER at day 2 (fullTime 48000)
        World day2World = createMockWorld("lunar_world", 48000L, 0L, 64, 15);
        ActiveLunarMob day2Mob = createMockMob("lunar_werewolf", day2World, new Location(day2World, 0, 64, 0));
        ConditionContext day2Ctx = new ConditionContext(
                day2Mob.entity(), null, "default", new CooldownRegistry(), Map.of(), 0L);

        ConditionResult matchLastQuarter = conditionRegistry.evaluate("lunarphase", day2Ctx, Map.of("phase", "LAST_QUARTER"));
        assertTrue(matchLastQuarter.valid() && matchLastQuarter.matched(), "LAST_QUARTER should match day 2");

        ConditionResult matchThirdQuarter = conditionRegistry.evaluate("lunarphase", day2Ctx, Map.of("phase", "THIRD_QUARTER"));
        assertTrue(matchThirdQuarter.valid() && matchThirdQuarter.matched(), "THIRD_QUARTER should match day 2");
    }

    @Test
    void testMoonlightAndCelestialConditions() {
        World skyWorld = createMockWorld("lunar_world", 18000L, 18000L, 64, 14);

        // 1. skylight condition
        ActiveLunarMob brightMob = createMockMob("bright_mob", skyWorld, new Location(skyWorld, 0, 64, 0));
        ConditionContext brightCtx = new ConditionContext(brightMob.entity(), null, "default", new CooldownRegistry(), Map.of(), 0L);

        ConditionResult skylightGte12 = conditionRegistry.evaluate("skylight", brightCtx, Map.of("level", 12));
        assertTrue(skylightGte12.valid() && skylightGte12.matched(), "Sky light 14 is >= 12");

        ConditionResult skylightGte15 = conditionRegistry.evaluate("skylight", brightCtx, Map.of("level", 15));
        assertTrue(skylightGte15.valid() && !skylightGte15.matched(), "Sky light 14 is NOT >= 15");

        // 2. underopensky condition (highest block is 64, mob at 64 -> open; mob at 50 -> cave/blocked)
        ConditionResult openSky = conditionRegistry.evaluate("underopensky", brightCtx, Map.of("bool", true));
        assertTrue(openSky.valid() && openSky.matched(), "Mob at y=64 with highestY=64 is under open sky");

        ActiveLunarMob caveMob = createMockMob("cave_mob", skyWorld, new Location(skyWorld, 0, 40, 0));
        ConditionContext caveCtx = new ConditionContext(caveMob.entity(), null, "default", new CooldownRegistry(), Map.of(), 0L);
        ConditionResult blockedSky = conditionRegistry.evaluate("underopensky", caveCtx, Map.of("bool", true));
        assertTrue(blockedSky.valid() && !blockedSky.matched(), "Mob at y=40 under highestY=64 is NOT under open sky");

        // 3. nightonly condition (time=18000 is night, between 13000 and 23000)
        ConditionResult nightTrue = conditionRegistry.evaluate("nightonly", brightCtx, Map.of("bool", true));
        assertTrue(nightTrue.valid() && nightTrue.matched(), "Time 18000 is night");

        World dayWorld = createMockWorld("lunar_world", 6000L, 6000L, 64, 15);
        ActiveLunarMob noonMob = createMockMob("noon_mob", dayWorld, new Location(dayWorld, 0, 64, 0));
        ConditionContext noonCtx = new ConditionContext(noonMob.entity(), null, "default", new CooldownRegistry(), Map.of(), 0L);
        ConditionResult dayNightCheck = conditionRegistry.evaluate("nightonly", noonCtx, Map.of("bool", true));
        assertTrue(dayNightCheck.valid() && !dayNightCheck.matched(), "Time 6000 is NOT night");
    }

    @Test
    void testLunarEnvironmentTrackerTransitions() {
        AtomicLong worldFullTime = new AtomicLong(0L);
        AtomicLong worldDayTime = new AtomicLong(12000L);

        World dynamicWorld = createMockWorld("lunar_world", worldFullTime, worldDayTime, 64, 15);
        ActiveLunarMob mob = createMockMob("lunar_boss", dynamicWorld, new Location(dynamicWorld, 0, 64, 0));
        mobManager.register(mob);

        List<SkillTrigger> triggered = new ArrayList<>();

        // 1. Initial tick to prime the baseline
        environmentTracker.tickWorld(dynamicWorld, mobManager, (m, trg) -> triggered.add(trg));
        assertTrue(triggered.isEmpty(), "First tick should set baseline without false triggers");

        // 2. Advance time past moonrise (13,000 ticks)
        worldDayTime.set(13050L);
        worldFullTime.set(13050L);
        environmentTracker.tickWorld(dynamicWorld, mobManager, (m, trg) -> triggered.add(trg));
        assertTrue(triggered.contains(SkillTrigger.ON_MOONRISE), "Should fire ON_MOONRISE when time reaches 13000+");

        // 3. Advance time past moonset (23,000 ticks)
        triggered.clear();
        worldDayTime.set(23100L);
        worldFullTime.set(23100L);
        environmentTracker.tickWorld(dynamicWorld, mobManager, (m, trg) -> triggered.add(trg));
        assertTrue(triggered.contains(SkillTrigger.ON_MOONSET), "Should fire ON_MOONSET when time reaches 23000+");

        // 4. Advance full time across in-game days to trigger lunar phase change
        triggered.clear();
        worldDayTime.set(1000L);
        worldFullTime.set(25000L); // Day 1 -> WANING_GIBBOUS
        environmentTracker.tickWorld(dynamicWorld, mobManager, (m, trg) -> triggered.add(trg));
        assertTrue(triggered.contains(SkillTrigger.ON_LUNAR_PHASE_CHANGE), "Should fire ON_LUNAR_PHASE_CHANGE when moon phase shifts");
    }

    @Test
    void testWeatherManipulationMechanic() {
        AtomicBoolean storming = new AtomicBoolean(false);
        AtomicBoolean thundering = new AtomicBoolean(false);
        AtomicInteger weatherDuration = new AtomicInteger(0);

        World mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "lunar_world";
                    if (method.getName().equals("setStorm")) {
                        storming.set((Boolean) args[0]);
                        return null;
                    }
                    if (method.getName().equals("setThundering")) {
                        thundering.set((Boolean) args[0]);
                        return null;
                    }
                    if (method.getName().equals("setWeatherDuration") || method.getName().equals("setThunderDuration")) {
                        weatherDuration.set((Integer) args[0]);
                        return null;
                    }
                    if (method.getName().equals("setClearWeatherDuration")) {
                        weatherDuration.set((Integer) args[0]);
                        return null;
                    }
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    return null;
                });

        ActiveLunarMob mob = createMockMob("weather_boss", mockWorld, new Location(mockWorld, 0, 64, 0));
        SkillDefinition skill = new SkillDefinition("stormSkill", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext cast = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(mob.entity())));

        // Summon Thunder storm
        mechanicRegistry.execute("setweather", ctx, Map.of("type", "THUNDER", "duration", 1200));
        assertTrue(storming.get());
        assertTrue(thundering.get());
        assertEquals(1200, weatherDuration.get());

        // Clear weather
        mechanicRegistry.execute("setweather", ctx, Map.of("type", "CLEAR", "duration", 6000));
        assertFalse(storming.get());
        assertFalse(thundering.get());
        assertEquals(6000, weatherDuration.get());
    }

    @Test
    void testGravityZoneCreationTickAndSafeCleanup() {
        World world = createMockWorld("lunar_world", 0L, 0L, 64, 15);
        ActiveLunarMob mob = createMockMob("gravity_master", world, new Location(world, 0, 64, 0));

        SkillDefinition skill = new SkillDefinition("gravSkill", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext cast = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.location(new Location(world, 10, 64, 10))));

        // Create gravity zone radius 15, multiplier 0.2, duration 50 ticks
        mechanicRegistry.execute("gravityzone", ctx, Map.of("radius", 15.0, "multiplier", 0.2, "duration", 50));
        assertEquals(1, fieldTracker.getActiveGravityZones().size());

        EnvironmentalFieldTracker.GravityZone zone = fieldTracker.getActiveGravityZones().get(0);
        assertTrue(zone.contains(new Location(world, 12, 64, 12)));
        assertFalse(zone.contains(new Location(world, 50, 64, 50)));

        // Create mock player inside zone
        Player insidePlayer = createMockPlayer(new Location(world, 11, 64, 11), 300);

        // Tick at tick 120 (active)
        fieldTracker.tick(120L, List.of(insidePlayer), null);
        assertEquals(1, fieldTracker.getActiveGravityZones().size());

        // Tick at tick 160 (expired, spawn was 100 + duration 50 = 150)
        fieldTracker.tick(160L, List.of(insidePlayer), null);
        assertTrue(fieldTracker.getActiveGravityZones().isEmpty(), "Gravity zone must expire and be cleaned up safely");
    }

    @Test
    void testOxygenFieldDrainAndRestore() {
        World world = createMockWorld("lunar_world", 0L, 0L, 64, 15);
        ActiveLunarMob mob = createMockMob("suffocator", world, new Location(world, 0, 64, 0));

        SkillDefinition skill = new SkillDefinition("o2Skill", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext cast = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.location(new Location(world, 0, 64, 0))));

        Player player = createMockPlayer(new Location(world, 2, 64, 2), 300);

        // 1. Oxygen Drain field
        mechanicRegistry.execute("oxygenfield", ctx, Map.of("radius", 10.0, "mode", "DRAIN", "amount", 5, "duration", 40));
        assertEquals(1, fieldTracker.getActiveOxygenFields().size());

        fieldTracker.tick(101L, List.of(player), null);
        // Initial air was 300, drained by 5 * 15 = 75 -> 225
        assertEquals(225, player.getRemainingAir());

        // 2. Oxygen Restore field
        fieldTracker.clear();
        mechanicRegistry.execute("oxygenfield", ctx, Map.of("radius", 10.0, "mode", "RESTORE", "amount", 4, "duration", 40));
        fieldTracker.tick(102L, List.of(player), null);
        // 225 + 4 * 15 = 285
        assertEquals(285, player.getRemainingAir());
    }

    // --- Helpers and Dynamic Mocks ---

    private ActiveLunarMob createMockMob(String id, World world, Location loc) {
        MobDefinition def = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.ZOMBIE,
                id,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of(),
                MobEquipmentDefinition.empty(),
                null,
                List.of()
        );
        UUID uuid = UUID.randomUUID();
        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc;
                    if (method.getName().equals("getWorld")) return world;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
        return new ActiveLunarMob(entity, def, new LunarMobIdentity(id, "1"));
    }

    private Player createMockPlayer(Location loc, int initialAir) {
        UUID uuid = UUID.randomUUID();
        AtomicInteger airRef = new AtomicInteger(initialAir);
        AtomicReference<Vector> velRef = new AtomicReference<>(new Vector(0, -0.5, 0));

        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc;
                    if (method.getName().equals("isOnline")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getRemainingAir")) return airRef.get();
                    if (method.getName().equals("setRemainingAir")) {
                        airRef.set((Integer) args[0]);
                        return null;
                    }
                    if (method.getName().equals("getMaximumAir")) return 300;
                    if (method.getName().equals("getVelocity")) return velRef.get();
                    if (method.getName().equals("setVelocity")) {
                        velRef.set((Vector) args[0]);
                        return null;
                    }
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private World createMockWorld(String name, long fullTime, long dayTime, int highestY, int skyLight) {
        return createMockWorld(name, new AtomicLong(fullTime), new AtomicLong(dayTime), highestY, skyLight);
    }

    private World createMockWorld(String name, AtomicLong fullTimeRef, AtomicLong dayTimeRef, int highestY, int skyLight) {
        Block mockBlock = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
                new Class<?>[]{Block.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getLightFromSky")) return (byte) skyLight;
                    return null;
                });

        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getFullTime")) return fullTimeRef.get();
                    if (method.getName().equals("getTime")) return dayTimeRef.get();
                    if (method.getName().equals("getHighestBlockYAt")) return highestY;
                    if (method.getName().equals("getBlockAt")) return mockBlock;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }
}
