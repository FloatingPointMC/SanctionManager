package io.github.floatingpointmc.sanctionmanager.core.config;

import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public final class CoreConfig {
    private final StorageConfig storageConfig;
    private final CacheConfig cacheConfig;
}
