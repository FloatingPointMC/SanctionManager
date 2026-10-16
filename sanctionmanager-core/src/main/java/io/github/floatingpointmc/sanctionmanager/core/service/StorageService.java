package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.BinaryFileService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.DatabaseService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Facade for the persistence storage layer.
 * <p>
 * Delegates to either {@code DatabaseService} or {@code BinaryFileService} based on configuration.
 * Handles initialization of the selected storage implementation.
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
     */
    public StorageService(@NotNull SanctionManagerCore core) {
        this.core = core;
        StorageConfig config = core.getConfig().getStorageConfig();
        if (config.isUsingDatabase()) {
            assert config.getDatabaseConfig() != null;
            storageService = new DatabaseService(core, config.getDatabaseConfig());
        } else {
            assert config.getBinaryFileConfig() != null;
            storageService = new BinaryFileService(core, config.getBinaryFileConfig());
        }
    }

    /**
     * Initializes the selected storage implementation.
     * <p>
     * Must be called during Core initialization to set up the persistence layer.
     * </p>
     */
    public void initialize() {
        log.info("Initializing storage({})", storageService);
        storageService.initialize();
    }
}
