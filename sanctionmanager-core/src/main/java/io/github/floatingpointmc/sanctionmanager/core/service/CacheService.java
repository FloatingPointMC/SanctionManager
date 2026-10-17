package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.ICacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.MemoryCacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.RedisCacheService;
import org.jetbrains.annotations.NotNull;

public class CacheService {
    private final @NotNull SanctionManagerCore core;
    private final boolean isUsingCache;
    private final @NotNull ICacheService cacheService;

    public CacheService(@NotNull SanctionManagerCore core) {
        this.core = core;
        CacheConfig cacheConfig = core.getConfig().getCacheConfig();
        isUsingCache = cacheConfig.isEnabled();
        cacheService = cacheConfig.isUsingRedis() ? new RedisCacheService(cacheConfig.getRedisConfig()) : new MemoryCacheService();
    }
}
