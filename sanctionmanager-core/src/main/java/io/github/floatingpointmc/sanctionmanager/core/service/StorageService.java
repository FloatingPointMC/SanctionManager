package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.DatabaseService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class StorageService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull StorageConfig config;
    private final @Nullable File file;
    private final @Nullable DatabaseService databaseService;

    public StorageService(@NotNull SanctionManagerCore core, @NotNull StorageConfig config) {
        this.core = core;
        this.config = config;
        if (config.isUsingDatabase()) {
            file = null;
            assert config.getDatabaseConfig() != null;
            databaseService = new DatabaseService(core, config.getDatabaseConfig());
        } else {
            assert config.getFilePath() != null;
            file = new File(config.getFilePath());
            databaseService = null;
        }
    }

    public void initialize() {
        if (config.isUsingDatabase()) {
            databaseService.initialize();
        }
    }
}
