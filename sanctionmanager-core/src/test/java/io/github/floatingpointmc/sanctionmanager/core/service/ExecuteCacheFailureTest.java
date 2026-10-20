package io.github.floatingpointmc.sanctionmanager.core.service;

import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.MemoryCacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.ICacheService;
import io.github.floatingpointmc.sanctionmanager.core.service.cache.QueryKey;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CF-03: Best-effort cache updates after storage commit.
 * <p>
 * Verifies that when cache operations fail after successful storage commit:
 * 1. The wrapper transitions to persisted state (not left as EntrySnapshot)
 * 2. Storage contains the committed record
 * 3. Retrying with the same wrapper object throws (already executed)
 * 4. No duplicate records are created
 * </p>
 */
public class ExecuteCacheFailureTest {
    private Path tempDir;
    private Path dataFile;
    private SanctionManagerCore core;
    private EntryServiceCore entryService;
    private EntryType testType;
    private FailingCacheWrapper failingCache;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("execute-cache-fail-test-");
        dataFile = tempDir.resolve("test.dat");

        List<EntryType> types = Collections.singletonList(EntryType.of("test"));
        testType = types.get(0);

        BinaryFileConfig binaryFileConfig = new BinaryFileConfig(dataFile.toString(), 4096);
        CacheConfig cacheConfig = CacheConfig.memory(new MemoryCacheConfig(100, 50));
        StorageConfig storageConfig = new StorageConfig(false, binaryFileConfig, null);
        CoreConfig coreConfig = new CoreConfig(storageConfig, cacheConfig);

        core = createCore(coreConfig, types);
        entryService = (EntryServiceCore) core.getEntryService();

        // Wrap the internal ICacheService with failing wrapper
        failingCache = new FailingCacheWrapper(getInternalCacheService());
        injectFailingCache();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (core != null && core.isInitialized()) {
            core.shutdown();
        }
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

    /**
     * CF-03: Cache put failure after storage commit should not leave wrapper as EntrySnapshot.
     */
    @Test
    public void testExecuteWithCachePutFailure() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = core.getEntryFactory().create(
                testType.getIdentifier(), target, executor, "Test", null, null);

        // Configure cache to fail on put
        failingCache.failOnPut = true;

        // Execute should succeed despite cache failure
        entryService.execute(entry);

        // Verify wrapper transitioned to persisted state (not EntrySnapshot)
        assertTrue(entry.getId() > 0, "Wrapper should have assigned ID");
        assertTrue(entry.getRelId() > 0, "Wrapper should have assigned relId");
        assertNotNull(entry.getExecutingTime(), "Wrapper should have executing time");

        // Disable cache failures before querying
        failingCache.failOnPut = false;

        // Verify storage contains the record
        Optional<Entry> stored = entryService.queryById(entry.getId());
        assertTrue(stored.isPresent(), "Storage should contain the committed record");
        assertEquals(entry.getId(), stored.get().getId());

        // Verify retrying with same wrapper throws (already executed)
        assertThrows(IllegalArgumentException.class, () -> entryService.execute(entry),
                "Retrying with same wrapper should throw 'already executed'");

        // Verify no duplicate records
        List<Entry> allEntries = entryService.queryAll(io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions.create());
        assertEquals(1, allEntries.size(), "Should have exactly 1 record, not duplicates");
    }

    /**
     * CF-03: Cache invalidate failure after storage commit should not leave wrapper as EntrySnapshot.
     */
    @Test
    public void testExecuteWithCacheInvalidateFailure() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = core.getEntryFactory().create(
                testType.getIdentifier(), target, executor, "Test", null, null);

        // Configure cache to fail on invalidate
        failingCache.failOnInvalidate = true;

        // Execute should succeed despite cache failure
        entryService.execute(entry);

        // Verify wrapper transitioned to persisted state
        assertTrue(entry.getId() > 0);
        assertTrue(entry.getRelId() > 0);

        // Disable cache failures before querying
        failingCache.failOnInvalidate = false;

        // Verify storage contains the record
        Optional<Entry> stored = entryService.queryById(entry.getId());
        assertTrue(stored.isPresent());

        // Verify no duplicates
        List<Entry> allEntries = entryService.queryAll(io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions.create());
        assertEquals(1, allEntries.size());
    }

    /**
     * CF-03: Both cache operations failing should still leave wrapper in persisted state.
     */
    @Test
    public void testExecuteWithBothCacheOperationsFailing() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = core.getEntryFactory().create(
                testType.getIdentifier(), target, executor, "Test", null, null);

        // Configure cache to fail on both operations
        failingCache.failOnPut = true;
        failingCache.failOnInvalidate = true;

        // Execute should succeed despite all cache failures
        entryService.execute(entry);

        // Verify wrapper transitioned to persisted state
        assertTrue(entry.getId() > 0);
        assertTrue(entry.getRelId() > 0);

        // Disable cache failures before querying
        failingCache.failOnPut = false;
        failingCache.failOnInvalidate = false;

        // Verify storage contains the record
        Optional<Entry> stored = entryService.queryById(entry.getId());
        assertTrue(stored.isPresent());
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

    private ICacheService getInternalCacheService() {
        try {
            java.lang.reflect.Field cacheServiceField = core.getCacheService().getClass().getDeclaredField("cacheService");
            cacheServiceField.setAccessible(true);
            return (ICacheService) cacheServiceField.get(core.getCacheService());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get internal cache service", e);
        }
    }

    private void injectFailingCache() {
        try {
            java.lang.reflect.Field cacheServiceField = core.getCacheService().getClass().getDeclaredField("cacheService");
            cacheServiceField.setAccessible(true);
            cacheServiceField.set(core.getCacheService(), failingCache);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject failing cache", e);
        }
    }

    /**
     * Cache wrapper that can be configured to fail on specific operations.
     */
    private static class FailingCacheWrapper implements ICacheService {
        private final ICacheService delegate;
        boolean failOnPut = false;
        boolean failOnInvalidate = false;

        FailingCacheWrapper(ICacheService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void put(@NonNull EntryPersisted entry) {
            if (failOnPut) {
                throw new RuntimeException("Simulated cache put failure");
            }
            delegate.put(entry);
        }

        @Override
        public @NonNull Optional<EntryPersisted> getById(long id) {
            return delegate.getById(id);
        }

        @Override
        public @NonNull Optional<EntryPersisted> getByTypeAndRelId(@NonNull String typeId, long relId) {
            return delegate.getByTypeAndRelId(typeId, relId);
        }

        @Override
        public @NonNull Optional<List<EntryPersisted>> getQueryResult(@NonNull QueryKey key) {
            return delegate.getQueryResult(key);
        }

        @Override
        public long getQueryGeneration() {
            return delegate.getQueryGeneration();
        }

        @Override
        public void putQueryResultIfGenerationMatches(@NonNull QueryKey key, @NonNull List<EntryPersisted> entries, long expectedGeneration) {
            if (failOnPut) {
                throw new RuntimeException("Simulated cache put failure");
            }
            delegate.putQueryResultIfGenerationMatches(key, entries, expectedGeneration);
        }

        @Override
        public void putQueryResult(@NonNull QueryKey key, @NonNull List<EntryPersisted> entries) {
            delegate.putQueryResult(key, entries);
        }

        @Override
        public void invalidateById(long id) {
            if (failOnInvalidate) {
                throw new RuntimeException("Simulated cache invalidate failure");
            }
            delegate.invalidateById(id);
        }

        @Override
        public void invalidateByTypeAndRelId(@NonNull String typeId, long relId) {
            if (failOnInvalidate) {
                throw new RuntimeException("Simulated cache invalidate failure");
            }
            delegate.invalidateByTypeAndRelId(typeId, relId);
        }

        @Override
        public void invalidateQueryResult(@NonNull QueryKey key) {
            if (failOnInvalidate) {
                throw new RuntimeException("Simulated cache invalidate failure");
            }
            delegate.invalidateQueryResult(key);
        }

        @Override
        public void invalidateAll() {
            if (failOnInvalidate) {
                throw new RuntimeException("Simulated cache invalidate failure");
            }
            delegate.invalidateAll();
        }

        @Override
        public void invalidateAllQueryResults() {
            if (failOnInvalidate) {
                throw new RuntimeException("Simulated cache invalidate failure");
            }
            delegate.invalidateAllQueryResults();
        }
    }
}

