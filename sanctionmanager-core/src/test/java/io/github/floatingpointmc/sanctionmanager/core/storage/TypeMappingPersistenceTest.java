package io.github.floatingpointmc.sanctionmanager.core.storage;

import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.MemoryCacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.IStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CF-01 and CF-02: Type ID mapping and relId counter persistence.
 * <p>
 * Verifies that type IDs assigned during serialization remain stable across storage engine
 * restarts, and that per-type relId counters are correctly persisted and restored.
 * </p>
 */
public class TypeMappingPersistenceTest {
    @TempDir
    Path tempDir;

    private SanctionManagerCore core;
    private List<EntryType> types;

    @BeforeEach
    public void setUp() {
        types = new ArrayList<>();
        types.add(EntryType.of("ban", false));
        types.add(EntryType.of("kick", false));
        types.add(EntryType.of("mute", false));
    }

    @AfterEach
    public void tearDown() {
        if (core != null && core.isInitialized()) {
            core.shutdown();
        }
    }

    /**
     * CF-01: Type ID mapping must persist across storage engine restarts.
     * <p>
     * Without persistence, restarting with types in a different order causes
     * type ID reassignment, making existing records unreadable.
     * </p>
     */
    @Test
    public void testTypeIdMappingPersistence() throws IOException {
        // Create first core and insert entries
        core = createCore(tempDir, types);
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        long banId = insertEntry(core, "ban", target, executor);
        long kickId = insertEntry(core, "kick", target, executor);

        // Verify we can read them
        IStorageService storage = core.getStorageService().getStorageService();
        Optional<EntryPersisted> ban1 = storage.getRecord(banId);
        assertTrue(ban1.isPresent());
        assertEquals("ban", ban1.get().getType().getIdentifier());

        core.shutdown();

        // Restart with types in different order (simulates type registration order change)
        List<EntryType> reorderedTypes = new ArrayList<>();
        reorderedTypes.add(types.get(2)); // mute first
        reorderedTypes.add(types.get(0)); // ban second
        reorderedTypes.add(types.get(1)); // kick third

        core = createCore(tempDir, reorderedTypes);
        storage = core.getStorageService().getStorageService();

        // Must still read with correct types despite reordering
        Optional<EntryPersisted> ban2 = storage.getRecord(banId);
        assertTrue(ban2.isPresent(), "Ban entry should be readable after restart");
        assertEquals("ban", ban2.get().getType().getIdentifier(), "Type must remain 'ban'");

        Optional<EntryPersisted> kick2 = storage.getRecord(kickId);
        assertTrue(kick2.isPresent(), "Kick entry should be readable after restart");
        assertEquals("kick", kick2.get().getType().getIdentifier(), "Type must remain 'kick'");
    }

    /**
     * CF-02: Per-type relId counters must persist across service restarts.
     * <p>
     * Without persistence, relId sequences restart from 1 on each boot, causing
     * relId collisions and breaking type+relId uniqueness.
     * </p>
     */
    @Test
    public void testRelIdCounterPersistence() throws IOException {
        core = createCore(tempDir, types);
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Insert 3 ban entries - relIds should be 1, 2, 3
        long ban1Id = insertEntry(core, "ban", target, executor);
        long ban2Id = insertEntry(core, "ban", target, executor);
        long ban3Id = insertEntry(core, "ban", target, executor);

        IStorageService storage = core.getStorageService().getStorageService();
        EntryPersisted ban1 = storage.getRecord(ban1Id).get();
        EntryPersisted ban2 = storage.getRecord(ban2Id).get();
        EntryPersisted ban3 = storage.getRecord(ban3Id).get();

        assertEquals(1L, ban1.getRelId());
        assertEquals(2L, ban2.getRelId());
        assertEquals(3L, ban3.getRelId());

        core.shutdown();

        // Restart and insert another ban entry
        core = createCore(tempDir, types);
        storage = core.getStorageService().getStorageService();

        long ban4Id = insertEntry(core, "ban", target, executor);
        EntryPersisted ban4 = storage.getRecord(ban4Id).get();

        // relId must be 4, not 1 (which would collide)
        assertEquals(4L, ban4.getRelId(), "RelId counter must continue from persisted value");

        // Verify old entries still readable
        assertTrue(storage.getRecord(ban1Id).isPresent());
        assertTrue(storage.getRecord(ban2Id).isPresent());
        assertTrue(storage.getRecord(ban3Id).isPresent());
    }

    /**
     * CF-02: Multiple types maintain independent relId sequences.
     */
    @Test
    public void testMultipleTypeRelIdPersistence() throws IOException {
        core = createCore(tempDir, types);
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Insert interleaved entries
        long ban1Id = insertEntry(core, "ban", target, executor);
        long kick1Id = insertEntry(core, "kick", target, executor);
        long ban2Id = insertEntry(core, "ban", target, executor);
        long mute1Id = insertEntry(core, "mute", target, executor);

        IStorageService storage = core.getStorageService().getStorageService();
        assertEquals(1L, storage.getRecord(ban1Id).get().getRelId());
        assertEquals(1L, storage.getRecord(kick1Id).get().getRelId());
        assertEquals(2L, storage.getRecord(ban2Id).get().getRelId());
        assertEquals(1L, storage.getRecord(mute1Id).get().getRelId());

        core.shutdown();

        // Restart and insert more
        core = createCore(tempDir, types);
        storage = core.getStorageService().getStorageService();

        long kick2Id = insertEntry(core, "kick", target, executor);
        long ban3Id = insertEntry(core, "ban", target, executor);

        // Each type continues its own sequence
        assertEquals(2L, storage.getRecord(kick2Id).get().getRelId());
        assertEquals(3L, storage.getRecord(ban3Id).get().getRelId());
    }

    private SanctionManagerCore createCore(Path tempDir, List<EntryType> entryTypes) {
        BinaryFileConfig binaryConfig = new BinaryFileConfig(tempDir.resolve("test.db").toString(), 4096);
        StorageConfig storageConfig = new StorageConfig(false, binaryConfig, null);
        MemoryCacheConfig memoryCacheConfig = new MemoryCacheConfig(100, 50);
        CacheConfig cacheConfig = CacheConfig.memory(memoryCacheConfig);
        CoreConfig config = new CoreConfig(storageConfig, cacheConfig);

        SanctionManagerCore newCore = SanctionManagerCore.create(config);
        newCore.types(entryTypes.toArray(new EntryType[0]));
        newCore.initialize();
        return newCore;
    }

    private long insertEntry(SanctionManagerCore core, String typeId, UUID target, UUID executor) throws IOException {
        // Use the factory and service API to create and execute entries
        // Factory.create() returns Entry, but the implementation returns EntryWrapper
        io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper entry =
            (io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper)
                core.getEntryFactory().create(typeId, target, executor, "Test reason", null, null);
        core.getEntryService().execute(entry);

        return entry.getId();
    }
}
