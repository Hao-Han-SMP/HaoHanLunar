package vn.haohan.lunar.api.spawner.fixed;

import java.util.List;
import java.util.Objects;

/**
 * Represents a wave in a multi-wave spawner sequence.
 */
public record SpawnerWave(int waveNumber, List<WaveEntry> entries) {

    public SpawnerWave {
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public record WaveEntry(String mobId, int count) {
        public WaveEntry {
            Objects.requireNonNull(mobId, "Mob ID must not be null");
            count = Math.max(1, count);
        }
    }
}
