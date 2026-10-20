package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.MemoryCacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests verifying cache-first behavior in EntryServiceCore.
 * <p>
 * Uses a custom instrumented storage wrapper to count storage access calls.
 * </p>
 */
public class EntryServiceCoreCacheTest {
    private Path tempDir;
    private Path dataFile;
    private SanctionManagerCore core;
    private EntryServiceCore entryService;
    private EntryFactoryCore entryFactory;
    private StorageAccessCounter storageCounter;
    private EntryType testType;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cache-test-");
        dataFile = tempDir.resolve("test.dat");

        List<EntryType> types = Collections.singletonList(EntryType.of("test"));
        testType = types.get(0);

        // Create configuration with caching ENABLED and small capacities
        BinaryFileConfig binaryFileConfig = new BinaryFileConfig(dataFile.toString(), 4096);
        CacheConfig cacheConfig = CacheConfig.memory(new MemoryCacheConfig(100, 50));
        StorageConfig storageConfig = new StorageConfig(false, binaryFileConfig, null);
        CoreConfig coreConfig = new CoreConfig(storageConfig, cacheConfig);

        // Create core
        core = createCore(coreConfig, types);
        entryService = (EntryServiceCore) core.getEntryService();
        entryFactory = (EntryFactoryCore) core.getEntryFactory();

        // Wrap storage with counter
        storageCounter = new StorageAccessCounter(core.getStorageService().getStorageService());
        injectCountingStorage();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });

        }
    }

    @Test
    public void testQueryByIdCacheHit() {
        // Create and execute entry
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);

        // Reset counter BEFORE execute to exclude execute's storage operations
        storageCounter.reset();

        entryService.execute(entry);
        long id = entry.getId();

        // Execute() stores the entry and caches it, so we expect 1 insertRecord call
        assertEquals(1, storageCounter.insertRecordCalls.get(), "Execute should store entry");

        // Clear the entry cache to test cache-first behavior on fresh lookup
        core.getCacheService().invalidateById(id);

        // Reset counter for query test
        storageCounter.reset();

        // First query - cache miss (we just invalidated), should access storage
        Optional<Entry> result1 = entryService.queryById(id);
        assertTrue(result1.isPresent());
        assertEquals(1, storageCounter.getRecordCalls.get(), "First query should access storage");

        // Second query - cache hit, should NOT access storage
        storageCounter.reset();
        Optional<Entry> result2 = entryService.queryById(id);
        assertTrue(result2.isPresent());
        assertEquals(0, storageCounter.getRecordCalls.get(), "Second query should use cache, not storage");
    }

    @Test
    public void testQueryByTypeAndRelIdCacheHit() {
        // Create and execute entry
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);
        entryService.execute(entry);

        String type = entry.getType().getIdentifier();
        long relId = entry.getRelId();

        // Clear the entry cache to test cache-first behavior
        core.getCacheService().invalidateByTypeAndRelId(type, relId);

        // Reset counter
        storageCounter.reset();

        // First query - cache miss
        Optional<Entry> result1 = entryService.queryByTypeAndRelId(type, relId);
        assertTrue(result1.isPresent());
        assertEquals(1, storageCounter.queryByTypeAndRelIdCalls.get(), "First query should access storage");

        // Second query - cache hit
        storageCounter.reset();
        Optional<Entry> result2 = entryService.queryByTypeAndRelId(type, relId);
        assertTrue(result2.isPresent());
        assertEquals(0, storageCounter.queryByTypeAndRelIdCalls.get(), "Second query should use cache");
    }

    @Test
    public void testQueryByTargetCacheHit() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);
        entryService.execute(entry);

        QueryOptions options = QueryOptions.create();

        // Reset counter
        storageCounter.reset();

        // First query - cache miss
        List<Entry> result1 = entryService.query(target, options);
        assertEquals(1, result1.size());
        assertEquals(1, storageCounter.queryByTargetCalls.get(), "First query should access storage");

        // Second query - cache hit
        storageCounter.reset();
        List<Entry> result2 = entryService.query(target, options);
        assertEquals(1, result2.size());
        assertEquals(0, storageCounter.queryByTargetCalls.get(), "Second query should use cache");
    }

    @Test
    public void testQueryAllCacheHit() {
        UUID executor = UUID.randomUUID();
        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), UUID.randomUUID(), executor, "Test", null);
        entryService.execute(entry);

        QueryOptions options = QueryOptions.create();

        // Reset counter
        storageCounter.reset();

        // First query - cache miss
        List<Entry> result1 = entryService.queryAll(options);
        assertEquals(1, result1.size());
        assertEquals(1, storageCounter.queryAllCalls.get(), "First query should access storage");

        // Second query - cache hit
        storageCounter.reset();
        List<Entry> result2 = entryService.queryAll(options);
        assertEquals(1, result2.size());
        assertEquals(0, storageCounter.queryAllCalls.get(), "Second query should use cache");
    }

    @Test
    public void testQueryLatestActiveCacheHit() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);
        entryService.execute(entry);

        // Reset counter
        storageCounter.reset();

        // First query - cache miss
        Optional<Entry> result1 = entryService.queryLatestActive(target, testType.getIdentifier());
        assertTrue(result1.isPresent());
        assertEquals(1, storageCounter.queryByTargetCalls.get(), "First query should access storage");

        // Second query - cache hit
        storageCounter.reset();
        Optional<Entry> result2 = entryService.queryLatestActive(target, testType.getIdentifier());
        assertTrue(result2.isPresent());
        assertEquals(0, storageCounter.queryByTargetCalls.get(), "Second query should use cache");
    }

    @Test
    public void testMutationInvalidatesQueryResults() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);
        entryService.execute(entry);

        QueryOptions options = QueryOptions.create().includeWithdrawn(true);

        // Query and cache the result
        List<Entry> result1 = entryService.query(target, options);
        assertEquals(1, result1.size());

        // Reset counter - now the query result should be cached
        storageCounter.reset();

        // Query again to verify it's cached
        List<Entry> cachedResult = entryService.query(target, options);
        assertEquals(1, cachedResult.size());
        assertEquals(0, storageCounter.queryByTargetCalls.get(), "Query result should be cached");

        // Perform mutation (withdraw)
        entryService.withdraw(entry, UUID.randomUUID(), "Withdrawn");

        // Reset counter after mutation
        storageCounter.reset();

        // Next query should be a cache miss (query result invalidated)
        List<Entry> result2 = entryService.query(target, options);
        assertEquals(1, result2.size());
        assertEquals(1, storageCounter.queryByTargetCalls.get(), "Query after mutation should access storage");
    }

    @Test
    public void testEmptyResultCaching() {
        UUID target = UUID.randomUUID();
        QueryOptions options = QueryOptions.create();

        // Reset counter
        storageCounter.reset();

        // First query - no entries exist
        List<Entry> result1 = entryService.query(target, options);
        assertTrue(result1.isEmpty());
        assertEquals(1, storageCounter.queryByTargetCalls.get(), "First query should access storage");

        // Second query - should use cached empty result
        storageCounter.reset();
        List<Entry> result2 = entryService.query(target, options);
        assertTrue(result2.isEmpty());
        assertEquals(0, storageCounter.queryByTargetCalls.get(), "Cached empty result should not access storage");
    }

    @Test
    public void testDifferentQueryParametersUseDifferentKeys() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(testType.getIdentifier(), target, executor, "Test", null);
        entryService.execute(entry);

        // Withdraw the entry
        entryService.withdraw(entry, UUID.randomUUID(), "Withdrawn");

        // Reset counter
        storageCounter.reset();

        // Query without withdrawn
        QueryOptions activeOnly = QueryOptions.create();
        List<Entry> result1 = entryService.query(target, activeOnly);
        assertTrue(result1.isEmpty());
        assertEquals(1, storageCounter.queryByTargetCalls.get());

        // Reset counter
        storageCounter.reset();

        // Query including withdrawn - different key, should miss cache
        QueryOptions includeWithdrawn = QueryOptions.create().includeWithdrawn(true);
        List<Entry> result2 = entryService.query(target, includeWithdrawn);
        assertEquals(1, result2.size());
        assertEquals(1, storageCounter.queryByTargetCalls.get(), "Different query params should miss cache");
    }

    private SanctionManagerCore createCore(CoreConfig config, List<EntryType> types) {
        try {
            java.lang.reflect.Constructor<SanctionManagerCore> constructor =
                    SanctionManagerCore.class.getDeclaredConstructor(CoreConfig.class);
            constructor.setAccessible(true);
            SanctionManagerCore core = constructor.newInstance(config);

            core.types(types.toArray(new EntryType[0]));

            java.lang.reflect.Field typeManagerField = SanctionManagerCore.class.getDeclaredField("typeManager");
            typeManagerField.setAccessible(true);
            typeManagerField.set(core, new TypeManager(types));

            core.initialize();

            return core;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SanctionManagerCore", e);
        }
    }

    private void injectCountingStorage() {
        try {
            StorageService storageService = core.getStorageService();
            java.lang.reflect.Field storageField = StorageService.class.getDeclaredField("storageService");
            storageField.setAccessible(true);
            storageField.set(storageService, storageCounter);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject counting storage", e);
        }
    }

    /**
     * Wrapper that counts storage access calls.
     */
    private static class StorageAccessCounter implements IStorageService {
        private final IStorageService delegate;
        final AtomicInteger getRecordCalls = new AtomicInteger(0);
        final AtomicInteger queryByTargetCalls = new AtomicInteger(0);
        final AtomicInteger queryByTypeAndRelIdCalls = new AtomicInteger(0);
        final AtomicInteger queryAllCalls = new AtomicInteger(0);
        final AtomicInteger insertRecordCalls = new AtomicInteger(0);
        final AtomicInteger updateRecordCalls = new AtomicInteger(0);
        final AtomicInteger deleteRecordCalls = new AtomicInteger(0);

        StorageAccessCounter(IStorageService delegate) {
            this.delegate = delegate;
        }

        void reset() {
            getRecordCalls.set(0);
            queryByTargetCalls.set(0);
            queryByTypeAndRelIdCalls.set(0);
            queryAllCalls.set(0);
            insertRecordCalls.set(0);
            updateRecordCalls.set(0);
            deleteRecordCalls.set(0);
        }

        @Override
        public void initialize() {
            delegate.initialize();
        }

        @Override
        public long insertRecord(EntryPersisted entry) throws IOException {
            insertRecordCalls.incrementAndGet();
            return delegate.insertRecord(entry);
        }

        @Override
        public Optional<EntryPersisted> getRecord(long id) throws IOException {
            getRecordCalls.incrementAndGet();
            return delegate.getRecord(id);
        }

        @Override
        public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
            queryByTargetCalls.incrementAndGet();
            return delegate.queryByTarget(target);
        }

        @Override
        public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
            queryByTypeAndRelIdCalls.incrementAndGet();
            return delegate.queryByTypeAndRelId(typeId, relId);
        }

        @Override
        public List<EntryPersisted> queryAll() throws IOException {
            queryAllCalls.incrementAndGet();
            return delegate.queryAll();
        }

        @Override
        public void updateRecord(EntryPersisted entry) throws IOException {
            updateRecordCalls.incrementAndGet();
            delegate.updateRecord(entry);
        }

        @Override
        public void deleteRecord(long id) throws IOException {
            deleteRecordCalls.incrementAndGet();
            delegate.deleteRecord(id);
        }

        @Override
        public void shutdown() throws IOException {
            delegate.shutdown();
        }

        @Override
        public java.util.Map<String, Long> getTypeRelIdCounters() {
            return delegate.getTypeRelIdCounters();
        }

        @Override
        public void updateTypeRelIdCounter(String typeIdentifier, long nextRelId) {
            delegate.updateTypeRelIdCounter(typeIdentifier, nextRelId);
        }

        @Override
        public void persistMetadata() throws IOException {
            delegate.persistMetadata();
        }
    }
}
