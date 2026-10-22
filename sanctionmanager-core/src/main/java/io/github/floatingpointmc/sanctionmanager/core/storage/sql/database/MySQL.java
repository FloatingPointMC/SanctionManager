package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.Addition;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import java.util.Date;

@RequiredArgsConstructor
public class MySQL implements IDatabase {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull HikariDataSource dataSource;

    private static final String CHECK_PARENT_EXISTS =
            "SELECT ENGINE, TABLE_COLLATION " +
                    "FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'sm_parent' " +
                    "AND TABLE_TYPE = 'BASE TABLE'";

    private static final String CREATE_PARENT =
            "CREATE TABLE sm_parent (" +
                    "id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                    "target BINARY(16) NOT NULL," +
                    "type VARCHAR(32) NOT NULL," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_target (target)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String CHECK_COLUMNS =
            "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA " +
                    "FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'sm_parent'";

    private static final String CHECK_PRIMARY_KEY =
            "SELECT COLUMN_NAME " +
                    "FROM information_schema.KEY_COLUMN_USAGE " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'sm_parent' " +
                    "AND CONSTRAINT_NAME = 'PRIMARY' " +
                    "ORDER BY ORDINAL_POSITION";

    private static final String CHECK_INDEX =
            "SELECT NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME " +
                    "FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() " +
                    "AND TABLE_NAME = 'sm_parent' " +
                    "AND INDEX_NAME = 'idx_target' " +
                    "ORDER BY SEQ_IN_INDEX";

    private static final String CHECK_CHARSET =
            "SELECT CHARACTER_SET_NAME " +
                    "FROM information_schema.COLLATION_CHARACTER_SET_APPLICABILITY " +
                    "WHERE COLLATION_NAME = ?";

    private static final String CREATE_DATA =
            "CREATE TABLE %s (" +
                    "rel_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                    "parent_id BIGINT UNSIGNED NOT NULL," +
                    "executor BINARY(16) NOT NULL," +
                    "expiry_time DATETIME NULL," +
                    "execution_time DATETIME NOT NULL," +
                    "status TINYINT NOT NULL," +
                    "PRIMARY KEY (rel_id)," +
                    "UNIQUE KEY uk_parent_id (parent_id)," +
                    "CONSTRAINT %s FOREIGN KEY (parent_id) " +
                    "REFERENCES sm_parent (id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String CREATE_DATA_EXTRA =
            "CREATE TABLE %s (" +
                    "rel_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                    "parent_id BIGINT UNSIGNED NOT NULL," +
                    "executor BINARY(16) NOT NULL," +
                    "expiry_time DATETIME NULL," +
                    "execution_time DATETIME NOT NULL," +
                    "status TINYINT NOT NULL," +
                    "extra_data JSON NOT NULL," +
                    "PRIMARY KEY (rel_id)," +
                    "UNIQUE KEY uk_parent_id (parent_id)," +
                    "CONSTRAINT %s FOREIGN KEY (parent_id) " +
                    "REFERENCES sm_parent (id) ON DELETE CASCADE" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final String INSERT =
            "INSERT INTO %s ";

    private static final String INSERT_EXTRA =
            "INSERT INTO %s ";

    private static byte[] uuidToBytes(@NotNull UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    public static @NotNull UUID bytesToUuid(byte @NotNull [] bytes) {
        if (bytes.length != 16) {
            throw new IllegalArgumentException(
                    "UUID must be exactly 16 bytes"
            );
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    @Override
    public void initialize() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            String product = metadata.getDatabaseProductName()
                    .toLowerCase(Locale.ROOT);

            if (!product.contains("mysql")
                    && !product.contains("mariadb")) {
                throw new SQLException(
                        "Unsupported database product: "
                                + metadata.getDatabaseProductName()
                );
            }

            ArrayList<String> errors = new ArrayList<>();
            checkOrCreateParentTables(connection, errors);
            checkOrCreateDataTables(connection, errors);
        }
    }

    private void checkOrCreateParentTables(@NotNull Connection connection, @NotNull ArrayList<String> errors) throws SQLException {
        if (tableNotExists(connection, "sm_parent")) {
            try (Statement create = connection.createStatement()) {
                create.executeUpdate(CREATE_PARENT);
            }
        }

        // Check columns.
        Set<String> foundColumns = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(CHECK_COLUMNS);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                String name = result.getString("COLUMN_NAME");
                String type = result.getString("COLUMN_TYPE");
                String nullable = result.getString("IS_NULLABLE");
                String extra = result.getString("EXTRA");

                foundColumns.add(name.toLowerCase(Locale.ROOT));

                switch (name.toLowerCase(Locale.ROOT)) {
                    case "id":
                        checkEquals(
                                errors,
                                "id type",
                                "bigint unsigned",
                                normalize(type)
                        );
                        checkEquals(
                                errors,
                                "id nullability",
                                "NO",
                                nullable
                        );

                        if (extra == null
                                || !extra.toLowerCase(Locale.ROOT)
                                .contains("auto_increment")) {
                            errors.add(
                                    "Column id must be AUTO_INCREMENT"
                            );
                        }
                        break;

                    case "target":
                        checkEquals(
                                errors,
                                "target type",
                                "binary(16)",
                                normalize(type)
                        );
                        checkEquals(
                                errors,
                                "target nullability",
                                "NO",
                                nullable
                        );
                        break;

                    case "type":
                        checkEquals(
                                errors,
                                "type type",
                                "varchar(32)",
                                normalize(type)
                        );
                        checkEquals(
                                errors,
                                "type nullability",
                                "NO",
                                nullable
                        );
                        break;

                    default:
                        errors.add(
                                "Unexpected column: " + name
                        );
                        break;
                }
            }
        }

        checkRequiredColumn(errors, foundColumns, "id");
        checkRequiredColumn(errors, foundColumns, "target");
        checkRequiredColumn(errors, foundColumns, "type");

        // Check primary key.
        List<String> primaryKeyColumns = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(CHECK_PRIMARY_KEY);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                primaryKeyColumns.add(
                        result.getString("COLUMN_NAME")
                );
            }
        }

        if (!primaryKeyColumns.equals(
                Collections.singletonList("id"))) {
            errors.add(
                    "Primary key must contain id only, found: "
                            + primaryKeyColumns
            );
        }

        // Check idx_target.
        List<String> indexColumns = new ArrayList<>();
        Boolean nonUnique = null;
        int expectedSequence = 1;

        try (PreparedStatement statement =
                     connection.prepareStatement(CHECK_INDEX);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                int currentNonUnique = result.getInt("NON_UNIQUE");
                int sequence = result.getInt("SEQ_IN_INDEX");

                if (nonUnique == null) {
                    nonUnique = currentNonUnique == 1;
                } else if (nonUnique != (currentNonUnique == 1)) {
                    errors.add(
                            "Index idx_target has inconsistent uniqueness metadata"
                    );
                }

                if (sequence != expectedSequence++) {
                    errors.add(
                            "Index idx_target has an invalid column sequence"
                    );
                }

                indexColumns.add(result.getString("COLUMN_NAME"));
            }
        }

        if (nonUnique == null) {
            errors.add("Missing index idx_target");
        } else {
            if (!nonUnique) {
                errors.add(
                        "Index idx_target must be non-unique"
                );
            }

            if (!indexColumns.equals(
                    Collections.singletonList("target"))) {
                errors.add(
                        "Index idx_target must contain target only, found: "
                                + indexColumns
                );
            }
        }

        if (!errors.isEmpty()) {
            throw new SQLException(
                    "Invalid schema for sm_parent:\n - "
                            + String.join("\n - ", errors)
            );
        }
    }

    private void checkOrCreateDataTables(
            @NotNull Connection connection,
            @NotNull ArrayList<String> errors
    ) throws SQLException {
        for (EntryType type : core.getTypeManager().getTypes()) {
            String identifier = type.getIdentifier();

            String tableName = String.format("sm_data_%s", identifier);
            String constraintName = "fk_" + tableName + "_parent";

            checkOrCreateDataTable(
                    connection,
                    tableName,
                    constraintName,
                    type.withExtraData(),
                    errors
            );
        }
    }

    private void checkOrCreateDataTable(
            @NotNull Connection connection,
            @NotNull String tableName,
            @NotNull String constraintName,
            boolean withExtraData,
            @NotNull ArrayList<String> errors
    ) throws SQLException {
        if (tableNotExists(connection, tableName)) {
            String template = withExtraData
                    ? CREATE_DATA_EXTRA
                    : CREATE_DATA;

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        String.format(template, tableName, constraintName)
                );
            }

            return;
        }

        validateDataTable(
                connection,
                tableName,
                constraintName,
                withExtraData
        );
    }

    private void validateDataTable(
            @NotNull Connection connection,
            @NotNull String tableName,
            @NotNull String constraintName,
            boolean withExtraData
    ) throws SQLException {
        List<String> errors = new ArrayList<>();
        Map<String, ColumnDefinition> expected = new LinkedHashMap<>();

        expected.put("rel_id",
                new ColumnDefinition("bigint unsigned", "NO", true));
        expected.put("parent_id",
                new ColumnDefinition("bigint unsigned", "NO", false));
        expected.put("executor",
                new ColumnDefinition("binary(16)", "NO", false));
        expected.put("expiry_time",
                new ColumnDefinition("datetime", "YES", false));
        expected.put("execution_time",
                new ColumnDefinition("datetime", "NO", false));
        expected.put("status",
                new ColumnDefinition("tinyint", "NO", false));

        if (withExtraData) {
            expected.put("extra_data",
                    new ColumnDefinition("json", "NO", false));
        }

        Set<String> found = new HashSet<>();

        String sql =
                "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA " +
                        "FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("COLUMN_NAME")
                            .toLowerCase(Locale.ROOT);
                    String columnType = normalize(result.getString("COLUMN_TYPE"));
                    String nullable = result.getString("IS_NULLABLE");
                    String extra = result.getString("EXTRA");

                    found.add(name);

                    ColumnDefinition definition = expected.get(name);
                    if (definition == null) {
                        errors.add("Unexpected column: " + name);
                        continue;
                    }

                    checkEquals(errors, name + " type",
                            definition.name, columnType);
                    checkEquals(errors, name + " nullability",
                            definition.nullability, nullable);

                    if (definition.autoIncrement
                            && (extra == null || !extra.toLowerCase(Locale.ROOT)
                            .contains("auto_increment"))) {
                        errors.add(name + " must be AUTO_INCREMENT");
                    }
                }
            }
        }

        for (String name : expected.keySet()) {
            checkRequiredColumn(errors, found, name);
        }

        validateDataTableIndexes(connection, tableName, errors);
        validateDataTableForeignKey(
                connection, tableName, constraintName, errors
        );

        if (!errors.isEmpty()) {
            throw new SQLException(
                    "Invalid schema for " + tableName + ":\n - "
                            + String.join("\n - ", errors)
            );
        }
    }

    private boolean tableNotExists(
            @NotNull Connection connection,
            @NotNull String tableName
    ) throws SQLException {
        String sql =
                "SELECT 1 FROM information_schema.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = ? " +
                        "AND TABLE_TYPE = 'BASE TABLE'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet result = statement.executeQuery()) {
                return !result.next();
            }
        }
    }

    private void validateDataTableIndexes(
            @NotNull Connection connection,
            @NotNull String tableName,
            @NotNull List<String> errors
    ) throws SQLException {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        Map<String, Boolean> unique = new HashMap<>();

        String sql =
                "SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME " +
                        "FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                        "ORDER BY INDEX_NAME, SEQ_IN_INDEX";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString("INDEX_NAME");
                    indexes.computeIfAbsent(name, ignored -> new ArrayList<>())
                            .add(result.getString("COLUMN_NAME"));

                    boolean isUnique = result.getInt("NON_UNIQUE") == 0;
                    Boolean previous = unique.putIfAbsent(name, isUnique);

                    if (previous != null && previous != isUnique) {
                        errors.add("Inconsistent index metadata: " + name);
                    }
                }
            }
        }

        if (!Collections.singletonList("rel_id").equals(indexes.get("PRIMARY"))) {
            errors.add("Primary key must contain rel_id only");
        }

        if (!Collections.singletonList("parent_id").equals(indexes.get("uk_parent_id"))
                || !Boolean.TRUE.equals(unique.get("uk_parent_id"))) {
            errors.add("uk_parent_id must be UNIQUE(parent_id)");
        }
    }

    private void validateDataTableForeignKey(
            @NotNull Connection connection,
            @NotNull String tableName,
            @NotNull String constraintName,
            @NotNull List<String> errors
    ) throws SQLException {
        String sql =
                "SELECT COLUMN_NAME, k.REFERENCED_TABLE_NAME, " +
                        "REFERENCED_COLUMN_NAME, DELETE_RULE " +
                        "FROM information_schema.KEY_COLUMN_USAGE k " +
                        "JOIN information_schema.REFERENTIAL_CONSTRAINTS r " +
                        "ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA " +
                        "AND r.TABLE_NAME = k.TABLE_NAME " +
                        "AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME " +
                        "WHERE k.CONSTRAINT_SCHEMA = DATABASE() " +
                        "AND k.TABLE_NAME = ? AND k.CONSTRAINT_NAME = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    errors.add("Missing foreign key: " + constraintName);
                    return;
                }

                checkEquals(errors, "Foreign key column",
                        "parent_id", result.getString("COLUMN_NAME"));
                checkEquals(errors, "Foreign key referenced table",
                        "sm_parent", result.getString("REFERENCED_TABLE_NAME"));
                checkEquals(errors, "Foreign key referenced column",
                        "id", result.getString("REFERENCED_COLUMN_NAME"));
                checkEquals(errors, "Foreign key ON DELETE rule",
                        "CASCADE", result.getString("DELETE_RULE"));

                if (result.next()) {
                    errors.add("Foreign key has multiple column mappings: "
                            + constraintName);
                }
            }
        }
    }

    private static void checkRequiredColumn(
            List<String> errors,
            Set<String> columns,
            String name
    ) {
        if (!columns.contains(name)) {
            errors.add("Missing column: " + name);
        }
    }

    private static void checkEquals(
            List<String> errors,
            String property,
            String expected,
            String actual
    ) {
        if (actual == null
                || !expected.equalsIgnoreCase(actual.trim())) {
            errors.add(
                    property + " must be " + expected
                            + ", found: " + actual
            );
        }
    }


    private static String normalize(String value) {
        return value == null
                ? null
                : value.trim().toLowerCase();
    }

    /**
     * CF-01/CF-02 FIX: Returns relId counters.
     * <p>
     * Current implementation returns empty map because relId counters are not yet persisted to database.
     * This allows Core initialization to complete. Counters will be managed in-memory by EntryServiceCore
     * until full database persistence is implemented per CF-02.
     * </p>
     */
    @Override
    public Map<String, Long> getTypeRelIdCounters() {
        // CF-01/CF-02 FIX: Return empty map to allow initialization.
        // In-memory counters in EntryServiceCore will start from 1.
        // Full implementation requires a metadata table (see CF-02).
        return new HashMap<>();
    }

    /**
     * CF-02 FIX: Updates relId counter (no-op until database persistence implemented).
     * <p>
     * Current implementation is a no-op. EntryServiceCore manages counters in-memory.
     * Full implementation requires persisting to a metadata table per CF-02.
     * </p>
     */
    @Override
    public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
        // CF-02 FIX: No-op until database metadata table is implemented.
        // Counters are currently managed in-memory by EntryServiceCore.
    }

    /**
     * CF-02 FIX: Persists metadata (no-op until database persistence implemented).
     * <p>
     * Current implementation is a no-op. Full implementation requires a metadata table per CF-02.
     * </p>
     */
    @Override
    public void persistMetadata() throws SQLException {
        // CF-02 FIX: No-op until database metadata table is implemented.
        // Metadata is currently only persisted for binary file storage.
    }

    @Override
    public void insert(@NotNull String tableName, @NotNull UUID target, @Nullable String reason, @NotNull Date executingTime, @Nullable Date expiryTime, @Nullable JsonObject data) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(data == null ? INSERT : INSERT_EXTRA)) {
                statement.setBytes(1, uuidToBytes(target));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
                    pstmt.setTimestamp(3, expiryTime != null ? new java.sql.Timestamp(expiryTime.getTime()) : null);
                    pstmt.setTimestamp(4, new java.sql.Timestamp(executingTime.getTime()));
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
    public Optional<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> getEntryById(long id) throws SQLException {
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
    public List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> queryByTarget(@NotNull UUID target) throws SQLException {
        List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> results = new ArrayList<>();
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
    public List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> queryByTypeAndRelId(@NotNull String typeId, long relId) throws SQLException {
        List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> results = new ArrayList<>();
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
    public List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> queryAll() throws SQLException {
        List<io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted> results = new ArrayList<>();
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
    private io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted buildEntry(@NotNull ResultSet parentRs, @NotNull Connection connection) throws SQLException {
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
    private io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted resultSetToEntry(long id, @NotNull UUID target, @NotNull String typeId,
                                           @NotNull ResultSet rs) throws SQLException {
        long relId = rs.getLong("rel_id");
        UUID executor = bytesToUuid(rs.getBytes("executor"));
        java.sql.Timestamp expiryTs = rs.getTimestamp("expiry_time");
        java.sql.Timestamp executionTs = rs.getTimestamp("execution_time");
        int status = rs.getInt("status");
        String reason = rs.getString("reason");

        Date expiryTime = expiryTs != null ? new Date(expiryTs.getTime()) : null;
        Date executionTime = new Date(executionTs.getTime());

        JsonObject data = null;
        if (core.getTypeManager().getType(typeId).hasExtraData()) {
            String dataStr = rs.getString("extra_data");
            if (dataStr != null) {
                data = com.google.gson.JsonParser.parseString(dataStr).getAsJsonObject();
            }
        }

        boolean isWithdrawn = (status & 1) != 0;
        boolean isOverridden = (status & 2) != 0;

        return new io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted(
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

    @RequiredArgsConstructor
    private static class ColumnDefinition {
        private final String name, nullability;
        private final boolean autoIncrement;
    }
}