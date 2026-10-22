package io.github.floatingpointmc.sanctionmanager.core.config.storage;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for the storage layer.
 * <p>
 * This configuration determines whether entries are persisted to a database or binary file format.
 * The configuration for the selected storage mode is always non-null. The other configuration may also be
 * set, in which case it is ignored.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class StorageConfig {
    /**
     * Whether database storage is enabled. Database storage is not implemented.
     */
    private final boolean usingDatabase;
    /**
     * Binary file configuration, non-null if {@code usingDatabase} is {@code false}.
     */
    private final @Nullable BinaryFileConfig binaryFileConfig;
    /**
     * Database configuration, non-null if {@code usingDatabase} is {@code true}.
     */
    private final @Nullable DatabaseConfig databaseConfig;

    public static @NotNull StorageConfig local(@NotNull BinaryFileConfig binaryFileConfig) {
        return new StorageConfig(false, binaryFileConfig, null);
    }

    public static @NotNull StorageConfig database(@NotNull DatabaseConfig databaseConfig) {
        return new StorageConfig(true, null, databaseConfig);
    }
}
