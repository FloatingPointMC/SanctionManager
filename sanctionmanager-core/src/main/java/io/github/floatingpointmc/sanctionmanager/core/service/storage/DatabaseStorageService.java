package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.storage.SanctionManagerDataSource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

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
    public String toString() {
        return "DatabaseService";
    }
}
