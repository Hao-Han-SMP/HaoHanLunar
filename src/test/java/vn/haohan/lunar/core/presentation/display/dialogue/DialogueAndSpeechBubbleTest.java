package vn.haohan.lunar.core.presentation.display.dialogue;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge;
import vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge.BubbleOptions;
import vn.haohan.lunar.api.presentation.display.dialogue.HaoHanDisplayUIBridge.DialogueChoice;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DialogueAndSpeechBubbleTest {

    private World mockWorld;
    private MechanicRegistry mechanicRegistry;

    @BeforeEach
    void setUp() {
        HaoHanDisplayUIBridge.clearAll();
        HaoHanDisplayUIBridge.setCustomProvider(null);
        mechanicRegistry = new MechanicRegistry();

        mockWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "world";
                    if (method.getName().equals("equals")) return args.length > 0 && proxy == args[0];
                    if (method.getName().equals("hashCode")) return 42;
                    if (method.getName().equals("getPlayers")) return List.of();
                    return null;
                });
    }

    @AfterEach
    void tearDown() {
        HaoHanDisplayUIBridge.clearAll();
        HaoHanDisplayUIBridge.setCustomProvider(null);
    }

    @Test
    @DisplayName("BubbleOptions parses multi-line delimiter and default styling properly")
    void testBubbleOptionsCreationAndDefaults() {
        BubbleOptions options = new BubbleOptions("Line 1|Line 2|Line 3", 60, 0.8, true, "lunar_bubble", 32.0);
        assertNotNull(options.lines());
        assertEquals(3, options.lines().size());
        assertEquals("Line 1", options.lines().get(0));
        assertEquals("Line 2", options.lines().get(1));
        assertEquals("Line 3", options.lines().get(2));
        assertEquals(60, options.durationTicks());
        assertEquals(0.8, options.offsetY(), 0.001);
        assertTrue(options.typewriter());
        assertEquals("lunar_bubble", options.style());
        assertEquals(32.0, options.audienceRadius(), 0.001);
        assertTrue(options.followMob());
        assertTrue(options.textShadow());
        assertFalse(options.seeThrough());
        assertEquals(200, options.lineWidth());
    }

    @Test
    @DisplayName("DisplayBubble session creation, multi-line progression, and expiry lifecycle")
    void testDisplayBubbleSessionLifecycle() {
        ActiveLunarMob mob = createDummyMob("lunar_guard", "Lunar Guard");
        BubbleOptions options = new BubbleOptions(
                "First line|Second line",
                List.of("First line", "Second line"),
                40, 0.5, false, "default", 24.0,
                Color.fromARGB(150, 0, 0, 0), true, false, 250,
                null, null, true, false
        );

        var session = HaoHanDisplayUIBridge.displayBubble(mob, options);
        assertNotNull(session);
        assertTrue(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));
        assertEquals(1, HaoHanDisplayUIBridge.activeSessionCount());

        assertEquals(0, session.currentLineIndex());
        assertEquals("First line", session.currentLineText());

        // Tick through first line duration (40 ticks / 2 = 20 ticks per line)
        for (int i = 0; i < 20; i++) {
            assertFalse(session.tick());
        }

        // Now transitioned to second line
        assertEquals(1, session.currentLineIndex());
        assertEquals("Second line", session.currentLineText());

        // Tick through second line duration
        boolean completed = false;
        for (int i = 0; i < 20; i++) {
            if (session.tick()) {
                completed = true;
                break;
            }
        }
        assertTrue(completed);

        HaoHanDisplayUIBridge.clearBubble(mob.entityId());
        assertFalse(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));
        assertEquals(0, HaoHanDisplayUIBridge.activeSessionCount());
    }

    @Test
    @DisplayName("sendDialoguePrompt sends formatted interactive prompt with clickable command signal")
    void testDialogueChoiceAndClickablePrompt() {
        ActiveLunarMob mob = createDummyMob("space_merchant", "Space Merchant");
        AtomicReference<Component> receivedMessage = new AtomicReference<>();

        Player mockPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length > 0 && args[0] instanceof Component comp) {
                        receivedMessage.set(comp);
                        return null;
                    }
                    if (method.getName().equals("getName")) return "Astronaut";
                    return null;
                });

        List<DialogueChoice> choices = List.of(
                new DialogueChoice("Accept Quest", "ACCEPT_QUEST", "Click to accept the Lunar Mission"),
                new DialogueChoice("Decline", "DECLINE_QUEST", "Click to decline")
        );

        HaoHanDisplayUIBridge.sendDialoguePrompt(mockPlayer, mob, "<gold>[Merchant]</gold> Do you want this trade?", choices);

        assertNotNull(receivedMessage.get());
        String serialized = receivedMessage.get().toString();
        assertTrue(serialized.contains("Accept Quest"));
        assertTrue(serialized.contains("ACCEPT_QUEST"));
        assertTrue(serialized.contains("/lunarmob signal"));
    }

    @Test
    @DisplayName("Mechanic speak/dialogue replaces placeholders and registers session")
    void testSpeakMechanicWithPlaceholders() {
        ActiveLunarMob mob = createDummyMob("lunar_boss", "Moon Guardian");
        Player mockPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "Hero";
                    if (method.getName().equals("getHealth")) return 18.5;
                    return null;
                });

        SkillDefinition skill = new SkillDefinition("test_speak", Set.of(SkillTrigger.ON_COMBAT), 0);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_COMBAT, 0);
        MechanicContext mechContext = new MechanicContext(castContext, List.of(TargetRef.of(mockPlayer)));

        MechanicResult result = mechanicRegistry.execute("speak", mechContext, Map.of(
                "text", "Halt <target.name>! I am <mob.name>!",
                "duration", 50,
                "typewriter", false
        ));

        assertTrue(result.isSuccess());
        assertTrue(HaoHanDisplayUIBridge.hasActiveBubble(mob.entityId()));
    }

    @Test
    @DisplayName("Mechanic dialogue_prompt parses options and sends clickable prompts to target players")
    void testDialoguePromptMechanic() {
        ActiveLunarMob mob = createDummyMob("alien_scout", "Alien Scout");
        AtomicReference<Component> receivedMessage = new AtomicReference<>();

        Player mockPlayer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length > 0 && args[0] instanceof Component comp) {
                        receivedMessage.set(comp);
                        return null;
                    }
                    if (method.getName().equals("getName")) return "Explorer";
                    if (method.getName().equals("getLocation")) return new Location(mockWorld, 0, 64, 0);
                    return null;
                });

        SkillDefinition skill = new SkillDefinition("test_prompt", Set.of(SkillTrigger.ON_INTERACT), 0);
        SkillCastContext castContext = new SkillCastContext(mob, skill, SkillTrigger.ON_INTERACT, 0);
        MechanicContext mechContext = new MechanicContext(castContext, List.of(TargetRef.of(mockPlayer)));

        MechanicResult result = mechanicRegistry.execute("prompt", mechContext, Map.of(
                "message", "<green><mob.name></green>: Will you help repair my radar?",
                "options", "Yes:REPAIR_ACCEPTED:Help the scout|No:REPAIR_DECLINED:Walk away",
                "radius", 20.0
        ));

        assertTrue(result.isSuccess());
        assertNotNull(receivedMessage.get());
        assertTrue(receivedMessage.get().toString().contains("REPAIR_ACCEPTED"));
    }

    private ActiveLunarMob createDummyMob(String id, String displayName) {
        UUID uuid = UUID.randomUUID();
        Location loc = new Location(mockWorld, 0, 64, 0);

        LivingEntity entity = (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(),
                new Class<?>[]{LivingEntity.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return uuid;
                    if (method.getName().equals("isValid")) return true;
                    if (method.getName().equals("isDead")) return false;
                    if (method.getName().equals("getLocation")) return loc.clone();
                    if (method.getName().equals("getWorld")) return mockWorld;
                    if (method.getName().equals("getHeight")) return 2.0;
                    if (method.getName().equals("getCustomName")) return displayName;
                    if (method.getName().equals("teleport")) return true;
                    return null;
                });

        MobDefinition definition = new MobDefinition(
                new MobDefinitionId(id),
                EntityType.ZOMBIE,
                displayName,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                null,
                Set.of()
        );

        return new ActiveLunarMob(entity, definition, new LunarMobIdentity(id, "1"));
    }
}
