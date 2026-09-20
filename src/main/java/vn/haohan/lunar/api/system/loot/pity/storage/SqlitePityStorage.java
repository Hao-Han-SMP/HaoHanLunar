package vn.haohan.lunar.api.loot.pity.storage;

import vn.haohan.lunar.api.loot.pity.PityRecord;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * SQLite embedded persistence provider for player Pity counters.
 */
public final class SqlitePityStorage implements PityStorage {

    private final String jdbcUrl;
    private Connection connection;

    public SqlitePityStorage(File dbFile) {
        Objects.requireNonNull(dbFile, "dbFile must not be null");
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    public SqlitePityStorage(String jdbcUrl) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(jdbcUrl);
        }
        return connection;
    }

    @Override
    public synchronized void init() throws SQLException {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS haohan_drop_pity (
                    player_uuid VARCHAR(36) NOT NULL,
                    pool_id VARCHAR(64) NOT NULL,
                    pity_count INT NOT NULL DEFAULT 0,
                    last_updated BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, pool_id)
                );
            """);
        }
    }

    @Override
    public synchronized Map<String, Integer> loadAll(UUID playerUuid) {
        if (playerUuid == null) return Map.of();
        Map<String, Integer> result = new HashMap<>();
        String sql = "SELECT pool_id, pity_count FROM haohan_drop_pity WHERE player_uuid = ?";

        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("pool_id"), rs.getInt("pity_count"));
                }
            }
        } catch (SQLException ignored) {}
        return result;
    }

    @Override
    public synchronized void saveAll(Collection<PityRecord> records) {
        if (records == null || records.isEmpty()) return;

        String sql = """
            INSERT INTO haohan_drop_pity (player_uuid, pool_id, pity_count, last_updated)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid, pool_id) DO UPDATE SET
                pity_count = excluded.pity_count,
                last_updated = excluded.last_updated;
        """;

        try {
            Connection conn = getConnection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (PityRecord r : records) {
                    ps.setString(1, r.playerUuid().toString());
                    ps.setString(2, r.poolId());
                    ps.setInt(3, r.count());
                    ps.setLong(4, r.lastUpdated());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } finally {
                conn.setAutoCommit(autoCommit);
            }
        } catch (SQLException ignored) {}
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {}
            connection = null;
        }
    }
}
