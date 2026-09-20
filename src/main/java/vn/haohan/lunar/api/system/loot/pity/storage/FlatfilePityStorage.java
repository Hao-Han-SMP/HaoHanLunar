package vn.haohan.lunar.api.loot.pity.storage;

import vn.haohan.lunar.api.loot.pity.PityRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flatfile fallback storage for Pity counters when SQLite is unavailable.
 */
public final class FlatfilePityStorage implements PityStorage {

    private final File file;
    // (playerUuid:poolId) -> PityRecord
    private final Map<String, PityRecord> fileCache = new ConcurrentHashMap<>();

    public FlatfilePityStorage(File file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    @Override
    public synchronized void init() {
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    try {
                        UUID uuid = UUID.fromString(parts[0]);
                        String pool = parts[1];
                        int count = Integer.parseInt(parts[2]);
                        long time = Long.parseLong(parts[3]);
                        fileCache.put(parts[0] + ":" + pool, new PityRecord(uuid, pool, count, time));
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException ignored) {}
    }

    @Override
    public Map<String, Integer> loadAll(UUID playerUuid) {
        if (playerUuid == null) return Map.of();
        String prefix = playerUuid + ":";
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, PityRecord> entry : fileCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getValue().poolId(), entry.getValue().count());
            }
        }
        return result;
    }

    @Override
    public synchronized void saveAll(Collection<PityRecord> records) {
        if (records == null || records.isEmpty()) return;
        for (PityRecord r : records) {
            fileCache.put(r.playerUuid() + ":" + r.poolId(), r);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            writer.write("# HaoHanLunar Pity Flatfile Storage\n");
            for (PityRecord r : fileCache.values()) {
                writer.write(r.playerUuid() + ";" + r.poolId() + ";" + r.count() + ";" + r.lastUpdated() + "\n");
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void close() {
        // No persistent handles to close
    }
}
