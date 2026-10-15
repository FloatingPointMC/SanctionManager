package io.github.floatingpointmc.sanctionmanager.core.service.storage;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.storage.SanctionManagerDataSource;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class DatabaseService {
    private final @NotNull SanctionManagerCore core;
    private final @NotNull DatabaseConfig databaseConfig;

    public void initialize() {
        SanctionManagerDataSource dataSource = new SanctionManagerDataSource();
    }
}
