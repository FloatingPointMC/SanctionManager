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
public class DatabaseService implements IStorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull DatabaseConfig databaseConfig;

    /**
     * Initializes the database storage implementation.
     * <p>
     * Sets up database connectivity and prepares persistence infrastructure.
     * </p>
     */
    @Override
    public void initialize() {
        SanctionManagerDataSource dataSource = new SanctionManagerDataSource();
    }

    @Override
    public String toString() {
        return "DatabaseService";
    }
}
