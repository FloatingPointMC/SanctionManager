package io.github.floatingpointmc.sanctionmanager.core.config;

import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Main configuration container for the SanctionManager Core.
 * <p>
 * Holds storage configuration and cache configuration that determine how entries are persisted
 * and cached at runtime.
 * </p>
 * <p>
 * The constructor (Lombok-generated, taking {@code storageConfig} and {@code cacheConfig} in that order)
 * performs no validation; both values are expected to be non-null.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class CoreConfig {
    /** Storage configuration for persistence layer. */
    private final @NotNull StorageConfig storageConfig;
    /** Cache configuration for runtime caching. */
    private final @NotNull CacheConfig cacheConfig;

    public static @NotNull CoreConfig of(@NotNull StorageConfig storageConfig) {
        return new CoreConfig(storageConfig, CacheConfig.disabled());
    }

    public static @NotNull CoreConfig of(@NotNull StorageConfig storageConfig, @NotNull CacheConfig cacheConfig) {
        return new CoreConfig(storageConfig, cacheConfig);
    }
}
