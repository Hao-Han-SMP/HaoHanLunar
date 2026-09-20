package vn.haohan.lunar.api.presentation.audio;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 3D Spatial Audio Engine and multi-track Sound Sequence scheduler.
 * Implements linear, exponential, and inverse-square volume falloff algorithms.
 */
public final class SpatialAudioEngine {

    public enum FalloffModel {
        LINEAR,
        EXPONENTIAL,
        INVERSE_SQUARE
    }

    public record SoundStep(
            String soundKey,
            int delayTicks,
            float volume,
            float pitch
    ) {}

    public static final class ActiveSoundSequence {
        private final UUID id = UUID.randomUUID();
        private final UUID ownerId;
        private final Location origin;
        private final List<SoundStep> steps;
        private final double maxRadius;
        private final FalloffModel falloff;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private int currentTick = 0;

        public ActiveSoundSequence(UUID ownerId, Location origin, List<SoundStep> steps,
                                   double maxRadius, FalloffModel falloff) {
            this.ownerId = ownerId;
            this.origin = origin.clone();
            this.steps = new ArrayList<>(steps != null ? steps : List.of());
            this.maxRadius = Math.max(1.0, maxRadius);
            this.falloff = falloff != null ? falloff : FalloffModel.EXPONENTIAL;
        }

        public UUID id() { return id; }
        public UUID ownerId() { return ownerId; }
        public boolean isCancelled() { return cancelled.get(); }
        public void cancel() { cancelled.set(true); }

        public boolean tick(Collection<? extends Player> worldPlayers) {
            if (cancelled.get()) return true;

            for (SoundStep step : steps) {
                if (step.delayTicks() == currentTick) {
                    playSoundToAudience(step, origin, worldPlayers, maxRadius, falloff);
                }
            }

            currentTick++;
            boolean allFinished = steps.stream().allMatch(s -> s.delayTicks() < currentTick);
            return allFinished;
        }
    }

    private final Map<UUID, List<ActiveSoundSequence>> activeSequences = new ConcurrentHashMap<>();

    /**
     * Schedules an audio sequence for execution.
     */
    public ActiveSoundSequence playSequence(UUID ownerId, Location origin, List<SoundStep> steps,
                                            double maxRadius, FalloffModel falloff) {
        if (origin == null || steps == null || steps.isEmpty()) return null;

        ActiveSoundSequence sequence = new ActiveSoundSequence(ownerId, origin, steps, maxRadius, falloff);
        if (ownerId != null) {
            activeSequences.computeIfAbsent(ownerId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(sequence);
        }
        return sequence;
    }

    /**
     * Halts and stops all active sound sequences associated with the specified owner UUID.
     */
    public void stopSound(UUID ownerId) {
        if (ownerId == null) return;
        List<ActiveSoundSequence> list = activeSequences.remove(ownerId);
        if (list != null) {
            for (ActiveSoundSequence seq : list) {
                seq.cancel();
            }
        }
    }

    /**
     * Ticks all running audio sequences centrally.
     */
    public void tickAll(Collection<? extends Player> worldPlayers) {
        activeSequences.entrySet().removeIf(entry -> {
            List<ActiveSoundSequence> list = entry.getValue();
            synchronized (list) {
                list.removeIf(seq -> seq.tick(worldPlayers));
                return list.isEmpty();
            }
        });
    }

    /**
     * Plays a single sound with spatial distance falloff calculation.
     */
    public static void playSoundToAudience(SoundStep step, Location origin,
                                          Collection<? extends Player> worldPlayers,
                                          double maxRadius, FalloffModel falloff) {
        if (origin == null || worldPlayers == null || step == null) return;

        double maxDistSq = maxRadius * maxRadius;

        for (Player player : worldPlayers) {
            if (player == null || !player.isValid() || player.isDead()) continue;
            if (player.getWorld() == null || !player.getWorld().equals(origin.getWorld())) continue;

            double distSq = player.getLocation().distanceSquared(origin);
            if (distSq > maxDistSq) continue;

            double dist = Math.sqrt(distSq);
            float calculatedVolume = calculateVolume(step.volume(), dist, maxRadius, falloff);

            if (calculatedVolume > 0.01f) {
                try {
                    player.playSound(origin, step.soundKey(), SoundCategory.HOSTILE, calculatedVolume, step.pitch());
                } catch (Throwable ignored) {
                    // Headless safe fallback
                }
            }
        }
    }

    /**
     * Computes the volume factor according to distance and falloff curves.
     */
    public static float calculateVolume(float baseVolume, double distance, double maxRadius, FalloffModel falloff) {
        if (distance <= 0.0) return baseVolume;
        if (distance >= maxRadius) return 0.0f;

        double normalizedDist = distance / maxRadius; // 0.0 to 1.0

        double factor = switch (falloff != null ? falloff : FalloffModel.EXPONENTIAL) {
            case LINEAR -> 1.0 - normalizedDist;
            case EXPONENTIAL -> Math.exp(-2.5 * normalizedDist);
            case INVERSE_SQUARE -> 1.0 / (1.0 + (distance * distance) / Math.max(1.0, maxRadius * 2.0));
        };

        return (float) Math.max(0.0, Math.min(baseVolume, baseVolume * factor));
    }

    public int activeSequenceCount() {
        return activeSequences.values().stream().mapToInt(List::size).sum();
    }
}
