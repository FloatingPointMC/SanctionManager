package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for the storage layer.
 * <p>
 * This configuration determines whether entries are persisted to a database or binary file format.
 * Exactly one of {@code databaseConfig} or {@code binaryFileConfig} is non-null, depending on the storage mode.
 * </p>
 */
@Getter
public final class StorageConfig {
    /** Whether database storage is enabled. */
    private final boolean usingDatabase;
    /** Binary file configuration, non-null if {@code usingDatabase} is {@code false}. */
    private final @Nullable BinaryFileConfig binaryFileConfig;
    /** Database configuration, non-null if {@code usingDatabase} is {@code true}. */
    private final @Nullable DatabaseConfig databaseConfig;

    /**
     * Constructs a StorageConfig with validation.
     * <p>
     * If {@code usingDatabase} is {@code true}, {@code databaseConfig} must be non-null.
     * If {@code usingDatabase} is {@code false}, {@code binaryFileConfig} must be non-null.
     * </p>
     *
     * @param usingDatabase    whether to use database storage
     * @param binaryFileConfig binary file configuration (required if not using database)
     * @param databaseConfig   database configuration (required if using database)
     * @throws NullPointerException if the appropriate config is null for the chosen storage mode
     */
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
