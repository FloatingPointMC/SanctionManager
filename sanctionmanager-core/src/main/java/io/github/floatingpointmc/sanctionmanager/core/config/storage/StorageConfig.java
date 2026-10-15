package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

@Getter
public final class StorageConfig {
    private final boolean usingDatabase;
    private final @Nullable String filePath;
    private final @Nullable DatabaseConfig databaseConfig;

    public StorageConfig(boolean usingDatabase, @Nullable String filePath, @Nullable DatabaseConfig databaseConfig) {
        if (usingDatabase) {
            if (databaseConfig == null) {
                throw new NullPointerException("databaseConfig cannot be null while using database");
            }
        } else if (filePath == null) {
            throw new NullPointerException("filePath cannot be null while not using database");
        }
        this.usingDatabase = usingDatabase;
        this.filePath = filePath;
        this.databaseConfig = databaseConfig;
    }
}
