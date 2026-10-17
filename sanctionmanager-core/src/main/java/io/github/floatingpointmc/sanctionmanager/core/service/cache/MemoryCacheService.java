package io.github.floatingpointmc.sanctionmanager.core.service.cache;

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
 * <ul>
 *   <li><b>Entry cache</b>: Stores individual EntryPersisted objects with dual indexing (ID and type+relId)</li>
 *   <li><b>Query result cache</b>: Stores lists of entry IDs (not full objects) for complex queries</li>
 * </ul>
 * </p>
 * <p>
 * <b>Thread Safety:</b> All operations use read-write locks for safe concurrent access.
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
    private final int entryCapacity;
    private final int queryCapacity;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // Entry cache: LRU map indexed by ID
    private final LinkedHashMap<Long, EntryPersisted> entryById;

    // Secondary index: type+relId -> ID mapping
    private final Map<String, Long> typeRelIdToId = new HashMap<>();

    // Query result cache: LRU map storing lists of entry IDs
    private final LinkedHashMap<QueryKey, List<Long>> queryResults;

    /**
     * Creates a bounded LRU memory cache with specified capacities.
     *
     * @param entryCapacity maximum number of entries to cache
     * @param queryCapacity maximum number of query results to cache
     */
    public MemoryCacheService(int entryCapacity, int queryCapacity) {
        if (entryCapacity <= 0) {
            throw new IllegalArgumentException("entryCapacity must be positive, got: " + entryCapacity);
        }
        if (queryCapacity <= 0) {
            throw new IllegalArgumentException("queryCapacity must be positive, got: " + queryCapacity);
        }

        this.entryCapacity = entryCapacity;
        this.queryCapacity = queryCapacity;

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
        this.queryResults = new LinkedHashMap<QueryKey, List<Long>>(queryCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<QueryKey, List<Long>> eldest) {
                return size() > queryCapacity;
            }
        };
    }

    @Override
    public @NotNull Optional<EntryPersisted> getById(long id) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(entryById.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Optional<EntryPersisted> getByTypeAndRelId(@NotNull String typeId, long relId) {
        lock.readLock().lock();
        try {
            String key = makeTypeRelIdKey(typeId, relId);
            Long id = typeRelIdToId.get(key);
            if (id == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(entryById.get(id));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @NotNull Optional<List<EntryPersisted>> getQueryResult(@NotNull QueryKey key) {
        lock.readLock().lock();
        try {
            List<Long> ids = queryResults.get(key);
            if (ids == null) {
                return Optional.empty();
            }

            // Resolve all entry IDs from the entry cache
            List<EntryPersisted> result = new ArrayList<>(ids.size());
            for (Long id : ids) {
                EntryPersisted entry = entryById.get(id);
                if (entry == null) {
                    // Entry was evicted, query result is invalid
                    return Optional.empty();
                }
                result.add(entry);
            }

            return Optional.of(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void put(@NotNull EntryPersisted entry) {
        lock.writeLock().lock();
        try {
            long id = entry.getId();
            String typeRelIdKey = makeTypeRelIdKey(entry.getType().getIdentifier(), entry.getRelId());

            // Update both indexes
            entryById.put(id, entry);
            typeRelIdToId.put(typeRelIdKey, id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void putQueryResult(@NotNull QueryKey key, @NotNull List<EntryPersisted> entries) {
        lock.writeLock().lock();
        try {
            // Store only entry IDs, not full objects
            List<Long> ids = entries.stream()
                    .map(EntryPersisted::getId)
                    .collect(Collectors.toList());
            queryResults.put(key, ids);
        } finally {
            lock.writeLock().unlock();
        }
    }

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

    @Override
    public void invalidateQueryResult(@NotNull QueryKey key) {
        lock.writeLock().lock();
        try {
            queryResults.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAll() {
        lock.writeLock().lock();
        try {
            entryById.clear();
            typeRelIdToId.clear();
            queryResults.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void invalidateAllQueryResults() {
        lock.writeLock().lock();
        try {
            queryResults.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private String makeTypeRelIdKey(String typeId, long relId) {
        return typeId + ":" + relId;
    }
}
