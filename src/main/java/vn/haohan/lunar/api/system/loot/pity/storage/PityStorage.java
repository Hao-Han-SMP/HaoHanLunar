package vn.haohan.lunar.api.system.loot.pity.storage;

import vn.haohan.lunar.api.system.loot.pity.PityRecord;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Storage interface for persistent Pity counters.
 */
public interface PityStorage extends AutoCloseable {

    /**
     * Initializes the storage schema/files.
     */
    void init() throws Exception;

    /**
     * Loads all pity records for a given player into a poolId -> count map.
     */
    Map<String, Integer> loadAll(UUID playerUuid);

    /**
     * Persists a collection of updated pity records.
     */
    void saveAll(Collection<PityRecord> records);

    /**
     * Closes underlying storage resources.
     */
    @Override
    void close();
}
