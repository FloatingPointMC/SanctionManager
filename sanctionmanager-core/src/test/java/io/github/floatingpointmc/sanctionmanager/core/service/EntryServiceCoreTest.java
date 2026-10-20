package io.github.floatingpointmc.sanctionmanager.core.service;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryWrapper;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.model.QueryOptions;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.BinaryFileConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.core.factory.EntryFactoryCore;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EntryServiceCore covering all implemented TODOs.
 * <p>
 * This test uses an actual BinaryFileStorage backend to verify that EntryServiceCore
 * correctly implements the business logic for queries, mutations, and cache coordination.
 * </p>
 */
public class EntryServiceCoreTest {
    private Path tempDir;
    private Path dataFile;
    private EntryServiceCore entryService;
    private EntryFactoryCore entryFactory;
    private TypeManager typeManager;
    private EntryType banType;
    private EntryType muteType;
    private EntryType kickType;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("entry-service-test-");
        dataFile = tempDir.resolve("test.dat");

        // Create test types with appropriate data requirements
        List<EntryType> types = Arrays.asList(
                EntryType.of("ban"), // No extra data
                EntryType.of("mute"), // No extra data
                EntryType.of("kick", true) // With extra data for testing data updates
        );
        typeManager = new TypeManager(types);
        banType = typeManager.getType("ban");
        muteType = typeManager.getType("mute");
        kickType = typeManager.getType("kick");

        // Create configuration
        BinaryFileConfig binaryFileConfig = new BinaryFileConfig(dataFile.toString(), 4096);
        CacheConfig cacheConfig = CacheConfig.disabled(); // Caching disabled for now
        StorageConfig storageConfig = new StorageConfig(false, binaryFileConfig, null);
        CoreConfig coreConfig = new CoreConfig(storageConfig, cacheConfig);

        // Create core using reflection (since constructor is private)
        SanctionManagerCore core = createCore(coreConfig, types);
        entryService = (EntryServiceCore) core.getEntryService();
        entryFactory = (EntryFactoryCore) core.getEntryFactory();
    }

    /**
     * Creates a SanctionManagerCore using reflection.
     */
    private SanctionManagerCore createCore(CoreConfig config, List<EntryType> types) {
        try {
            java.lang.reflect.Constructor<SanctionManagerCore> constructor =
                    SanctionManagerCore.class.getDeclaredConstructor(CoreConfig.class);
            constructor.setAccessible(true);
            SanctionManagerCore core = constructor.newInstance(config);

            // Set types before initializing
            core.types(types.toArray(new EntryType[0]));

            // Create TypeManager before calling initialize() so storage can access it
            java.lang.reflect.Field typeManagerField = SanctionManagerCore.class.getDeclaredField("typeManager");
            typeManagerField.setAccessible(true);
            typeManagerField.set(core, new TypeManager(types));

            // Now initialize services
            core.initialize();

            return core;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SanctionManagerCore", e);
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Cleanup temp files
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
    public void testExecuteAndQueryById() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create and execute entry
        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Query by ID
        Optional<Entry> retrieved = entryService.queryById(entry.getId());
        assertTrue(retrieved.isPresent());
        assertEquals(entry.getId(), retrieved.get().getId());
        assertEquals(target, retrieved.get().getTarget());
        assertEquals(executor, retrieved.get().getExecutor());
        assertEquals("Test ban", retrieved.get().getReason());
    }

    @Test
    public void testQueryByTarget() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create and execute multiple entries for same target
        EntryWrapper entry1 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Ban 1", null);
        entryService.execute(entry1);

        EntryWrapper entry2 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Ban 2", null);
        entryService.execute(entry2);

        // Query by target
        QueryOptions options = QueryOptions.create();
        List<Entry> results = entryService.query(target, options);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(e -> e.getTarget().equals(target)));
    }

    @Test
    public void testQueryByTypeAndRelId() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create and execute entry
        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Query by type and relId
        Optional<Entry> retrieved = entryService.queryByTypeAndRelId(banType.getIdentifier(), entry.getRelId());
        assertTrue(retrieved.isPresent());
        assertEquals(entry.getId(), retrieved.get().getId());
        assertEquals(entry.getRelId(), retrieved.get().getRelId());
    }

    @Test
    public void testQueryLatestActive() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create multiple entries, with delays to ensure different timestamps
        EntryWrapper entry1 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Ban 1", null);
        entryService.execute(entry1);

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        EntryWrapper entry2 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Ban 2", null);
        entryService.execute(entry2);

        // Query latest active
        Optional<Entry> latest = entryService.queryLatestActive(target, banType.getIdentifier());
        assertTrue(latest.isPresent());
        assertEquals(entry2.getId(), latest.get().getId());
    }

    @Test
    public void testQueryAll() {
        UUID executor = UUID.randomUUID();

        // Create entries of different types
        EntryWrapper ban1 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), UUID.randomUUID(), executor, "Ban 1", null);
        entryService.execute(ban1);

        EntryWrapper mute1 = (EntryWrapper) entryFactory.create(muteType.getIdentifier(), UUID.randomUUID(), executor, "Mute 1", null);
        entryService.execute(mute1);

        // Query all
        QueryOptions options = QueryOptions.create();
        List<Entry> allEntries = entryService.queryAll(options);
        assertEquals(2, allEntries.size());

        // Query only bans
        List<Entry> bans = entryService.queryAll(options, banType.getIdentifier());
        assertEquals(1, bans.size());
        assertEquals(banType.getIdentifier(), bans.get(0).getType().getIdentifier());
    }

    @Test
    public void testWithdraw() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        UUID withdrawnBy = UUID.randomUUID();

        // Create and execute entry
        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Withdraw
        entryService.withdraw(entry, withdrawnBy, "Withdrawn for testing");

        // Verify withdrawal
        assertTrue(entry.isWithdrawn());
        assertEquals(withdrawnBy, entry.getWithdrawnBy());
        assertEquals("Withdrawn for testing", entry.getWithdrawalReason());

        // Verify persisted
        Optional<Entry> retrieved = entryService.queryById(entry.getId());
        assertTrue(retrieved.isPresent());
        assertTrue(retrieved.get().isWithdrawn());
    }

    @Test
    public void testWithdrawAlreadyWithdrawn() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Withdraw once
        entryService.withdraw(entry, UUID.randomUUID(), "First withdrawal");

        // Try to withdraw again - should throw
        assertThrows(IllegalStateException.class, () ->
                entryService.withdraw(entry, UUID.randomUUID(), "Second withdrawal")
        );
    }

    @Test
    public void testUpdateWithdrawReason() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Withdraw
        entryService.withdraw(entry, UUID.randomUUID(), "Original reason");

        // Update withdraw reason
        entryService.updateWithdrawReason(entry, "Updated reason");

        assertEquals("Updated reason", entry.getWithdrawalReason());

        // Verify persisted
        Optional<Entry> retrieved = entryService.queryById(entry.getId());
        assertTrue(retrieved.isPresent());
        assertEquals("Updated reason", retrieved.get().getWithdrawalReason());
    }

    @Test
    public void testUpdateWithdrawReasonNotWithdrawn() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        // Try to update withdraw reason on non-withdrawn entry
        assertThrows(IllegalStateException.class, () ->
                entryService.updateWithdrawReason(entry, "New reason")
        );
    }

    @Test
    public void testUpdateData() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        JsonObject originalData = new JsonObject();
        originalData.addProperty("key1", "value1");

        EntryWrapper entry = (EntryWrapper) entryFactory.create(kickType.getIdentifier(), target, executor, "Test kick", null, originalData);
        entryService.execute(entry);

        // Update data
        JsonObject newData = new JsonObject();
        newData.addProperty("key2", "value2");
        entryService.updateData(entry, newData);

        // Verify change
        JsonObject retrievedData = entry.getData();
        assertTrue(retrievedData.has("key2"));
        assertEquals("value2", retrievedData.get("key2").getAsString());

        // Verify persisted
        Optional<Entry> retrieved = entryService.queryById(entry.getId());
        assertTrue(retrieved.isPresent());
        JsonObject persistedData = retrieved.get().getData();
        assertTrue(persistedData.has("key2"));
    }

    @Test
    public void testUpdateDataOnWithdrawnEntry() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        JsonObject originalData = new JsonObject();
        originalData.addProperty("key1", "value1");

        EntryWrapper entry = (EntryWrapper) entryFactory.create(kickType.getIdentifier(), target, executor, "Test kick", null, originalData);
        entryService.execute(entry);

        // Withdraw
        entryService.withdraw(entry, UUID.randomUUID(), "Withdrawn");

        // Try to update data - should throw
        JsonObject newData = new JsonObject();
        assertThrows(IllegalStateException.class, () ->
                entryService.updateData(entry, newData)
        );
    }

    @Test
    public void testDeleteById() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        long id = entry.getId();

        // Delete
        entryService.delete(id);

        // Verify deletion
        Optional<Entry> retrieved = entryService.queryById(id);
        assertFalse(retrieved.isPresent());
    }

    @Test
    public void testDeleteByTypeAndRelId() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        EntryWrapper entry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Test ban", null);
        entryService.execute(entry);

        long id = entry.getId();
        long relId = entry.getRelId();

        // Delete by type and relId
        entryService.delete(banType.getIdentifier(), relId);

        // Verify deletion
        Optional<Entry> retrieved = entryService.queryById(id);
        assertFalse(retrieved.isPresent());
    }

    @Test
    public void testQueryWithFilters() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create entries
        EntryWrapper ban = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Ban", null);
        entryService.execute(ban);

        EntryWrapper mute = (EntryWrapper) entryFactory.create(muteType.getIdentifier(), target, executor, "Mute", null);
        entryService.execute(mute);

        EntryWrapper withdrawn = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Withdrawn ban", null);
        entryService.execute(withdrawn);
        entryService.withdraw(withdrawn, UUID.randomUUID(), "Test withdrawal");

        // Query without filters - should only get active entries
        QueryOptions activeOnly = QueryOptions.create();
        List<Entry> activeEntries = entryService.query(target, activeOnly);
        assertEquals(2, activeEntries.size());
        assertTrue(activeEntries.stream().noneMatch(Entry::isWithdrawn));

        // Query including withdrawn
        QueryOptions includeWithdrawn = QueryOptions.create().includeWithdrawn(true);
        List<Entry> allEntries = entryService.query(target, includeWithdrawn);
        assertEquals(3, allEntries.size());

        // Query with type filter
        QueryOptions banOnly = QueryOptions.create().types(banType.getIdentifier());
        List<Entry> banEntries = entryService.query(target, banOnly);
        assertEquals(1, banEntries.size());
        assertEquals(banType.getIdentifier(), banEntries.get(0).getType().getIdentifier());
    }

    @Test
    public void testQueryLatestActiveExcludesWithdrawn() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create first entry and withdraw it
        EntryWrapper entry1 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "First ban", null);
        entryService.execute(entry1);
        entryService.withdraw(entry1, UUID.randomUUID(), "Withdrawn");

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        // Create second active entry
        EntryWrapper entry2 = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Second ban", null);
        entryService.execute(entry2);

        // Query latest active - should return entry2, not entry1
        Optional<Entry> latest = entryService.queryLatestActive(target, banType.getIdentifier());
        assertTrue(latest.isPresent());
        assertEquals(entry2.getId(), latest.get().getId());
    }

    @Test
    public void testQueryLatestActiveWithExpiredEntries() {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();

        // Create expired entry (1ms duration)
        EntryWrapper expiredEntry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Expired ban", Duration.ofMillis(1));
        entryService.execute(expiredEntry);

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        // Create permanent entry
        EntryWrapper permanentEntry = (EntryWrapper) entryFactory.create(banType.getIdentifier(), target, executor, "Permanent ban", null);
        entryService.execute(permanentEntry);

        // Query latest active - should return permanent entry
        Optional<Entry> latest = entryService.queryLatestActive(target, banType.getIdentifier());
        assertTrue(latest.isPresent());
        assertEquals(permanentEntry.getId(), latest.get().getId());
    }
}
