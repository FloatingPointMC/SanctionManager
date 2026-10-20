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

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper for cache service implementations.
 * <p>
 * Provides a unified interface for caching operations with automatic no-op behavior when caching is disabled.
 * Supports both entry-level caching and query result caching. Eviction behavior depends on the
 * selected implementation; the in-memory implementation uses bounded LRU eviction.
 * </p>
 */
public class CacheService {
    /**
     * Whether caching is enabled. When {@code false}, all operations are no-ops and lookups return empty.
     */
    @Getter
    private final boolean enabled;
    private final ICacheService cacheService;

    /**
     * Constructs a CacheService from the Core's cache configuration.
     * <p>
     * If caching is disabled, no backend is created. If Redis is selected, a {@link RedisCacheService} is
     * created; it connects on first use. Otherwise a {@link MemoryCacheService} is created.
     * </p>
     * <p>
     * With Redis, cache operations throw {@code PersistenceException} subclasses when Redis is unreachable or
     * returns invalid data; there is no fallback to the in-memory cache.
     * </p>
     *
     * @param core the Core instance whose configuration is read. Cannot be null.
     * @throws IllegalArgumentException if the memory cache is selected and its capacities are not positive, or if
     *                                  Redis is selected and its host or port is invalid
     */
    public CacheService(SanctionManagerCore core) {
        CacheConfig config = core.getConfig().getCacheConfig();
        this.enabled = config.isEnabled();

        if (!enabled) {
            this.cacheService = null;
        } else if (config.isUsingRedis()) {
            assert config.getRedisConfig() != null;
            this.cacheService = new RedisCacheService(config.getRedisConfig());
        } else {
            assert config.getMemoryCacheConfig() != null;
            this.cacheService = new MemoryCacheService(config.getMemoryCacheConfig());
        }
    }

    /**
     * Retrieves an entry by ID from cache.
     * <p>
     * Checks if the entry has expired since caching and treats expired entries as cache misses.
     * This ensures that consumers never receive stale expired entries from cache.
     * </p>
     *
     * @param id the entry ID
     * @return an Optional containing the cached entry, or empty if not cached, expired, or caching is disabled
     */
    public @NotNull Optional<EntryPersisted> getById(long id) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<EntryPersisted> cached = cacheService.getById(id);
        if (cached.isPresent() && isExpired(cached.get())) {
            // Entry expired since caching - treat as miss and invalidate
            cacheService.invalidateById(id);
            return Optional.empty();
        }
        return cached;
    }

    /**
     * Retrieves an entry by type and relId from cache.
     * <p>
     * Checks if the entry has expired since caching and treats expired entries as cache misses.
     * This ensures that consumers never receive stale expired entries from cache.
     * </p>
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     * @return an Optional containing the cached entry, or empty if not cached, expired, or caching is disabled
     */
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<EntryPersisted> cached = cacheService.getByTypeAndRelId(typeId, relId);
        if (cached.isPresent() && isExpired(cached.get())) {
            // Entry expired since caching - treat as miss and invalidate
            cacheService.invalidateByTypeAndRelId(typeId, relId);
            return Optional.empty();
        }
        return cached;
    }

    /**
     * Retrieves a cached query result.
     * <p>
     * If any entry in the cached result has expired since caching, treats the entire
     * query result as invalid and returns empty. This prevents returning query results
     * that contain stale expired entries.
     * </p>
     *
     * @param key the query key
     * @return an Optional containing the resolved result list, or empty if not cached, contains expired entries, or caching is disabled
     */
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<List<EntryPersisted>> cached = cacheService.getQueryResult(key);
        if (cached.isPresent()) {
            // Check if any entry in the result has expired
            for (EntryPersisted entry : cached.get()) {
                if (isExpired(entry)) {
                    // Query result contains expired entry - invalidate and treat as miss
                    cacheService.invalidateQueryResult(key);
                    return Optional.empty();
                }
            }
        }
        return cached;
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
     * Returns the current query result cache generation.
     * <p>
     * The generation is incremented each time {@link #invalidateAllQueryResults()} is called.
     * Callers should capture the generation before querying storage and pass it to
     * {@link #putQueryResultIfGenerationMatches} to prevent caching stale data after
     * concurrent invalidation.
     * </p>
     *
     * @return the current generation counter, or 0 if caching is disabled
     */
    public long getQueryGeneration() {
        if (!enabled) {
            return 0L;
        }
        return cacheService.getQueryGeneration();
    }

    /**
     * Stores a query result in the cache only if the generation hasn't changed.
     * <p>
     * This prevents stale data from being cached after a concurrent invalidation.
     * If the current generation doesn't match expectedGeneration, the put is silently skipped.
     * </p>
     *
     * @param key                the query key
     * @param entries            the query result entries
     * @param expectedGeneration the generation that was current when the query started
     */
    public void putQueryResultIfGenerationMatches(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries, long expectedGeneration) {
        if (enabled) {
            cacheService.putQueryResultIfGenerationMatches(key, entries, expectedGeneration);
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

    /**
     * Releases resources held by the cache backend, such as the Redis connection.
     * <p>
     * Cached data is kept. The service remains usable; a Redis backend reconnects on its next operation.
     * </p>
     */
    public void shutdown() {
        if (cacheService instanceof AutoCloseable) {
            try {
                ((AutoCloseable) cacheService).close();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close the cache backend", e);
            }
        }
    }

    /**
     * Checks if an entry has expired based on its expiryTime.
     * <p>
     * An entry is considered expired if it has a non-null expiryTime that is in the past.
     * </p>
     *
     * @param entry the entry to check
     * @return true if the entry has expired, false otherwise
     */
    private boolean isExpired(EntryPersisted entry) {
        Date expiryTime = entry.getExpiryTime();
        if (expiryTime == null) {
            return false;
        }
        return expiryTime.before(new Date());
    }
}
