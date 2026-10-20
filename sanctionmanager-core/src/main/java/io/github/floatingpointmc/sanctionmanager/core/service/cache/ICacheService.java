package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Interface for cache service implementations.
 * <p>
 * Provides two-tier caching:
 * </p>
 * <ul>
 *   <li><b>Entry cache</b>: Stores individual EntryPersisted objects indexed by ID and (type, relId)</li>
 *   <li><b>Query result cache</b>: Stores lists of entry IDs for complex queries</li>
 * </ul>
 * <p>
 * Eviction and bounding are implementation-dependent; {@link MemoryCacheService} uses bounded LRU caches,
 * while {@link RedisCacheService} relies on the Redis server's eviction policy. Query results are valid only if all
 * referenced entries can be resolved from the entry cache.
 * </p>
 */
public interface ICacheService {
    /**
     * Retrieves an entry from cache by its ID.
     *
     * @param id the entry ID
     * @return an Optional containing the cached entry if found, or empty if not cached
     */
    @NotNull Optional<EntryPersisted> getById(long id);

    /**
     * Retrieves an entry from cache by type and related ID.
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     * @return an Optional containing the cached entry if found, or empty if not cached
     */
    @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId);

    /**
     * Retrieves a cached query result.
     * <p>
     * Returns empty if the query is not cached or if any referenced entry has been evicted.
     * The returned list contains resolved EntryPersisted objects, not just IDs.
     * </p>
     *
     * @param key the query key
     * @return an Optional containing the resolved result list, or empty if not fully cached
     */
    @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key);

    /**
     * Returns the current query result cache generation.
     * <p>
     * The generation is incremented each time {@link #invalidateAllQueryResults()} is called.
     * Callers should capture the generation before querying storage and pass it to
     * {@link #putQueryResultIfGenerationMatches} to prevent caching stale data after
     * concurrent invalidation.
     * </p>
     *
     * @return the current generation counter
     */
    long getQueryGeneration();

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
    void putQueryResultIfGenerationMatches(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries, long expectedGeneration);

    /**
     * Stores an entry in the cache.
     * <p>
     * The entry is indexed by both ID and (type, relId) for dual-key lookup.
     * May evict the least recently used entry if capacity is reached.
     * </p>
     *
     * @param entry the entry to cache
     */
    void put(@NotNull EntryPersisted entry);

    /**
     * Stores a query result in the cache.
     * <p>
     * Stores entry IDs rather than duplicating full entry objects.
     * May evict the least recently used query result if capacity is reached.
     * </p>
     *
     * @param key     the query key
     * @param entries the query result entries
     */
    void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries);

    /**
     * Removes an entry from cache by its ID.
     * <p>
     * Also removes the entry from the (type, relId) index.
     * </p>
     *
     * @param id the entry ID
     */
    void invalidateById(long id);

    /**
     * Removes an entry from cache by type and related ID.
     * <p>
     * Also removes the entry from the ID index.
     * </p>
     *
     * @param typeId the type identifier
     * @param relId  the related ID
     */
    void invalidateByTypeAndRelId(@NotNull String typeId, long relId);

    /**
     * Invalidates a specific query result.
     *
     * @param key the query key
     */
    void invalidateQueryResult(@NotNull QueryKey key);

    /**
     * Clears all cached entries and query results.
     */
    void invalidateAll();

    /**
     * Clears all cached query results but preserves individual entries.
     * <p>
     * Use this after mutations to invalidate potentially stale query results
     * without evicting individual entry data.
     * </p>
     */
    void invalidateAllQueryResults();
}
