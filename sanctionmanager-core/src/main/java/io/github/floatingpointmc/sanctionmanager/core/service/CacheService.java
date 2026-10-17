package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.ICacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.MemoryCacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.QueryKey;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.RedisCacheService;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Wrapper for cache service implementations.
 * <p>
 * Provides a unified interface for caching operations with automatic no-op behavior when caching is disabled.
 * Supports both entry-level caching and query result caching with bounded LRU eviction.
 * </p>
 */
public class CacheService {
    /**
     *  Checks if caching is enabled.
     */
    @Getter
    private final boolean enabled;
    private final ICacheService cacheService;

    public CacheService(SanctionManagerCore core) {
        CacheConfig config = core.getConfig().getCacheConfig();
        this.enabled = config.isEnabled();

        if (!enabled) {
            this.cacheService = null;
        } else if (config.isUsingRedis()) {
            this.cacheService = new RedisCacheService(config.getRedisConfig());
        } else {
            this.cacheService = new MemoryCacheService(
                    config.getEntryCapacity(),
                    config.getQueryCapacity()
            );
        }
    }

    /**
     * Retrieves an entry by ID from cache.
     *
     * @param id the entry ID
     * @return an Optional containing the cached entry, or empty if not cached or caching is disabled
     */
    public @NotNull Optional<EntryPersisted> getById(long id) {
        if (!enabled) {
            return Optional.empty();
        }
        return cacheService.getById(id);
    }

    /**
     * Retrieves an entry by type and relId from cache.
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     * @return an Optional containing the cached entry, or empty if not cached or caching is disabled
     */
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        if (!enabled) {
            return Optional.empty();
        }
        return cacheService.getByTypeAndRelId(typeId, relId);
    }

    /**
     * Retrieves a cached query result.
     *
     * @param key the query key
     * @return an Optional containing the resolved result list, or empty if not cached or caching is disabled
     */
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        if (!enabled) {
            return Optional.empty();
        }
        return cacheService.getQueryResult(key);
    }

    /**
     * Stores an entry in the cache.
     *
     * @param entry the entry to cache
     */
    public void put(@NotNull EntryPersisted entry) {
        if (enabled) {
            cacheService.put(entry);
        }
    }

    /**
     * Stores a query result in the cache.
     *
     * @param key     the query key
     * @param entries the query result entries
     */
    public void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries) {
        if (enabled) {
            cacheService.putQueryResult(key, entries);
        }
    }

    /**
     * Invalidates an entry by ID.
     *
     * @param id the entry ID
     */
    public void invalidateById(long id) {
        if (enabled) {
            cacheService.invalidateById(id);
        }
    }

    /**
     * Invalidates an entry by type and relId.
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     */
    public void invalidateByTypeAndRelId(@NotNull String typeId, long relId) {
        if (enabled) {
            cacheService.invalidateByTypeAndRelId(typeId, relId);
        }
    }

    /**
     * Invalidates a specific query result.
     *
     * @param key the query key
     */
    public void invalidateQueryResult(@NotNull QueryKey key) {
        if (enabled) {
            cacheService.invalidateQueryResult(key);
        }
    }

    /**
     * Clears all cached entries and query results.
     */
    public void invalidateAll() {
        if (enabled) {
            cacheService.invalidateAll();
        }
    }

    /**
     * Clears all cached query results but preserves individual entries.
     * <p>
     * This is the recommended invalidation strategy after mutations to ensure query results
     * don't become stale while still preserving individual entry cache data.
     * </p>
     */
    public void invalidateAllQueryResults() {
        if (enabled) {
            cacheService.invalidateAllQueryResults();
        }
    }
}
