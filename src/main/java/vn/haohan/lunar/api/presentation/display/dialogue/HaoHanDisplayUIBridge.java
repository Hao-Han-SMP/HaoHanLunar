package vn.haohan.lunar.api.presentation.display.dialogue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import vn.haohan.lunar.core.subsystem.mob.ActiveMob;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speech bubble and dialogue display provider.
 * Delegates to HaoHanDisplayUI plugin if available, otherwise executes standalone
 * Paper 1.21.1 TextDisplay Billboard.CENTER fallback.
 */
public final class HaoHanDisplayUIBridge {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<UUID, ActiveBubbleSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    private static DisplayUIProvider customProvider;

    public record BubbleOptions(
            String text,
            int durationTicks,
            double offsetY,
            boolean typewriter,
            String style,
            double audienceRadius
    ) {}

    public interface DisplayUIProvider {
        boolean showSpeechBubble(ActiveMob mob, BubbleOptions options);
        void cancelSpeechBubble(UUID mobId);
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

        Component formattedText = MINI_MESSAGE.deserialize(options.text());
        ActiveBubbleSession session = new ActiveBubbleSession(mobId, options.durationTicks(), options.typewriter(), options.text());

        try {
            Location loc = entity.getLocation().clone();
            double height = entity.getHeight() > 0 ? entity.getHeight() : 1.8;
            loc.add(0, height + options.offsetY(), 0);

            if (loc.getWorld() != null) {
                TextDisplay textDisplay = loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                    display.text(formattedText);
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setViewRange((float) (options.audienceRadius() > 0 ? options.audienceRadius() : 24.0));
                });
                session.attachDisplayEntity(textDisplay);
            }
        } catch (Throwable ignored) {
            // Headless testing safe fallback
        }

        ACTIVE_SESSIONS.put(mobId, session);
        return session;
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
     * Central tick update for bubble sessions (handles typewriter and duration countdown).
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

    public static final class ActiveBubbleSession {
        private final UUID mobId;
        private int remainingTicks;
        private final boolean typewriter;
        private final String fullText;
        private int currentCharIndex;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private TextDisplay displayEntity;

        public ActiveBubbleSession(UUID mobId, int durationTicks, boolean typewriter, String fullText) {
            this.mobId = mobId;
            this.remainingTicks = Math.max(1, durationTicks);
            this.typewriter = typewriter;
            this.fullText = fullText != null ? fullText : "";
            this.currentCharIndex = typewriter ? 1 : this.fullText.length();
        }

        public void attachDisplayEntity(TextDisplay display) {
            this.displayEntity = display;
        }

        public TextDisplay displayEntity() {
            return displayEntity;
        }

        public boolean isExpired() {
            return cancelled.get() || remainingTicks <= 0;
        }

        public boolean tick() {
            if (cancelled.get()) return true;
            remainingTicks--;

            if (typewriter && currentCharIndex < fullText.length()) {
                currentCharIndex++;
                if (displayEntity != null && !displayEntity.isDead()) {
                    String partial = fullText.substring(0, currentCharIndex);
                    displayEntity.text(MINI_MESSAGE.deserialize(partial));
                }
            }

            return remainingTicks <= 0;
        }

        public void cleanup() {
            cancelled.set(true);
            if (displayEntity != null) {
                try {
                    displayEntity.remove();
                } catch (Throwable ignored) {
                }
                displayEntity = null;
            }
        }
    }
}
