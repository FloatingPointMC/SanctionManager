package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.Addition;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * SQLite database implementation for persistent entry storage.
 */
@RequiredArgsConstructor
public class SQLite implements IDatabase {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull HikariDataSource dataSource;
    private volatile Map<String, Long> typeRelIdCounters = new HashMap<>();

    private static final String ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";
    private static final String ENABLE_WAL_MODE = "PRAGMA journal_mode = WAL";
    private static final String SET_BUSY_TIMEOUT = "PRAGMA busy_timeout = 5000";

    private static final String CREATE_METADATA_TABLE =
            "CREATE TABLE IF NOT EXISTS sm_metadata (" +
                    "type_id TEXT PRIMARY KEY," +
                    "next_rel_id INTEGER NOT NULL" +
                    ")";

    private static final String CREATE_PARENT_TABLE =
            "CREATE TABLE IF NOT EXISTS sm_parent (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "target BLOB NOT NULL," +
                    "type TEXT NOT NULL" +
                    ")";

    private static final String CREATE_PARENT_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_parent_target ON sm_parent(target)";

    private static final String CREATE_DATA_TABLE =
            "CREATE TABLE IF NOT EXISTS %s (" +
                    "rel_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "parent_id INTEGER NOT NULL UNIQUE," +
                    "executor BLOB NOT NULL," +
                    "expiry_time INTEGER," +
                    "execution_time INTEGER NOT NULL," +
                    "status INTEGER NOT NULL," +
                    "reason TEXT," +
                    "FOREIGN KEY (parent_id) REFERENCES sm_parent(id) ON DELETE CASCADE" +
                    ")";

    private static final String CREATE_DATA_TABLE_EXTRA =
            "CREATE TABLE IF NOT EXISTS %s (" +
                    "rel_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "parent_id INTEGER NOT NULL UNIQUE," +
                    "executor BLOB NOT NULL," +
                    "expiry_time INTEGER," +
                    "execution_time INTEGER NOT NULL," +
                    "status INTEGER NOT NULL," +
                    "reason TEXT," +
                    "extra_data TEXT," +
                    "FOREIGN KEY (parent_id) REFERENCES sm_parent(id) ON DELETE CASCADE" +
                    ")";

    @Override
    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(ENABLE_FOREIGN_KEYS);
                stmt.execute(ENABLE_WAL_MODE);
                stmt.execute(SET_BUSY_TIMEOUT);
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_METADATA_TABLE);
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute(CREATE_PARENT_TABLE);
                stmt.execute(CREATE_PARENT_INDEX);
            }

            for (String typeId : core.getTypeManager().getTypeIdentifiers()) {
                createDataTable(connection, typeId);
            }

            loadMetadata(connection);
        }
    }

    private void createDataTable(@NotNull Connection connection, @NotNull String typeId) throws SQLException {
        String tableName = "sm_data_" + typeId;
        String createStmt = core.getTypeManager().getType(typeId).hasExtraData()
                ? String.format(CREATE_DATA_TABLE_EXTRA, tableName)
                : String.format(CREATE_DATA_TABLE, tableName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createStmt);
        }
    }

    private void loadMetadata(@NotNull Connection connection) throws SQLException {
        typeRelIdCounters = new HashMap<>();
        String query = "SELECT type_id, next_rel_id FROM sm_metadata";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                typeRelIdCounters.put(rs.getString("type_id"), rs.getLong("next_rel_id"));
            }
        }
    }

    @Override
    public Map<String, Long> getTypeRelIdCounters() {
        return new HashMap<>(typeRelIdCounters);
    }

    @Override
    public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
        typeRelIdCounters.put(typeIdentifier, nextRelId);
    }

    @Override
    public void persistMetadata() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("DELETE FROM sm_metadata");
                }

                String insert = "INSERT INTO sm_metadata (type_id, next_rel_id) VALUES (?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insert)) {
                    for (Map.Entry<String, Long> entry : typeRelIdCounters.entrySet()) {
                        pstmt.setString(1, entry.getKey());
                        pstmt.setLong(2, entry.getValue());
                        pstmt.addBatch();
                    }
                    pstmt.executeBatch();
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void insert(@NotNull String tableName, @NotNull UUID target, @Nullable String reason,
                       @NotNull Date executingTime, @Nullable Date expiryTime, @Nullable JsonObject data) {
        throw new UnsupportedOperationException("Use complete methods in DatabaseStorageService");
    }

    /**
     * Inserts a new entry and returns global and type-relative IDs.
     */
    public Addition insertEntry(@NotNull String typeId, @NotNull UUID target, @NotNull UUID executor,
                                @Nullable String reason, @NotNull Date executingTime,
                                @Nullable Date expiryTime, @Nullable JsonObject data) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String insertParent = "INSERT INTO sm_parent (target, type) VALUES (?, ?)";
                long parentId;
                try (PreparedStatement pstmt = connection.prepareStatement(insertParent, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setBytes(1, uuidToBytes(target));
                    pstmt.setString(2, typeId);
                    pstmt.executeUpdate();
                    try (ResultSet keys = pstmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            parentId = keys.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve generated parent ID");
                        }
                    }
                }

                String dataTableName = "sm_data_" + typeId;
                String insertData = data == null
                        ? "INSERT INTO " + dataTableName + " (parent_id, executor, expiry_time, execution_time, status, reason) VALUES (?, ?, ?, ?, ?, ?)"
                        : "INSERT INTO " + dataTableName + " (parent_id, executor, expiry_time, execution_time, status, reason, extra_data) VALUES (?, ?, ?, ?, ?, ?, ?)";

                long relId;
                try (PreparedStatement pstmt = connection.prepareStatement(insertData, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setLong(1, parentId);
                    pstmt.setBytes(2, uuidToBytes(executor));
                    pstmt.setObject(3, expiryTime != null ? expiryTime.getTime() : null);
                    pstmt.setLong(4, executingTime.getTime());
                    pstmt.setInt(5, 0);
                    pstmt.setString(6, reason);
                    if (data != null) {
                        pstmt.setString(7, data.toString());
                    }
                    pstmt.executeUpdate();
                    try (ResultSet keys = pstmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            relId = keys.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve generated relId");
                        }
                    }
                }

                connection.commit();
                return new Addition(parentId, relId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * Retrieves entry by global ID.
     */
    public Optional<EntryPersisted> getEntryById(long id) throws SQLException {
        String query = "SELECT sp.id, sp.target, sp.type FROM sm_parent sp WHERE sp.id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(buildEntry(rs, connection));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Queries entries by target UUID.
     */
    public List<EntryPersisted> queryByTarget(@NotNull UUID target) throws SQLException {
        List<EntryPersisted> results = new ArrayList<>();
        String query = "SELECT sp.id, sp.target, sp.type FROM sm_parent sp WHERE sp.target = ? ORDER BY sp.id ASC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setBytes(1, uuidToBytes(target));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(buildEntry(rs, connection));
                }
            }
        }
        return results;
    }

    /**
     * Queries entries by type and type-relative ID.
     */
    public List<EntryPersisted> queryByTypeAndRelId(@NotNull String typeId, long relId) throws SQLException {
        List<EntryPersisted> results = new ArrayList<>();
        String dataTableName = "sm_data_" + typeId;
        String query = "SELECT sp.id, sp.target, sp.type FROM sm_parent sp " +
                "INNER JOIN " + dataTableName + " sd ON sp.id = sd.parent_id " +
                "WHERE sd.rel_id = ? ORDER BY sp.id ASC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setLong(1, relId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(buildEntry(rs, connection));
                }
            }
        }
        return results;
    }

    /**
     * Queries all entries.
     */
    public List<EntryPersisted> queryAll() throws SQLException {
        List<EntryPersisted> results = new ArrayList<>();
        String query = "SELECT sp.id, sp.target, sp.type FROM sm_parent sp ORDER BY sp.id ASC";
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                results.add(buildEntry(rs, connection));
            }
        }
        return results;
    }

    /**
     * Updates entry record.
     */
    public void updateEntry(@NotNull String typeId, long parentId, @Nullable UUID withdrawnBy,
                           @Nullable String withdrawalReason, boolean overridden,
                           @Nullable Long overriddenBy) throws SQLException {
        String dataTableName = "sm_data_" + typeId;
        String query = "UPDATE " + dataTableName + " SET status = ? WHERE parent_id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            int status = 0;
            if (withdrawnBy != null) status |= 1;
            if (overridden) status |= 2;

            pstmt.setInt(1, status);
            pstmt.setLong(2, parentId);
            pstmt.executeUpdate();
        }
    }

    /**
     * Deletes entry by global ID.
     */
    public void deleteEntry(long id) throws SQLException {
        String query = "DELETE FROM sm_parent WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    /**
     * Builds EntryPersisted from parent table result set.
     */
    private EntryPersisted buildEntry(@NotNull ResultSet parentRs, @NotNull Connection connection) throws SQLException {
        long id = parentRs.getLong("id");
        UUID target = bytesToUuid(parentRs.getBytes("target"));
        String typeId = parentRs.getString("type");

        String dataTableName = "sm_data_" + typeId;
        String dataQuery = "SELECT rel_id, executor, expiry_time, execution_time, status, reason" +
                (core.getTypeManager().getType(typeId).hasExtraData() ? ", extra_data" : "") +
                " FROM " + dataTableName + " WHERE parent_id = ? LIMIT 1";

        try (PreparedStatement pstmt = connection.prepareStatement(dataQuery)) {
            pstmt.setLong(1, id);
            try (ResultSet dataRs = pstmt.executeQuery()) {
                if (dataRs.next()) {
                    return resultSetToEntry(id, target, typeId, dataRs);
                }
            }
        }

        throw new SQLException("Entry " + id + " has no data in type table " + dataTableName);
    }

    /**
     * Converts result set row to EntryPersisted.
     */
    private EntryPersisted resultSetToEntry(long id, @NotNull UUID target, @NotNull String typeId,
                                           @NotNull ResultSet rs) throws SQLException {
        long relId = rs.getLong("rel_id");
        UUID executor = bytesToUuid(rs.getBytes("executor"));
        Long expiryTimeMs = rs.getObject("expiry_time") != null ? rs.getLong("expiry_time") : null;
        Long executionTimeMs = rs.getLong("execution_time");
        int status = rs.getInt("status");
        String reason = rs.getString("reason");

        Date expiryTime = expiryTimeMs != null ? new Date(expiryTimeMs) : null;
        Date executionTime = new Date(executionTimeMs);

        JsonObject data = null;
        if (core.getTypeManager().getType(typeId).hasExtraData()) {
            String dataStr = rs.getString("extra_data");
            if (dataStr != null) {
                data = com.google.gson.JsonParser.parseString(dataStr).getAsJsonObject();
            }
        }

        boolean isWithdrawn = (status & 1) != 0;
        boolean isOverridden = (status & 2) != 0;

        return new EntryPersisted(
                id, relId,
                core.getTypeManager().getType(typeId),
                target, executor,
                expiryTime, executionTime,
                reason, data,
                isWithdrawn ? executor : null,
                null,
                isOverridden,
                null,
                0
        );
    }

    /**
     * UUID to 16-byte BLOB.
     */
    private static byte[] uuidToBytes(@NotNull UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * 16-byte BLOB to UUID.
     */
    private static @NotNull UUID bytesToUuid(byte @NotNull [] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException("UUID must be exactly 16 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
