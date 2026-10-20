package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.DatabaseType;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.SanctionManagerDataSource;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.IDatabase;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Placeholder storage service for a future database backend.
 * <p>
 * Database persistence is not implemented. Every operation except {@link #shutdown()} throws
 * {@code UnsupportedOperationException}; {@link #shutdown()} is a no-op. The constructor
 * (Lombok-generated) only stores the Core and configuration.
 * </p>
 */
@RequiredArgsConstructor
public class DatabaseStorageService implements IStorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull DatabaseConfig databaseConfig;
    private @Nullable HikariDataSource dataSource;
    private @Nullable IDatabase database;

    /**
     * Initializes the database storage implementation.
     * <p>
     * CF-03 FIX: Cleanup resources on any initialization failure.
     * </p>
     *
     * @throws Exception if initialization fails
     */
    @SneakyThrows
    @Override
    public void initialize() {
        HikariDataSource tempDataSource = null;
        try {
            SanctionManagerDataSource dataSource = new SanctionManagerDataSource(
                    databaseConfig.getJdbcUrl(),
                    databaseConfig.getCredentialProvider()
            );
            DatabaseType type = DatabaseType.parse(databaseConfig.getJdbcUrl());
            HikariConfig config = new HikariConfig();
            config.setDataSource(dataSource);
            config.setPoolName("SanctionManager-HikariCP-" + type);
            config.setMaximumPoolSize(databaseConfig.getMaximumPoolSize());
            tempDataSource = new HikariDataSource(config);
            Class<? extends IDatabase> iDatabase = type.getDatabaseClass();
            database = iDatabase.getDeclaredConstructor(SanctionManagerCore.class, HikariDataSource.class).newInstance(core, tempDataSource);
            database.initialize();
            // Only assign to field after successful initialization
            this.dataSource = tempDataSource;
        } catch (Exception e) {
            // CF-03 FIX: Close data source if initialization fails
            if (tempDataSource != null) {
                try {
                    tempDataSource.close();
                } catch (Exception closeEx) {
                    e.addSuppressed(closeEx);
                }
            }
            database = null;
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public long insertRecord(EntryPersisted entry) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public List<EntryPersisted> queryAll() throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void updateRecord(EntryPersisted entry) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Not implemented.
     * </p>
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void deleteRecord(long id) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    /**
     * {@inheritDoc}
     * <p>
     * No-op, since this implementation holds no resources.
     * </p>
     */
    @Override
    public void shutdown() throws IOException {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * CF-01 FIX: Delegates to database implementation.
     * </p>
     */
    @Override
    public java.util.Map<String, Long> getTypeRelIdCounters() {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        return database.getTypeRelIdCounters();
    }

    /**
     * {@inheritDoc}
     * <p>
     * CF-01 FIX: Delegates to database implementation.
     * </p>
     */
    @Override
    public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        database.updateTypeRelIdCounter(typeIdentifier, nextRelId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * CF-01 FIX: Delegates to database implementation.
     * </p>
     */
    @Override
    public void persistMetadata() throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            database.persistMetadata();
        } catch (SQLException e) {
            throw new IOException("Failed to persist metadata", e);
        }
    }

    @Override
    public String toString() {
        return "DatabaseService";
    }
}
