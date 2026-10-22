package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.BinaryFileStorageService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.DatabaseStorageService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Facade for the persistence storage layer.
 * <p>
 * Delegates to either {@link DatabaseStorageService} or {@link BinaryFileStorageService} based on configuration.
 * Handles initialization of the selected storage implementation. The database backend is not implemented.
 * </p>
 */
@Slf4j
public class StorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull IStorageService storageService;

    /**
     * Constructs a StorageService that selects the appropriate backend based on configuration.
     *
     * @param core the Core instance. Cannot be null.
     * @throws IllegalStateException if the Core is already initialized
     */
    public StorageService(@NotNull SanctionManagerCore core) {
        if (core.isInitialized()) {
            throw new IllegalStateException("SanctionManagerCore is already initialized");
        }
        this.core = core;
        StorageConfig config = core.getConfig().getStorageConfig();
        if (config.isUsingDatabase()) {
            assert config.getDatabaseConfig() != null;
            storageService = new DatabaseStorageService(core, config.getDatabaseConfig());
        } else {
            assert config.getBinaryFileConfig() != null;
            storageService = new BinaryFileStorageService(core, config.getBinaryFileConfig());
        }
    }

    /**
     * Initializes the selected storage implementation.
     * <p>
     * Must be called during Core initialization to set up the persistence layer.
     * </p>
     *
     * @throws UnsupportedOperationException if the database backend is selected (not implemented)
     * @throws IllegalArgumentException      if the binary file configuration is invalid
     * @throws RuntimeException              if the storage backend fails to open
     */
    public void initialize() {
        log.info("Initializing storage({})", storageService);
        storageService.initialize();
    }

    /**
     * Gets the underlying storage service implementation.
     *
     * @return the storage service instance
     */
    public @NotNull IStorageService getStorageService() {
        return storageService;
    }

    /**
     * Shuts down the storage service and releases resources.
     *
     * @throws java.io.IOException if the shutdown operation fails
     */
    public void shutdown() throws IOException {
        log.info("Shutting down storage({})", storageService);
        storageService.shutdown();
    }
}
