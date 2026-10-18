package io.github.floatingpointmc.sanctionmanager.core.config;

import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
@Getter
public final class CoreConfig {
    /** Storage configuration for persistence layer. */
    private final StorageConfig storageConfig;
    /** Cache configuration for runtime caching. */
    private final CacheConfig cacheConfig;
}
