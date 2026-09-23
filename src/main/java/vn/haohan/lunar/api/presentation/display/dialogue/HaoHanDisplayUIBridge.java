package vn.haohan.lunar.api.presentation.display.dialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speech bubble, dialogue sequence, and interactive chat prompt bridge.
 * Delegates to HaoHanDisplayUI plugin if available, otherwise executes standalone
 * Paper 1.21.1 TextDisplay Billboard.CENTER fallback with dynamic mob tracking,
 * multi-line typewriter effects, and clickable prompt responses.
 */
public final class HaoHanDisplayUIBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<UUID, ActiveBubbleSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    private static DisplayUIProvider customProvider;

    public record BubbleOptions(
            String text,
            List<String> lines,
            int durationTicks,
            double offsetY,
            boolean typewriter,
            String style,
            double audienceRadius,
            Color backgroundColor,
            boolean textShadow,
            boolean seeThrough,
            int lineWidth,
            String soundName,
            String charSoundName,
            boolean followMob,
            boolean broadcastChat
    ) {
        // Backward-compatible constructor
        public BubbleOptions(String text, int durationTicks, double offsetY, boolean typewriter, String style, double audienceRadius) {
            this(text, (text != null && text.contains("|")) ? List.of(text.split("\\|")) : (text != null ? List.of(text) : List.of()),
                    durationTicks, offsetY, typewriter, style, audienceRadius,
                    null, true, false, 200, null, null, true, false);
        }
    }

    public record DialogueChoice(
            String label,
            String signal,
            String hoverText,
            String command
    ) {
        public DialogueChoice(String label, String signal, String hoverText) {
            this(label, signal, hoverText, null);
        }
    }

    public interface DisplayUIProvider {
        boolean showSpeechBubble(ActiveMob mob, BubbleOptions options);
        void cancelSpeechBubble(UUID mobId);
        default void sendDialoguePrompt(Player player, ActiveMob mob, String promptMessage, List<DialogueChoice> choices) {}
    }

    public static void setCustomProvider(DisplayUIProvider provider) {
        customProvider = provider;
    }

    public static boolean isHaoHanDisplayUIAvailable() {
        if (customProvider != null) return true;
        try {
            return Bukkit.getPluginManager() != null
                    && Bukkit.getPluginManager().isPluginEnabled("HaoHanDisplayUI");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Spawns a speech bubble above the mob, respecting provider delegation and fallback.
     */
    public static ActiveBubbleSession displayBubble(ActiveMob mob, BubbleOptions options) {
        if (mob == null || options == null) return null;
        UUID mobId = mob.entityId();

        // Clear existing bubble on mob
        clearBubble(mobId);

        if (customProvider != null) {
            boolean handled = customProvider.showSpeechBubble(mob, options);
            if (handled) return null;
        }

        // Standalone Paper 1.21.1 TextDisplay Fallback
        LivingEntity entity = mob.entity();
        if (entity == null || entity.isDead() || !entity.isValid()) return null;

        List<String> lineList = (options.lines() != null && !options.lines().isEmpty())
                ? options.lines()
                : ((options.text() != null && options.text().contains("|"))
                    ? List.of(options.text().split("\\|"))
                    : (options.text() != null ? List.of(options.text()) : List.of()));

        ActiveBubbleSession session = new ActiveBubbleSession(mob, options, lineList);

        try {
            Location loc = entity.getLocation().clone();
            double height = entity.getHeight() > 0 ? entity.getHeight() : 1.8;
            loc.add(0, height + options.offsetY(), 0);

            if (loc.getWorld() != null) {
                String firstLine = lineList.isEmpty() ? "" : lineList.get(0);
                Component formattedText = MINI_MESSAGE.deserialize(options.typewriter() && !firstLine.isEmpty() ? firstLine.substring(0, 1) : firstLine);
                TextDisplay textDisplay = loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                    display.text(formattedText);
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setViewRange((float) (options.audienceRadius() > 0 ? options.audienceRadius() : 24.0));
                    if (options.backgroundColor() != null) {
                        display.setBackgroundColor(options.backgroundColor());
                    }
                    display.setShadowed(options.textShadow());
                    display.setSeeThrough(options.seeThrough());
                    if (options.lineWidth() > 0) {
                        display.setLineWidth(options.lineWidth());
                    }
                });
                session.attachDisplayEntity(textDisplay);

                // Play starting line sound if present
                if (options.soundName() != null && !options.soundName().isBlank()) {
                    playSoundSafely(loc, options.soundName(), 1.0f, 1.0f);
                }
            }
        } catch (Throwable ignored) {
            // Headless testing safe fallback
        }

        ACTIVE_SESSIONS.put(mobId, session);
        return session;
    }

    /**
     * Sends an interactive dialogue prompt to a player with clickable options.
     */
    public static void sendDialoguePrompt(Player player, ActiveMob mob, String promptMessage, List<DialogueChoice> choices) {
        if (player == null || promptMessage == null || choices == null || choices.isEmpty()) return;

        if (customProvider != null) {
            try {
                customProvider.sendDialoguePrompt(player, mob, promptMessage, choices);
                return;
            } catch (Throwable ignored) {}
        }

        Component baseComp = MINI_MESSAGE.deserialize(promptMessage);
        var builder = Component.text().append(baseComp).append(Component.newline());

        for (int i = 0; i < choices.size(); i++) {
            DialogueChoice choice = choices.get(i);
            String cmd = choice.command();
            if (cmd == null || cmd.isBlank()) {
                UUID mobId = mob != null ? mob.entityId() : null;
                cmd = mobId != null ? "/lunarmob signal " + mobId + " " + choice.signal() : "/lunarmob signal " + choice.signal();
            }
            if (!cmd.startsWith("/")) cmd = "/" + cmd;

            var choiceComp = Component.text(" [" + choice.label() + "]")
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand(cmd));

            if (choice.hoverText() != null && !choice.hoverText().isBlank()) {
                choiceComp = choiceComp.hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize(choice.hoverText())));
            }
            builder.append(choiceComp);
            if (i < choices.size() - 1) {
                builder.append(Component.text("  "));
            }
        }

        player.sendMessage(builder.build());
    }

    public static void clearBubble(UUID mobId) {
        if (mobId == null) return;
        if (customProvider != null) {
            customProvider.cancelSpeechBubble(mobId);
        }
        ActiveBubbleSession removed = ACTIVE_SESSIONS.remove(mobId);
        if (removed != null) {
            removed.cleanup();
        }
    }

    public static void clearAll() {
        for (ActiveBubbleSession session : ACTIVE_SESSIONS.values()) {
            session.cleanup();
        }
        ACTIVE_SESSIONS.clear();
    }

    public static boolean hasActiveBubble(UUID mobId) {
        if (mobId == null) return false;
        ActiveBubbleSession session = ACTIVE_SESSIONS.get(mobId);
        return session != null && !session.isExpired();
    }

    /**
     * Central tick update for bubble sessions (handles typewriter, line sequencing, and dynamic entity tracking).
     */
    public static void tickAll() {
        ACTIVE_SESSIONS.entrySet().removeIf(entry -> {
            ActiveBubbleSession session = entry.getValue();
            if (session.tick()) {
                session.cleanup();
                return true;
            }
            return false;
        });
    }

    public static int activeSessionCount() {
        return ACTIVE_SESSIONS.size();
    }

    private static void playSoundSafely(Location loc, String soundName, float volume, float pitch) {
        if (loc == null || loc.getWorld() == null || soundName == null || soundName.isBlank()) return;
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            loc.getWorld().playSound(loc, sound, volume, pitch);
        } catch (Throwable ignored) {
            // Safe fallback for testing and non-vanilla sound keys
        }
    }

    public static final class ActiveBubbleSession {
        private final ActiveMob mob;
        private final UUID mobId;
        private final BubbleOptions options;
        private final List<String> lines;
        private int currentLineIndex = 0;
        private int remainingLineTicks;
        private int currentCharIndex;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private TextDisplay displayEntity;

        public ActiveBubbleSession(ActiveMob mob, BubbleOptions options, List<String> lines) {
            this.mob = mob;
            this.mobId = mob != null ? mob.entityId() : UUID.randomUUID();
            this.options = options;
            this.lines = (lines != null && !lines.isEmpty()) ? lines : List.of(options != null && options.text() != null ? options.text() : "");

            int totalDuration = options != null ? Math.max(1, options.durationTicks()) : 60;
            int perLineDuration = Math.max(1, totalDuration / Math.max(1, this.lines.size()));
            this.remainingLineTicks = perLineDuration;
            this.currentCharIndex = (options != null && options.typewriter()) ? 1 : currentLineText().length();
        }

        public void attachDisplayEntity(TextDisplay display) {
            this.displayEntity = display;
        }

        public TextDisplay displayEntity() {
            return displayEntity;
        }

        public boolean isExpired() {
            return cancelled.get() || currentLineIndex >= lines.size();
        }

        public String currentLineText() {
            if (currentLineIndex < lines.size()) {
                return lines.get(currentLineIndex);
            }
            return "";
        }

        public int currentLineIndex() {
            return currentLineIndex;
        }

        public boolean tick() {
            if (cancelled.get()) return true;

            // Mob validity and movement tracking
            if (mob != null) {
                LivingEntity entity = mob.entity();
                if (entity == null || entity.isDead() || !entity.isValid()) {
                    return true;
                }

                if (options.followMob() && displayEntity != null && !displayEntity.isDead()) {
                    try {
                        Location entityLoc = entity.getLocation();
                        double height = entity.getHeight() > 0 ? entity.getHeight() : 1.8;
                        Location targetLoc = entityLoc.clone().add(0, height + options.offsetY(), 0);
                        displayEntity.teleport(targetLoc);
                    } catch (Throwable ignored) {}
                }
            }

            remainingLineTicks--;

            String lineText = currentLineText();

            // Typewriter progression
            if (options.typewriter() && currentCharIndex < lineText.length()) {
                currentCharIndex++;
                if (displayEntity != null && !displayEntity.isDead()) {
                    try {
                        String partial = lineText.substring(0, currentCharIndex);
                        displayEntity.text(MINI_MESSAGE.deserialize(partial));

                        // Play typewriter character blip
                        if (options.charSoundName() != null && !options.charSoundName().isBlank() && currentCharIndex % 2 == 0) {
                            playSoundSafely(displayEntity.getLocation(), options.charSoundName(), 0.5f, 1.8f);
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Line transition
            if (remainingLineTicks <= 0) {
                currentLineIndex++;
                if (currentLineIndex < lines.size()) {
                    String nextLine = lines.get(currentLineIndex);
                    int totalDuration = Math.max(1, options.durationTicks());
                    this.remainingLineTicks = Math.max(1, totalDuration / lines.size());
                    this.currentCharIndex = options.typewriter() ? 1 : nextLine.length();

                    if (displayEntity != null && !displayEntity.isDead()) {
                        try {
                            String initial = options.typewriter() && !nextLine.isEmpty() ? nextLine.substring(0, 1) : nextLine;
                            displayEntity.text(MINI_MESSAGE.deserialize(initial));
                            if (options.soundName() != null && !options.soundName().isBlank()) {
                                playSoundSafely(displayEntity.getLocation(), options.soundName(), 1.0f, 1.0f);
                            }
                        } catch (Throwable ignored) {}
                    }
                } else {
                    return true; // Sequence completed
                }
            }

            return false;
        }

        public void cleanup() {
            cancelled.set(true);
            if (displayEntity != null) {
                try {
                    displayEntity.remove();
                } catch (Throwable ignored) {}
                displayEntity = null;
            }
        }
    }
}
