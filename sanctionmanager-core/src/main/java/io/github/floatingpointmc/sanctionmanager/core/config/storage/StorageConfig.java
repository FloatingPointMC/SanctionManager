package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public final class StorageConfig {
    private final boolean usingDatabase;
    private final @Nullable BinaryFileConfig binaryFileConfig;
    private final @Nullable DatabaseConfig databaseConfig;

    public StorageConfig(boolean usingDatabase, @Nullable BinaryFileConfig binaryFileConfig, @Nullable DatabaseConfig databaseConfig) {
        if (usingDatabase) {
            if (databaseConfig == null) {
                throw new NullPointerException("databaseConfig cannot be null while using database");
            }
        } else if (binaryFileConfig == null) {
            throw new NullPointerException("binaryFileConfig cannot be null while not using database");
        }
        this.usingDatabase = usingDatabase;
        this.binaryFileConfig = binaryFileConfig;
        this.databaseConfig = databaseConfig;
    }
}
