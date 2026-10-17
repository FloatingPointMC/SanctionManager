package io.github.floatingpointmc.sanctionmanager.core.storage.engine;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StorageEngine covering basic CRUD operations and persistence.
 */
public class StorageEngineTest {
    private Path tempDir;
    private Path dataFile;
    private StorageEngine engine;
    private TypeManager typeManager;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("storage-test-");
        dataFile = tempDir.resolve("test.dat");

        // Create TypeManager with test types
        typeManager = new TypeManager(Arrays.asList(
            EntryType.of("ban"),
            EntryType.of("mute"),
            EntryType.of("kick")
        ));

        // Initialize engine
        engine = new StorageEngine(dataFile, 4096, 100, typeManager);
        engine.initialize();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (engine != null) {
            engine.shutdown();
        }
        // Cleanup temp files
        if (Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
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
    public void testCreateAndRetrieveRecord() throws IOException {
        // Create a record
        EntryType type = typeManager.getType("ban");
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();

        EntryPersisted entry = new EntryPersisted(
            0, 1, type, target, executor, null, now, "Test ban",
            new JsonObject(), null, null, false, null, 0
        );

        // Insert
        long id = engine.insertRecord(entry);
        assertTrue(id > 0);

        // Retrieve
        Optional<EntryPersisted> retrieved = engine.getRecord(id);
        assertTrue(retrieved.isPresent());

        EntryPersisted record = retrieved.get();
        assertEquals(id, record.getId());
        assertEquals(1, record.getRelId());
        assertEquals(type.getIdentifier(), record.getType().getIdentifier());
        assertEquals(target, record.getTarget());
        assertEquals(executor, record.getExecutor());
    }

    @Test
    public void testMultipleRecords() throws IOException {
        EntryType type = typeManager.getType("ban");
        UUID executor = UUID.randomUUID();
        Date now = new Date();

        // Insert multiple records
        long id1 = engine.insertRecord(new EntryPersisted(
            0, 1, type, UUID.randomUUID(), executor, null, now, "Ban 1",
            new JsonObject(), null, null, false, null, 0
        ));

        long id2 = engine.insertRecord(new EntryPersisted(
            0, 2, type, UUID.randomUUID(), executor, null, now, "Ban 2",
            new JsonObject(), null, null, false, null, 0
        ));

        // Verify both exist
        assertTrue(engine.getRecord(id1).isPresent());
        assertTrue(engine.getRecord(id2).isPresent());
        assertNotEquals(id1, id2);
    }

    @Test
    public void testQueryByTarget() throws IOException {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();
        EntryType type = typeManager.getType("ban");

        // Insert records for same target
        engine.insertRecord(new EntryPersisted(
            0, 1, type, target, executor, null, now, "Ban 1",
            new JsonObject(), null, null, false, null, 0
        ));

        engine.insertRecord(new EntryPersisted(
            0, 2, type, target, executor, null, now, "Ban 2",
            new JsonObject(), null, null, false, null, 0
        ));

        // Query by target
        java.util.List<EntryPersisted> results = engine.queryByTarget(target);
        assertEquals(2, results.size());
    }

    @Test
    public void testRecordWithdrawal() throws IOException {
        EntryType type = typeManager.getType("ban");
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UUID withdrawnBy = UUID.randomUUID();
        Date now = new Date();

        // Insert record
        long id = engine.insertRecord(new EntryPersisted(
            0, 1, type, target, executor, null, now, "Ban",
            new JsonObject(), null, null, false, null, 0
        ));

        // Update with withdrawal
        Optional<EntryPersisted> optEntry = engine.getRecord(id);
        assertTrue(optEntry.isPresent());
        EntryPersisted entry = optEntry.get();
        entry.setWithdrawnBy(withdrawnBy);
        entry.setWithdrawalReason("Withdrawn for testing");
        engine.updateRecord(entry);

        // Verify update
        Optional<EntryPersisted> optUpdated = engine.getRecord(id);
        assertTrue(optUpdated.isPresent());
        EntryPersisted updated = optUpdated.get();
        assertTrue(updated.isWithdrawn());
        assertEquals(withdrawnBy, updated.getWithdrawnBy());
        assertEquals("Withdrawn for testing", updated.getWithdrawalReason());
    }

    @Test
    public void testRecordDeletion() throws IOException {
        EntryType type = typeManager.getType("ban");
        long id = engine.insertRecord(new EntryPersisted(
            0, 1, type, UUID.randomUUID(), UUID.randomUUID(), null, new Date(), "Ban",
            new JsonObject(), null, null, false, null, 0
        ));

        assertTrue(engine.getRecord(id).isPresent());

        // Delete
        engine.deleteRecord(id);

        // Verify deletion
        assertFalse(engine.getRecord(id).isPresent());
    }

    @Test
    public void testSuperblockPersistence() throws IOException {
        // Check initial state
        Superblock sb = engine.getSuperblock();
        assertEquals(1, sb.filePageCount);
        assertEquals(1, sb.nextEntryId);

        // Insert a record to increment ID
        engine.insertRecord(new EntryPersisted(
            0, 1, typeManager.getType("ban"), UUID.randomUUID(), UUID.randomUUID(),
            null, new Date(), "Ban", new JsonObject(), null, null, false, null, 0
        ));

        // Superblock should be updated
        Superblock sb2 = engine.getSuperblock();
        assertEquals(2, sb2.nextEntryId);
    }
}

