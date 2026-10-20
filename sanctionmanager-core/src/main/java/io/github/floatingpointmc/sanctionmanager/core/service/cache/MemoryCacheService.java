package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.core.config.cache.MemoryCacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * In-memory bounded LRU cache service implementation.
 * <p>
 * Uses two bounded LRU caches:
 * </p>
 * <ul>
 *   <li><b>Entry cache</b>: Stores individual EntryPersisted objects with dual indexing (ID and type+relId)</li>
 *   <li><b>Query result cache</b>: Stores lists of entry IDs (not full objects) for complex queries</li>
 * </ul>
 * <p>
 * <b>Locking:</b> Mutating operations take the write lock and lookups take the read lock. Note that the
 * underlying maps use access order, so lookups also reorder them while only the read lock is held;
 * concurrent lookups are therefore not guaranteed to be safe.
 * </p>
 * <p>
 * <b>Eviction:</b> Both caches use LRU (Least Recently Used) eviction when capacity is reached.
 * Entry eviction automatically cleans up both the ID index and type+relId index.
 * </p>
 * <p>
 * <b>Query Result Validity:</b> A cached query result is valid only if all referenced entry IDs
 * can be resolved from the entry cache. If any entry has been evicted, the query result is
 * treated as a cache miss.
 * </p>
 */
public class MemoryCacheService implements ICacheService {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Entry cache: LRU map indexed by ID
    private final LinkedHashMap<Long, EntryPersisted> entryById;

    // Secondary index: type+relId -> ID mapping
    private final Map<String, Long> typeRelIdToId = new HashMap<>();

    // Query result cache: LRU map storing lists of entry IDs with generation
    private final LinkedHashMap<QueryKey, QueryResultEntry> queryResults;

    // Query cache generation counter - incremented on invalidateAllQueryResults to invalidate all cached results
    private long queryGeneration = 0;

    /**
     * Creates a bounded LRU memory cache with the capacities from the given configuration.
     *
     * @param memoryCacheConfig the configuration providing the maximum number of cached entries and
     *                          cached query results. Cannot be null.
     * @throws IllegalArgumentException if either capacity is not positive
     */
    public MemoryCacheService(@NotNull MemoryCacheConfig memoryCacheConfig) {
        int entryCapacity = memoryCacheConfig.getEntryCapacity();
        int queryCapacity = memoryCacheConfig.getQueryCapacity();
        if (entryCapacity <= 0) {
            throw new IllegalArgumentException("entryCapacity must be positive, got: " + entryCapacity);
        }
        if (queryCapacity <= 0) {
            throw new IllegalArgumentException("queryCapacity must be positive, got: " + queryCapacity);
        }

        // Create LRU cache for entries with automatic eviction
        this.entryById = new LinkedHashMap<Long, EntryPersisted>(entryCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, EntryPersisted> eldest) {
                if (size() > entryCapacity) {
                    // Clean up secondary index when entry is evicted
                    EntryPersisted evicted = eldest.getValue();
                    String typeRelIdKey = makeTypeRelIdKey(
                            evicted.getType().getIdentifier(),
                            evicted.getRelId()
                    );
                    typeRelIdToId.remove(typeRelIdKey);
                    return true;
                }
                return false;
            }
        };

        // Create LRU cache for query results
        this.queryResults = new LinkedHashMap<QueryKey, QueryResultEntry>(queryCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<QueryKey, QueryResultEntry> eldest) {
                return size() > queryCapacity;
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Optional<EntryPersisted> getById(long id) {
        // Use writeLock because entryById uses access-order, so get() modifies iteration order
        lock.writeLock().lock();
        try {
            return Optional.ofNullable(entryById.get(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        // Use writeLock because entryById uses access-order, so get() modifies iteration order
        lock.writeLock().lock();
        try {
            String key = makeTypeRelIdKey(typeId, relId);
            Long id = typeRelIdToId.get(key);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(entryById.get(id));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the cached query result along with the generation it was stored with.
     * If the generation doesn't match the current generation, treats it as a cache miss.
     * </p>
     */
    @Override
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        // Use writeLock because entryById uses access-order and we call get() on it
        lock.writeLock().lock();
        try {
            QueryResultEntry resultEntry = queryResults.get(key);
            if (resultEntry == null) {
                return Optional.empty();
            }

            // Check generation - if stale, treat as cache miss
            if (resultEntry.generation != queryGeneration) {
                queryResults.remove(key); // Clean up stale entry
                return Optional.empty();
            }

            // Resolve all entry IDs from the entry cache
            List<EntryPersisted> result = new ArrayList<>(resultEntry.entryIds.size());
            for (Long id : resultEntry.entryIds) {
                EntryPersisted entry = entryById.get(id);
                if (entry == null) {
                    // Entry was evicted, query result is invalid
                    return Optional.empty();
                }
                result.add(entry);
            }

            return Optional.of(result);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getQueryGeneration() {
        lock.readLock().lock();
        try {
            return queryGeneration;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void putQueryResultIfGenerationMatches(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries, long expectedGeneration) {
        lock.writeLock().lock();
        try {
            // Only cache if generation hasn't changed (no invalidation occurred during query)
            if (queryGeneration != expectedGeneration) {
                return;
            }

            // Store only entry IDs with current generation
            List<Long> ids = entries.stream()
                    .map(EntryPersisted::getId)
                    .collect(Collectors.toList());
            queryResults.put(key, new QueryResultEntry(ids, queryGeneration));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void put(@NotNull EntryPersisted entry) {
        lock.writeLock().lock();
        try {
            long id = entry.getId();
            String newTypeRelIdKey = makeTypeRelIdKey(entry.getType().getIdentifier(), entry.getRelId());

            // If replacing an existing entry with a different type/relId, remove the old mapping
            EntryPersisted oldEntry = entryById.get(id);
            if (oldEntry != null) {
                String oldTypeRelIdKey = makeTypeRelIdKey(oldEntry.getType().getIdentifier(), oldEntry.getRelId());
                if (!oldTypeRelIdKey.equals(newTypeRelIdKey)) {
                    typeRelIdToId.remove(oldTypeRelIdKey);
                }
            }

            // Update both indexes
            entryById.put(id, entry);
            typeRelIdToId.put(newTypeRelIdKey, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries) {
        lock.writeLock().lock();
        try {
            // Don't overwrite if key already exists with current generation
            // This prevents stale data from being cached after concurrent invalidation
            QueryResultEntry existing = queryResults.get(key);
            if (existing != null && existing.generation == queryGeneration) {
                // Already cached with current generation, don't overwrite
                return;
            }

            // Store only entry IDs with current generation
            List<Long> ids = entries.stream()
                    .map(EntryPersisted::getId)
                    .collect(Collectors.toList());
            queryResults.put(key, new QueryResultEntry(ids, queryGeneration));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateById(long id) {
        lock.writeLock().lock();
        try {
            EntryPersisted removed = entryById.remove(id);
            if (removed != null) {
                // Clean up secondary index
                String typeRelIdKey = makeTypeRelIdKey(
                        removed.getType().getIdentifier(),
                        removed.getRelId()
                );
                typeRelIdToId.remove(typeRelIdKey);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateByTypeAndRelId(@NotNull String typeId, long relId) {
        lock.writeLock().lock();
        try {
            String key = makeTypeRelIdKey(typeId, relId);
            Long id = typeRelIdToId.remove(key);
            if (id != null) {
                entryById.remove(id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateQueryResult(@NotNull QueryKey key) {
        lock.writeLock().lock();
        try {
            queryResults.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateAll() {
        lock.writeLock().lock();
        try {
            entryById.clear();
            typeRelIdToId.clear();
            // Increment generation instead of clearing - more efficient
            queryGeneration++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void invalidateAllQueryResults() {
        lock.writeLock().lock();
        try {
            // Increment generation to invalidate all cached query results
            // This is more efficient than clearing the map, as stale entries will be
            // lazily removed on next access
            queryGeneration++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String makeTypeRelIdKey(String typeId, long relId) {
        return typeId + ":" + relId;
    }

    /**
     * Wrapper for cached query results that includes generation tracking.
     * Results are only valid if their generation matches the current generation.
     */
    private static class QueryResultEntry {
        final List<Long> entryIds;
        final long generation;

        QueryResultEntry(List<Long> entryIds, long generation) {
            this.entryIds = entryIds;
            this.generation = generation;
        }
    }
}
