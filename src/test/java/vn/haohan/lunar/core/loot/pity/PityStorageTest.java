package vn.haohan.lunar.core.loot.pity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.haohan.lunar.core.loot.pity.storage.FlatfilePityStorage;
import vn.haohan.lunar.core.loot.pity.storage.SqlitePityStorage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PityStorageTest {

    @Test
    @DisplayName("SqlitePityStorage saves, updates, and reloads player pity records")
    void testSqlitePityStorage(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("pity_test.db").toFile();
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        try (SqlitePityStorage storage = new SqlitePityStorage(dbFile)) {
            storage.init();

            // Save initial pity records
            storage.saveAll(List.of(
                    new PityRecord(player1, "lunar_sword", 15, System.currentTimeMillis()),
                    new PityRecord(player1, "lunar_shield", 5, System.currentTimeMillis()),
                    new PityRecord(player2, "lunar_sword", 49, System.currentTimeMillis())
            ));

            Map<String, Integer> p1Loaded = storage.loadAll(player1);
            assertEquals(15, p1Loaded.get("lunar_sword"));
            assertEquals(5, p1Loaded.get("lunar_shield"));

            Map<String, Integer> p2Loaded = storage.loadAll(player2);
            assertEquals(49, p2Loaded.get("lunar_sword"));

            // Upsert / update record
            storage.saveAll(List.of(
                    new PityRecord(player1, "lunar_sword", 16, System.currentTimeMillis())
            ));
            Map<String, Integer> p1Updated = storage.loadAll(player1);
            assertEquals(16, p1Updated.get("lunar_sword"));
        }
    }

    @Test
    @DisplayName("FlatfilePityStorage saves and reloads player pity records from flat file")
    void testFlatfilePityStorage(@TempDir Path tempDir) {
        File flatFile = tempDir.resolve("pity_flat.txt").toFile();
        UUID player = UUID.randomUUID();

        try (FlatfilePityStorage storage = new FlatfilePityStorage(flatFile)) {
            storage.init();
            storage.saveAll(List.of(
                    new PityRecord(player, "lunar_helm", 30, System.currentTimeMillis())
            ));

            Map<String, Integer> loaded = storage.loadAll(player);
            assertEquals(30, loaded.get("lunar_helm"));
        }

        // Re-read from a new storage instance to verify file persistence
        try (FlatfilePityStorage newStorage = new FlatfilePityStorage(flatFile)) {
            newStorage.init();
            Map<String, Integer> reloaded = newStorage.loadAll(player);
            assertEquals(30, reloaded.get("lunar_helm"));
        }
    }

    @Test
    @DisplayName("PityManager in-memory cache and async write queue flush correctly")
    void testPityManagerCacheAndFlush(@TempDir Path tempDir) throws Exception {
        File dbFile = tempDir.resolve("pity_mgr.db").toFile();
        SqlitePityStorage storage = new SqlitePityStorage(dbFile);
        UUID player = UUID.randomUUID();

        try (PityManager manager = new PityManager(storage)) {
            manager.loadPlayer(player);
            assertTrue(manager.isLoaded(player));

            // Record miss -> immediately reflected in memory with 0ms latency
            int c1 = manager.recordMiss(player, "boss_gem");
            assertEquals(1, c1);
            assertEquals(1, manager.getPity(player, "boss_gem"));
            assertEquals(1, manager.getPendingDirtyCount());

            int c2 = manager.recordMiss(player, "boss_gem");
            assertEquals(2, c2);
            assertEquals(2, manager.getPity(player, "boss_gem"));

            // Flush dirty queue to SQLite
            manager.flush();
            assertEquals(0, manager.getPendingDirtyCount());
        }

        // Re-open fresh PityManager on same database to verify persistence
        SqlitePityStorage newStorage = new SqlitePityStorage(dbFile);
        try (PityManager restoredManager = new PityManager(newStorage)) {
            restoredManager.loadPlayer(player);
            assertEquals(2, restoredManager.getPity(player, "boss_gem"));

            // Record success -> resets to 0
            restoredManager.recordSuccess(player, "boss_gem");
            assertEquals(0, restoredManager.getPity(player, "boss_gem"));
            restoredManager.flush();
        }

        // Verify reset persisted
        SqlitePityStorage checkStorage = new SqlitePityStorage(dbFile);
        try (PityManager checkManager = new PityManager(checkStorage)) {
            checkManager.loadPlayer(player);
            assertEquals(0, checkManager.getPity(player, "boss_gem"));
        }
    }
}
