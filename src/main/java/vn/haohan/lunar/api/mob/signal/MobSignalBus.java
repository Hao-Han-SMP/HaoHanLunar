package vn.haohan.lunar.api.mob.signal;

import org.bukkit.Location;
import org.bukkit.World;
import vn.haohan.lunar.core.mob.ActiveLunarMob;
import vn.haohan.lunar.core.mob.LunarMobManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Signal Event Bus enabling inter-mob messaging and wireless skill coordination.
 * Features a strict recursion depth barrier (max depth 3) to eliminate infinite ping-pong signal storms.
 */
public final class MobSignalBus {

    public static final int MAX_CASCADE_DEPTH = 3;

    public record SignalDelivery(
            ActiveLunarMob sender,
            ActiveLunarMob recipient,
            String signal,
            int cascadeDepth
    ) {}

    private final List<BiConsumer<SignalDelivery, Integer>> listeners = new ArrayList<>();

    public void registerListener(BiConsumer<SignalDelivery, Integer> listener) {
        if (listener != null) listeners.add(listener);
    }

    /**
     * Dispatches a single point-to-point signal from sender to recipient.
     *
     * @param sender       the originating lunar mob (can be same as target)
     * @param recipient    the receiving lunar mob
     * @param signal       the alphanumeric signal key
     * @param cascadeDepth current cascade recursion level
     * @return true if successfully delivered without depth violation
     */
    public boolean sendSignal(ActiveLunarMob sender, ActiveLunarMob recipient, String signal, int cascadeDepth) {
        if (recipient == null || signal == null || signal.isBlank()) return false;
        if (cascadeDepth > MAX_CASCADE_DEPTH) {
            // Drop signal and halt cascade to protect server tick rate
            return false;
        }

        String normalizedSignal = signal.trim().toUpperCase(Locale.ROOT);
        SignalDelivery delivery = new SignalDelivery(sender, recipient, normalizedSignal, cascadeDepth);

        // Notify registered subscribers
        for (BiConsumer<SignalDelivery, Integer> listener : listeners) {
            listener.accept(delivery, cascadeDepth);
        }

        return true;
    }

    /**
     * Broadcasts a wireless radio signal to all eligible lunar mobs within a spherical radius.
     *
     * @param sender       originating lunar mob
     * @param signal       alphanumeric signal key
     * @param radius       detection radius in blocks
     * @param targetFilter filter criteria ('ALL', 'MINIONS', 'SAME_ID', etc.)
     * @param mobManager   manager providing active mobs
     * @param cascadeDepth current cascade recursion level
     * @return number of mobs that received the signal
     */
    public int broadcastSignal(ActiveLunarMob sender, String signal, double radius, String targetFilter,
                               LunarMobManager mobManager, int cascadeDepth) {
        if (sender == null || sender.entity() == null || mobManager == null) return 0;
        if (cascadeDepth > MAX_CASCADE_DEPTH) return 0;

        Location senderLoc = sender.entity().getLocation();
        World senderWorld = senderLoc.getWorld();
        if (senderWorld == null) return 0;

        double radiusSq = radius * radius;
        String filter = targetFilter != null ? targetFilter.trim().toUpperCase(Locale.ROOT) : "ALL";
        int deliveredCount = 0;

        for (ActiveLunarMob candidate : mobManager.snapshot()) {
            if (candidate == null || candidate.entity() == null || !candidate.entity().isValid() || candidate.entity().isDead()) {
                continue;
            }
            if (!senderWorld.equals(candidate.entity().getWorld())) {
                continue;
            }
            if (candidate.entity().getLocation().distanceSquared(senderLoc) > radiusSq) {
                continue;
            }

            // Apply filter
            if (!matchesFilter(sender, candidate, filter)) {
                continue;
            }

            if (sendSignal(sender, candidate, signal, cascadeDepth)) {
                deliveredCount++;
            }
        }

        return deliveredCount;
    }

    private boolean matchesFilter(ActiveLunarMob sender, ActiveLunarMob candidate, String filter) {
        return switch (filter) {
            case "ALL" -> true;
            case "OTHERS" -> !candidate.entityId().equals(sender.entityId());
            case "SAME_ID" -> candidate.definition().id().equals(sender.definition().id());
            case "MINIONS" -> candidate.parentUUID() != null && candidate.parentUUID().equals(sender.entityId());
            default -> true;
        };
    }
}
