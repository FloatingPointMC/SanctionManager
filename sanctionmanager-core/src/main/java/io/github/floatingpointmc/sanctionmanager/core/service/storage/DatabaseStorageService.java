package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.DatabaseType;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.SanctionManagerDataSource;
import io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.IDatabase;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
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
     */
    @Override
    public @NotNull Addition insertRecord(@NotNull EntrySnapshot entry, @NotNull Date executingTime, @Nullable Date expiryTime) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            database.insert("sm_data_" + entry.getType(), entry.getTarget(), entry.getExecutor(), executingTime, expiryTime, entry.getDataInternal());
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).insertEntry(
                        entry.getType().getIdentifier(), entry.getTarget(),
                        entry.getExecutor(), entry.getReason(), executingTime, expiryTime, entry.getDataInternal());
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).insert(
                        entry.getType().getIdentifier(), entry.getTarget(),
                        entry.getExecutor(), entry.getReason(), executingTime, expiryTime, entry.getDataInternal());
            } else {
                throw new IOException("Unsupported database implementation: " + database.getClass().getName());
            }
        } catch (SQLException e) {
            throw new IOException("Failed to insert record", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).getEntryById(id);
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).getEntryById(id);
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to retrieve record", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).queryByTarget(target);
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).queryByTarget(target);
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to query by target", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).queryByTypeAndRelId(typeId, relId);
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).queryByTypeAndRelId(typeId, relId);
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to query by type and relId", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EntryPersisted> queryAll() throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).queryAll();
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                return ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).queryAll();
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to query all records", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateRecord(EntryPersisted entry) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).updateEntry(
                        entry.getType().getIdentifier(), entry.getId(),
                        entry.getWithdrawnBy(), entry.getWithdrawalReason(),
                        entry.isOverridden(), entry.getOverriddenBy());
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).updateEntry(
                        entry.getType().getIdentifier(), entry.getId(),
                        entry.getWithdrawnBy(), entry.getWithdrawalReason(),
                        entry.isOverridden(), entry.getOverriddenBy());
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to update record", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteRecord(long id) throws IOException {
        if (database == null) {
            throw new IllegalStateException("Database not initialized");
        }
        try {
            if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) {
                ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.SQLite) database).deleteEntry(id);
            } else if (database instanceof io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) {
                ((io.github.floatingpointmc.sanctionmanager.core.storage.sql.database.MySQL) database).deleteEntry(id);
            } else {
                throw new IOException("Unsupported database implementation");
            }
        } catch (SQLException e) {
            throw new IOException("Failed to delete record", e);
        }
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
