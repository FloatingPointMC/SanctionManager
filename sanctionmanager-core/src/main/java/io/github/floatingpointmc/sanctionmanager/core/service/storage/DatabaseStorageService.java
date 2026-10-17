package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage service implementation using a database backend.
 * <p>
 * Initializes and manages database persistence for entries via JDBC connections.
 * </p>
 */
@RequiredArgsConstructor
public class DatabaseStorageService implements IStorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull DatabaseConfig databaseConfig;

    /**
     * Initializes the database storage implementation.
     * <p>
     * Database support is not yet implemented. This method throws {@code UnsupportedOperationException}.
     * </p>
     *
     * @throws UnsupportedOperationException always, as database support is not implemented
     */
    @Override
    public void initialize() {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public long insertRecord(EntryPersisted entry) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public Optional<EntryPersisted> getRecord(long id) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public List<EntryPersisted> queryAll() throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public void updateRecord(EntryPersisted entry) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public void deleteRecord(long id) throws IOException {
        throw new UnsupportedOperationException("Database storage is not yet implemented");
    }

    @Override
    public void shutdown() throws IOException {
        // No-op for unimplemented database storage
    }

    @Override
    public String toString() {
        return "DatabaseService";
    }
}
