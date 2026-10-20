package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;

@RequiredArgsConstructor
public class MySQL implements IDatabase {
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

            String engine;
            String collation;

            try (PreparedStatement statement =
                         connection.prepareStatement(CHECK_PARENT_EXISTS);
                 ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    try (Statement create = connection.createStatement()) {
                        create.executeUpdate(CREATE_PARENT);
                    }
                    return;
                }

                engine = result.getString("ENGINE");
                collation = result.getString("TABLE_COLLATION");
            }

            List<String> errors = new ArrayList<>();

            // Check storage engine.
            if (!"InnoDB".equalsIgnoreCase(engine)) {
                errors.add(
                        "Engine must be InnoDB, found: " + engine
                );
            }

            // Check default character set.
            if (collation == null) {
                errors.add("Table collation must not be NULL");
            } else {
                try (PreparedStatement statement =
                             connection.prepareStatement(CHECK_CHARSET)) {

                    statement.setString(1, collation);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            errors.add(
                                    "Cannot determine character set for collation: "
                                            + collation
                            );
                        } else {
                            String charset =
                                    result.getString("CHARACTER_SET_NAME");

                            if (!"utf8mb4".equalsIgnoreCase(charset)) {
                                errors.add(
                                        "Default character set must be utf8mb4, found: "
                                                + charset
                                );
                            }
                        }
                    }
                }
            }

            // Check columns.
            Set<String> foundColumns = new HashSet<>();

            try (PreparedStatement statement =
                         connection.prepareStatement(CHECK_COLUMNS);
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
                : value.trim().toLowerCase(Locale.ROOT);
    }
}