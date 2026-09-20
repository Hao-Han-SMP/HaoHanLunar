package vn.haohan.lunar.api.presentation.display;

import vn.haohan.lunar.api.presentation.display.*;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.presentation.audio.SpatialAudioEngine;
import vn.haohan.lunar.api.presentation.display.bossbar.LunarBossBar;
import vn.haohan.lunar.api.presentation.display.bossbar.LunarBossBarTracker;
import vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge;
import vn.haohan.lunar.api.presentation.display.nameplate.LunarNameplate;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicContext;
import vn.haohan.lunar.api.system.combat.skill.mechanic.MechanicRegistry;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobIdentity;
import vn.haohan.lunar.core.mob.LunarMobManager;
import vn.haohan.lunar.api.mob.MobDefinition;
import vn.haohan.lunar.api.mob.MobDefinitionId;
import vn.haohan.lunar.api.system.combat.skill.SkillCastContext;
import vn.haohan.lunar.api.system.combat.skill.SkillDefinition;
import vn.haohan.lunar.api.system.combat.skill.SkillTrigger;
import vn.haohan.lunar.api.system.combat.skill.target.TargetRef;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class Phase8BossPresentationTest {

    @Test
    void testMultipleIndependentBossBars() {
        LunarBossBarTracker tracker = new LunarBossBarTracker();

        // 1. Create health bar and rage bar
        LunarBossBar healthBar = tracker.create("health", Component.text("Boss HP"), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS, 32.0);
        LunarBossBar rageBar = tracker.create("rage", Component.text("Rage"), 0.25f,
                BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_6, 32.0);

        assertEquals(2, tracker.allBars().size());
        assertTrue(tracker.get("health").isPresent());
        assertTrue(tracker.get("rage").isPresent());

        // 2. Modify values
        rageBar.setProgress(0.85f);
        assertEquals(0.85f, tracker.get("rage").get().progress(), 0.001f);

        // 3. Distance-based player viewer tracking
        World mockWorld = mockWorld("lunar");
        Location mobLoc = new Location(mockWorld, 0, 64, 0);

        Player nearPlayer = mockPlayer("NearPlayer", new Location(mockWorld, 10, 64, 0));
        Player farPlayer = mockPlayer("FarPlayer", new Location(mockWorld, 100, 64, 0));

        tracker.tick(mobLoc, List.of(nearPlayer, farPlayer));

        assertTrue(healthBar.currentViewers().contains(nearPlayer));
        assertFalse(healthBar.currentViewers().contains(farPlayer));

        // 4. Remove one bar
        tracker.remove("health");
        assertEquals(1, tracker.allBars().size());
        assertFalse(tracker.get("health").isPresent());

        // 5. Remove all
        tracker.removeAll();
        assertEquals(0, tracker.allBars().size());
    }

    @Test
    void testMobManagerCleansUpBossBars() {
        LunarMobManager manager = new LunarMobManager(e -> {});
        World mockWorld = mockWorld("lunar");
        Location mobLoc = new Location(mockWorld, 0, 64, 0);
        ActiveLunarMob mob = createMockMob("lunar_boss", mobLoc);

        mob.bossBars().create("boss_hp", Component.text("Boss HP"), 1.0f,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS, 32.0);
        manager.register(mob);

        Player player = mockPlayer("P1", new Location(mockWorld, 5, 64, 0));
        mob.bossBars().tick(mobLoc, List.of(player));
        assertEquals(1, mob.bossBars().get("boss_hp").get().currentViewers().size());

        // Unregister mob -> BossBars are cleaned up
        manager.unregister(mob.entityId());
        assertEquals(0, mob.bossBars().allBars().size());
    }

    @Test
    void testLunarNameplateFormatting() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0, 64, 0);
        ActiveLunarMob mob = createMockMob("solar_beast", loc);
        mob.setStance("enraged");

        String template = "<yellow><name></yellow> <gray>[<green><health>/<max_health></green>]</gray> (<health_percent>)";
        Component rendered = LunarNameplate.render(template, mob);
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);

        assertTrue(plain.contains("solar_beast"));
        assertTrue(plain.contains("100/100"));
        assertTrue(plain.contains("100%"));
    }

    @Test
    void testHaoHanDisplayUIBridgeFallbackAndTypewriter() {
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0, 64, 0);
        ActiveLunarMob mob = createMockMob("dialogue_boss", loc);

        HaoHanDisplayUIBridge.clearAll();
        assertFalse(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));

        HaoHanDisplayUIBridge.BubbleOptions options = new HaoHanDisplayUIBridge.BubbleOptions(
                "<gold>You shall not pass!", 3, 0.5, true, "lunar", 24.0);

        HaoHanDisplayUIBridge.ActiveBubbleSession session = HaoHanDisplayUIBridge.displayBubble(mob, options);
        assertNotNull(session);
        assertTrue(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));

        // Ticking session
        assertFalse(session.tick()); // tick 1
        assertFalse(session.tick()); // tick 2
        assertTrue(session.tick());  // tick 3 -> expired

        HaoHanDisplayUIBridge.clearBubble(mob.entityId());
        assertFalse(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));
    }

    @Test
    void testHaoHanDisplayUIProviderDelegation() {
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        HaoHanDisplayUIBridge.DisplayUIProvider mockProvider = new HaoHanDisplayUIBridge.DisplayUIProvider() {
            @Override
            public boolean showSpeechBubble(vn.haohan.lunar.core.subsystem.mob.ActiveMob mob, HaoHanDisplayUIBridge.BubbleOptions options) {
                providerCalled.set(true);
                return true;
            }

            @Override
            public void cancelSpeechBubble(UUID mobId) {}
        };

        HaoHanDisplayUIBridge.setCustomProvider(mockProvider);
        try {
            assertTrue(HaoHanDisplayUIBridge.isHaoHanDisplayUIAvailable());
            World mockWorld = mockWorld("lunar");
            ActiveLunarMob mob = createMockMob("custom_provider_mob", new Location(mockWorld, 0, 64, 0));

            HaoHanDisplayUIBridge.displayBubble(mob, new HaoHanDisplayUIBridge.BubbleOptions(
                    "Hello world", 20, 0.5, false, "default", 20.0));

            assertTrue(providerCalled.get());
        } finally {
            HaoHanDisplayUIBridge.setCustomProvider(null);
        }
    }

    @Test
    void testSpatialAudioEngineFalloffAndSequencing() {
        SpatialAudioEngine engine = new SpatialAudioEngine();

        // 1. Falloff curve tests
        float maxVol = 2.0f;
        double radius = 30.0;

        // At distance 0, volume is max
        assertEquals(2.0f, SpatialAudioEngine.calculateVolume(maxVol, 0.0, radius, SpatialAudioEngine.FalloffModel.LINEAR), 0.001f);
        // At half radius LINEAR -> half volume
        assertEquals(1.0f, SpatialAudioEngine.calculateVolume(maxVol, 15.0, radius, SpatialAudioEngine.FalloffModel.LINEAR), 0.001f);
        // At max radius -> 0.0f
        assertEquals(0.0f, SpatialAudioEngine.calculateVolume(maxVol, 30.0, radius, SpatialAudioEngine.FalloffModel.LINEAR), 0.001f);

        // Exponential volume decreases faster
        float expVol = SpatialAudioEngine.calculateVolume(maxVol, 15.0, radius, SpatialAudioEngine.FalloffModel.EXPONENTIAL);
        assertTrue(expVol < 1.0f && expVol > 0.0f);

        // 2. Sound sequence scheduling
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0, 64, 0);
        UUID mobId = UUID.randomUUID();

        List<SpatialAudioEngine.SoundStep> steps = List.of(
                new SpatialAudioEngine.SoundStep("haohan:roar", 0, 1.0f, 1.0f),
                new SpatialAudioEngine.SoundStep("haohan:charge", 2, 1.5f, 1.2f)
        );

        SpatialAudioEngine.ActiveSoundSequence seq = engine.playSequence(mobId, loc, steps, 32.0, SpatialAudioEngine.FalloffModel.EXPONENTIAL);
        assertEquals(1, engine.activeSequenceCount());

        Player player = mockPlayer("AudioAudience", new Location(mockWorld, 5, 64, 0));

        // Tick 0 -> roar plays
        engine.tickAll(List.of(player));
        assertEquals(1, engine.activeSequenceCount());

        // Stop sound early
        engine.stopSound(mobId);
        assertTrue(seq.isCancelled());
        engine.tickAll(List.of(player));
        assertEquals(0, engine.activeSequenceCount());
    }

    @Test
    void testBossPresentationMechanics() {
        MechanicRegistry registry = new MechanicRegistry();
        World mockWorld = mockWorld("lunar");
        Location loc = new Location(mockWorld, 0, 64, 0);
        ActiveLunarMob mob = createMockMob("mechanic_boss", loc);

        SkillDefinition skill = new SkillDefinition("test_skill", Set.of(SkillTrigger.ON_TIMER), 0);
        SkillCastContext cast = new SkillCastContext(mob, skill, SkillTrigger.ON_TIMER, 100L);
        MechanicContext ctx = new MechanicContext(cast, List.of(TargetRef.entity(mob.entity())));

        // 1. barcreate
        registry.get("barcreate").get().execute(ctx, Map.of(
                "bar", "shield",
                "title", "<aqua>Shield</aqua>",
                "value", 0.5,
                "color", "BLUE"
        ));
        assertTrue(mob.bossBars().get("shield").isPresent());
        assertEquals(0.5f, mob.bossBars().get("shield").get().progress(), 0.001f);

        // 2. barset
        registry.get("barset").get().execute(ctx, Map.of(
                "bar", "shield",
                "value", 0.9
        ));
        assertEquals(0.9f, mob.bossBars().get("shield").get().progress(), 0.001f);

        // 3. barremove
        registry.get("barremove").get().execute(ctx, Map.of("bar", "shield"));
        assertFalse(mob.bossBars().get("shield").isPresent());

        // 4. speechbubble
        registry.get("speechbubble").get().execute(ctx, Map.of(
                "text", "Taste my blade!",
                "duration", 40
        ));
        assertTrue(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));
        HaoHanDisplayUIBridge.clearBubble(mob.entityId());

        // 5. soundsequence and stopsound
        registry.get("soundsequence").get().execute(ctx, Map.of(
                "sounds", List.of(Map.of("s", "minecraft:entity.warden.roar", "d", 0, "v", 1.0, "p", 1.0)),
                "radius", 20.0
        ));
        assertEquals(1, registry.audioEngine().activeSequenceCount());

        registry.get("stopsound").get().execute(ctx, Map.of());
        assertEquals(0, registry.audioEngine().activeSequenceCount());
    }

    // --- Helpers ---

    private static World mockWorld(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return name.hashCode();
                    return null;
                });
    }

    private static LivingEntity mockLiving(Location loc) {
        UUID uuid = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getHealth")) return 100.0;
                    if (method.getName().equals("getMaxHealth")) return 100.0;
                    if (method.getName().equals("getHeight")) return 2.0;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static Player mockPlayer(String name, Location loc) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("getName")) return name;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return loc.getWorld();
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("showBossBar")) return null;
                    if (method.getName().equals("hideBossBar")) return null;
                    if (method.getName().equals("playSound")) return null;
                    if (method.getName().equals("sendMessage")) return null;
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return uuid.hashCode();
                    return null;
                });
    }

    private static ActiveLunarMob createMockMob(String mobId, Location loc) {
        LivingEntity entity = mockLiving(loc);
        MobDefinition def = new MobDefinition(new MobDefinitionId(mobId), EntityType.ZOMBIE, mobId, null,
                Map.of(), Map.of(), List.of(), null, Set.of());
        return new ActiveLunarMob(entity, def, new LunarMobIdentity(mobId, "1.0.0"));
    }
}
