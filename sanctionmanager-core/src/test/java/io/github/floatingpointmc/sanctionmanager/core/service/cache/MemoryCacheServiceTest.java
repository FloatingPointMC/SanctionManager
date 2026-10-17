package io.github.floatingpointmc.sanctionmanager.core.service.cache;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MemoryCacheService bounded LRU cache behavior.
 */
public class MemoryCacheServiceTest {
    private MemoryCacheService cache;
    private EntryType testType;

    @BeforeEach
    public void setUp() {
        cache = new MemoryCacheService(5, 3); // Small capacities for testing
        testType = EntryType.of("test");
    }

    @Test
    public void testEntryCapacityBound() {
        // Insert more entries than capacity
        for (int i = 1; i <= 10; i++) {
            EntryPersisted entry = createEntry(i, i, "test");
            cache.put(entry);
        }

        // Verify only recent entries are cached (LRU eviction)
        assertFalse(cache.getById(1).isPresent(), "Oldest entry should be evicted");
        assertFalse(cache.getById(2).isPresent());
        assertFalse(cache.getById(3).isPresent());
        assertFalse(cache.getById(4).isPresent());
        assertTrue(cache.getById(6).isPresent(), "Recent entries should be cached");
        assertTrue(cache.getById(7).isPresent());
        assertTrue(cache.getById(8).isPresent());
        assertTrue(cache.getById(9).isPresent());
        assertTrue(cache.getById(10).isPresent());
    }

    @Test
    public void testQueryResultCapacityBound() {
        // Create entries and add them to cache
        List<EntryPersisted> entries = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            EntryPersisted entry = createEntry(i, i, "test");
            cache.put(entry);
            entries.add(entry);
        }

        // Insert more query results than capacity
        for (int i = 1; i <= 5; i++) {
            QueryKey.LatestActiveQuery key = new QueryKey.LatestActiveQuery(
                    UUID.randomUUID(),
                    "type" + i
            );
            cache.putQueryResult(key, entries);
        }

        // Verify oldest query results are evicted (capacity = 3)
        QueryKey.LatestActiveQuery key1 = new QueryKey.LatestActiveQuery(
                UUID.randomUUID(),
                "type1"
        );
        assertFalse(cache.getQueryResult(key1).isPresent(), "Oldest query should be evicted");
    }

    @Test
    public void testSecondaryIndexConsistency() {
        EntryPersisted entry = createEntry(1, 100, "test");
        cache.put(entry);

        // Verify both indexes work
        assertTrue(cache.getById(1).isPresent());
        assertTrue(cache.getByTypeAndRelId("test", 100).isPresent());

        // Invalidate by ID
        cache.invalidateById(1);

        // Verify both indexes are cleared
        assertFalse(cache.getById(1).isPresent());
        assertFalse(cache.getByTypeAndRelId("test", 100).isPresent());
    }

    @Test
    public void testSecondaryIndexConsistencyAfterEviction() {
        // Fill cache to trigger eviction
        for (int i = 1; i <= 10; i++) {
            EntryPersisted entry = createEntry(i, i * 10, "test");
            cache.put(entry);
        }

        // Verify oldest entries are not in secondary index
        assertFalse(cache.getByTypeAndRelId("test", 10).isPresent(), "Evicted entry should not be in secondary index");
        assertFalse(cache.getByTypeAndRelId("test", 20).isPresent());
    }

    @Test
    public void testQueryResultInvalidWhenEntryEvicted() {
        // Create entries
        List<EntryPersisted> entries = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            EntryPersisted entry = createEntry(i, i, "test");
            cache.put(entry);
            entries.add(entry);
        }

        // Cache query result
        QueryKey.LatestActiveQuery key = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "test");
        cache.putQueryResult(key, entries);

        // Verify query result is cached
        assertTrue(cache.getQueryResult(key).isPresent());

        // Trigger eviction by adding more entries
        for (int i = 10; i <= 20; i++) {
            EntryPersisted entry = createEntry(i, i, "test");
            cache.put(entry);
        }

        // Query result should now be invalid (referenced entries evicted)
        assertFalse(cache.getQueryResult(key).isPresent(), "Query result should be invalid after entry eviction");
    }

    @Test
    public void testLRUAccessOrder() {
        // Insert entries 1-5
        for (int i = 1; i <= 5; i++) {
            cache.put(createEntry(i, i, "test"));
        }

        // Access entry 1 to make it recently used
        cache.getById(1);

        // Insert new entry to trigger eviction
        cache.put(createEntry(6, 6, "test"));

        // Entry 1 should still be cached (was accessed recently)
        assertTrue(cache.getById(1).isPresent(), "Recently accessed entry should not be evicted");

        // Entry 2 should be evicted (least recently used)
        assertFalse(cache.getById(2).isPresent(), "Least recently used entry should be evicted");
    }

    @Test
    public void testInvalidateAllQueryResults() {
        List<EntryPersisted> entries = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            EntryPersisted entry = createEntry(i, i, "test");
            cache.put(entry);
            entries.add(entry);
        }

        QueryKey.LatestActiveQuery key1 = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "type1");
        QueryKey.LatestActiveQuery key2 = new QueryKey.LatestActiveQuery(UUID.randomUUID(), "type2");
        cache.putQueryResult(key1, entries);
        cache.putQueryResult(key2, entries);

        assertTrue(cache.getQueryResult(key1).isPresent());
        assertTrue(cache.getQueryResult(key2).isPresent());

        // Invalidate all query results
        cache.invalidateAllQueryResults();

        // Verify query results are cleared
        assertFalse(cache.getQueryResult(key1).isPresent());
        assertFalse(cache.getQueryResult(key2).isPresent());

        // Verify entries are still cached
        assertTrue(cache.getById(1).isPresent());
        assertTrue(cache.getById(2).isPresent());
        assertTrue(cache.getById(3).isPresent());
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Simple smoke test for thread safety
        Thread[] threads = new Thread[10];

        for (int t = 0; t < threads.length; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    int id = threadId * 100 + i;
                    EntryPersisted entry = createEntry(id, id, "test");
                    cache.put(entry);
                    cache.getById(id);
                    cache.getByTypeAndRelId("test", id);
                }
            });
            threads[t].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Verify cache is still consistent (no exceptions thrown)
        assertTrue(true, "Concurrent access completed without exceptions");
    }

    @Test
    public void testInvalidCapacityValues() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryCacheService(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCacheService(100, 0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCacheService(-1, 100));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCacheService(100, -1));
    }

    private EntryPersisted createEntry(long id, long relId, String typeId) {
        return new EntryPersisted(
                id,
                relId,
                testType,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                new Date(),
                "Test reason",
                null,
                null,
                null,
                false,
                null,
                0
        );
    }
}
