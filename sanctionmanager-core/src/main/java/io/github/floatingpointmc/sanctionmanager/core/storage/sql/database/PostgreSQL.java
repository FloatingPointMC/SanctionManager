package io.github.floatingpointmc.sanctionmanager.core.storage.sql.database;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class PostgreSQL implements IDatabase {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull HikariDataSource dataSource;

    @Override
    public void initialize() throws SQLException {
        throw new UnsupportedOperationException("PostgreSQL support not implemented yet.");
    }

    /**
     * CF-01 FIX: Returns empty map to allow graceful handling.
     */
    @Override
    public Map<String, Long> getTypeRelIdCounters() {
        return new HashMap<>();
    }

    /**
     * CF-02 FIX: No-op implementation.
     */
    @Override
    public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
        // No-op
    }

    /**
     * CF-02 FIX: No-op implementation.
     */
    @Override
    public void persistMetadata() throws SQLException {
        // No-op
    }

    @Override
    public void insert(@NotNull String tableName, @NotNull UUID target, @Nullable String reason, @NotNull Date executingTime, @Nullable Date expiryTime, @Nullable JsonObject data) {

    }
}
