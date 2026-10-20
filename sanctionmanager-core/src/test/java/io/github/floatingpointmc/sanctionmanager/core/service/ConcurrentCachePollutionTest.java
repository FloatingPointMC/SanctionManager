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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CF-04: Concurrent cache pollution prevention.
 * <p>
 * Verifies that the double-check pattern prevents stale data from being published to cache
 * after a concurrent modification has invalidated the cache.
 * </p>
 */
public class ConcurrentCachePollutionTest {
    private Path tempDir;
    private Path dataFile;
    private SanctionManagerCore core;
    private EntryServiceCore entryService;
    private EntryType testType;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("cache-pollution-test-");
        dataFile = tempDir.resolve("test.dat");

        List<EntryType> types = Collections.singletonList(EntryType.of("test"));
        testType = types.get(0);

        BinaryFileConfig binaryFileConfig = new BinaryFileConfig(dataFile.toString(), 4096);
        CacheConfig cacheConfig = CacheConfig.memory(new MemoryCacheConfig(100, 50));
        StorageConfig storageConfig = new StorageConfig(false, binaryFileConfig, null);
        CoreConfig coreConfig = new CoreConfig(storageConfig, cacheConfig);

        core = createCore(coreConfig, types);
        entryService = (EntryServiceCore) core.getEntryService();

        // Wrap storage with intercepting wrapper to control read timing
        LatchedStorageWrapper latchedStorage = new LatchedStorageWrapper(core.getStorageService().getStorageService());
        injectLatchedStorage(latchedStorage);
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
     * CF-04: Verify that queryById double-check prevents stale cache pollution after concurrent withdrawal.
     */
    @Test
    public void testQueryByIdRaceWithWithdrawal() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create and execute entry
        EntryWrapper entry = (EntryWrapper) core.getEntryFactory().create(
                testType.getIdentifier(), target, executor, "Test", null, null);
        entryService.execute(entry);
        long entryId = entry.getId();

        // Invalidate cache to force storage read
        core.getCacheService().invalidateById(entryId);

        // Get the latched storage wrapper
        LatchedStorageWrapper latchedStorage = getLatchedStorage();

        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch writerComplete = new CountDownLatch(1);
        AtomicReference<Entry> readerResult = new AtomicReference<>();
        AtomicReference<Throwable> readerError = new AtomicReference<>();

        // Configure storage to pause after reading, before returning
        latchedStorage.setGetRecordLatch(readerStarted, writerComplete);

        // Thread 1: Query (will read old non-withdrawn state)
        Thread readerThread = new Thread(() -> {
            try {
                Optional<Entry> result = entryService.queryById(entryId);
                readerResult.set(result.orElse(null));
            } catch (Throwable t) {
                readerError.set(t);
            }
        });

        readerThread.start();

        // Wait for reader to complete storage read
        assertTrue(readerStarted.await(5, TimeUnit.SECONDS), "Reader should start");

        // Thread 2: Withdraw and invalidate cache (completes while reader is paused)
        entryService.withdraw(entry, UUID.randomUUID(), "Withdrawn during race");

        // Verify cache now has the WITHDRAWN entry (withdraw puts it back)
        Optional<EntryPersisted> cachedWithdrawn = core.getCacheService().getById(entryId);
        assertTrue(cachedWithdrawn.isPresent(), "Cache should contain withdrawn entry after withdraw()");
        assertTrue(cachedWithdrawn.get().isWithdrawn(), "Cached entry should be withdrawn");

        // Release reader to complete
        writerComplete.countDown();

        // Wait for reader to complete
        readerThread.join(5000);
        assertFalse(readerThread.isAlive(), "Reader thread should complete");

        // Verify reader didn't fail
        assertNull(readerError.get(), "Reader should not throw");
        assertNotNull(readerResult.get(), "Reader should get result");

        // Critical assertion: Cache should STILL have the withdrawn entry
        // Without double-check, reader would have overwritten it with old non-withdrawn state
        Optional<EntryPersisted> cachedAfterRace = core.getCacheService().getById(entryId);
        assertTrue(cachedAfterRace.isPresent(), "Cache should still contain entry");
        assertTrue(cachedAfterRace.get().isWithdrawn(),
                "Cache should still have withdrawn state - double-check should have prevented stale overwrite");

        // Verify subsequent query gets current (withdrawn) state from cache
        Optional<Entry> freshQuery = entryService.queryById(entryId);
        assertTrue(freshQuery.isPresent());
        assertTrue(freshQuery.get().isWithdrawn(), "Fresh query should see withdrawn state from cache");
    }

    /**
     * CF-04: Verify that query list double-check prevents stale query result after concurrent withdrawal.
     */
    @Test
    public void testQueryListRaceWithWithdrawal() throws Exception {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create and execute entry
        EntryWrapper entry = (EntryWrapper) core.getEntryFactory().create(
                testType.getIdentifier(), target, executor, "Test", null, null);
        entryService.execute(entry);

        // Invalidate query cache to force storage read
        core.getCacheService().invalidateAllQueryResults();

        // Get the latched storage wrapper
        LatchedStorageWrapper latchedStorage = getLatchedStorage();

        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch writerComplete = new CountDownLatch(1);
        AtomicReference<List<Entry>> readerResult = new AtomicReference<>();
        AtomicReference<Throwable> readerError = new AtomicReference<>();

        // Configure storage to pause after reading, before returning
        latchedStorage.setQueryByTargetLatch(readerStarted, writerComplete);

        // Thread 1: Query (will read old non-withdrawn state)
        Thread readerThread = new Thread(() -> {
            try {
                List<Entry> result = entryService.query(target, QueryOptions.create());
                readerResult.set(result);
            } catch (Throwable t) {
                readerError.set(t);
            }
        });

        readerThread.start();

        // Wait for reader to complete storage read
        assertTrue(readerStarted.await(5, TimeUnit.SECONDS), "Reader should start");

        // Thread 2: Withdraw and invalidate query cache (completes while reader is paused)
        entryService.withdraw(entry, UUID.randomUUID(), "Withdrawn during race");

        // Note: withdraw() invalidates ALL query results, so query cache should be empty now

        // Release reader to complete
        writerComplete.countDown();

        // Wait for reader to complete
        readerThread.join(5000);
        assertFalse(readerThread.isAlive(), "Reader thread should complete");

        // Verify reader didn't fail
        assertNull(readerError.get(), "Reader should not throw");
        assertNotNull(readerResult.get(), "Reader should get result");

        // Reader got old data (non-withdrawn) from storage before withdraw happened
        // But it should NOT have polluted the cache with it due to double-check

        // Verify subsequent query gets current (empty, no active entries) state
        // This query will hit storage since query cache was invalidated by withdraw
        List<Entry> freshQuery = entryService.query(target, QueryOptions.create());
        assertTrue(freshQuery.isEmpty(),
                "Fresh query should return empty - entry is withdrawn and default options exclude withdrawn entries");
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

    private void injectLatchedStorage(LatchedStorageWrapper latchedStorage) {
        try {
            StorageService storageService = core.getStorageService();
            java.lang.reflect.Field storageField = StorageService.class.getDeclaredField("storageService");
            storageField.setAccessible(true);
            storageField.set(storageService, latchedStorage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject latched storage", e);
        }
    }

    private LatchedStorageWrapper getLatchedStorage() {
        try {
            StorageService storageService = core.getStorageService();
            java.lang.reflect.Field storageField = StorageService.class.getDeclaredField("storageService");
            storageField.setAccessible(true);
            return (LatchedStorageWrapper) storageField.get(storageService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get latched storage", e);
        }
    }

    /**
     * Storage wrapper that can pause operations at key points using latches to control thread interleaving.
     */
    private static class LatchedStorageWrapper implements IStorageService {
        private final IStorageService delegate;
        private CountDownLatch getRecordStartLatch;
        private CountDownLatch getRecordCompleteLatch;
        private CountDownLatch queryByTargetStartLatch;
        private CountDownLatch queryByTargetCompleteLatch;

        LatchedStorageWrapper(IStorageService delegate) {
            this.delegate = delegate;
        }

        void setGetRecordLatch(CountDownLatch startLatch, CountDownLatch completeLatch) {
            this.getRecordStartLatch = startLatch;
            this.getRecordCompleteLatch = completeLatch;
        }

        void setQueryByTargetLatch(CountDownLatch startLatch, CountDownLatch completeLatch) {
            this.queryByTargetStartLatch = startLatch;
            this.queryByTargetCompleteLatch = completeLatch;
        }

        @Override
        public void initialize() {
            delegate.initialize();
        }

        @Override
        public long insertRecord(EntryPersisted entry) throws IOException {
            return delegate.insertRecord(entry);
        }

        @Override
        public Optional<EntryPersisted> getRecord(long id) throws IOException {
            Optional<EntryPersisted> result = delegate.getRecord(id);

            // Pause after read if latches configured
            if (getRecordStartLatch != null && getRecordCompleteLatch != null) {
                getRecordStartLatch.countDown();
                try {
                    getRecordCompleteLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }

            return result;
        }

        @Override
        public List<EntryPersisted> queryByTarget(UUID target) throws IOException {
            List<EntryPersisted> result = delegate.queryByTarget(target);

            // Pause after read if latches configured
            if (queryByTargetStartLatch != null && queryByTargetCompleteLatch != null) {
                queryByTargetStartLatch.countDown();
                try {
                    queryByTargetCompleteLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }

            return result;
        }

        @Override
        public List<EntryPersisted> queryByTypeAndRelId(String typeId, long relId) throws IOException {
            return delegate.queryByTypeAndRelId(typeId, relId);
        }

        @Override
        public List<EntryPersisted> queryAll() throws IOException {
            return delegate.queryAll();
        }

        @Override
        public void updateRecord(EntryPersisted entry) throws IOException {
            delegate.updateRecord(entry);
        }

        @Override
        public void deleteRecord(long id) throws IOException {
            delegate.deleteRecord(id);
        }

        @Override
        public void shutdown() throws IOException {
            delegate.shutdown();
        }

        @Override
        public Map<String, Long> getTypeRelIdCounters() {
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

