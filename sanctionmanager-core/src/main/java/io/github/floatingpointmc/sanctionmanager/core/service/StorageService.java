package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.BinaryFileService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.DatabaseService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class StorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull IStorageService storageService;

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

    public void initialize() {
        log.info("Initializing storage({})", storageService);
        storageService.initialize();
    }
}
