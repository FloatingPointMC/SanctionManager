package io.github.floatingpointmc.sanctionmanager.core.storage.sql;

import io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.IDatabase;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum DatabaseType {
    MYSQL("MySQL", MySQL.class),
    MARIADB("MariaDB", MySQL.class),
    POSTGRESQL("PostgreSQL", MySQL.class);

    private final @NotNull String name;
    @Getter
    private final @NotNull Class<? extends IDatabase> databaseClass;

    public static @NotNull DatabaseType parse(@NotNull String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:")) {
            throw new IllegalArgumentException("jdbcUrl must start with 'jdbc:'");
        }
        jdbcUrl = jdbcUrl.substring(jdbcUrl.indexOf(':') + 1, jdbcUrl.indexOf(':', 6)).toLowerCase();
        switch (jdbcUrl) {
            case "mysql":
                return MYSQL;
            case "mariadb":
                return MARIADB;
            case "postgresql":
                return POSTGRESQL;
            default:
                throw new IllegalArgumentException("Unknown database type: " + jdbcUrl);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}