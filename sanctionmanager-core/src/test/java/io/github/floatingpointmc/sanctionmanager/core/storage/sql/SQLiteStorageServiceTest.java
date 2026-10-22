package io.github.floatingpointmc.sanctionmanager.core.storage.sql;

import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.floatingpointmc.sanctionmanager.api.model.EntryType;
import io.github.floatingpointmc.sanctionmanager.api.credential.CredentialProvider;
import io.github.floatingpointmc.sanctionmanager.core.SanctionManagerCore;
import io.github.floatingpointmc.sanctionmanager.core.config.CoreConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.cache.CacheConfig;
import io.github.floatingpointmc.sanctionmanager.core.config.storage.DatabaseConfig;
import io.github.floatingpointmc.sanctionmanager.core.management.TypeManager;
import io.github.floatingpointmc.sanctionmanager.core.model.EntryPersisted;
import io.github.floatingpointmc.sanctionmanager.core.model.EntrySnapshot;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.DatabaseStorageService;
import io.github.floatingpointmc.sanctionmanager.core.service.storage.Addition;
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
 * Integration tests for SQLite storage backend.
 */
public class SQLiteStorageServiceTest {
    private Path tempDir;
    private Path dbFile;
    private HikariDataSource dataSource;
    private DatabaseStorageService storageService;
    private SanctionManagerCore core;
    private TypeManager typeManager;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("sqlite-test-");
        dbFile = tempDir.resolve("test.db");

        List<EntryType> types = Arrays.asList(
            EntryType.of("ban"),
            EntryType.of("mute"),
            EntryType.of("kick").withExtraData()
        );
        typeManager = new TypeManager(types);

        CoreConfig coreConfig = new CoreConfig(0, Duration.ofMinutes(5));
        core = new SanctionManagerCore(coreConfig, typeManager, new CacheConfig(false, null, 0));

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        config.setAutoCommit(true);
        dataSource = new HikariDataSource(config);

        CredentialProvider credProvider = () -> new AbstractMap.SimpleEntry<>("", "");
        DatabaseConfig dbConfig = new DatabaseConfig("jdbc:sqlite:" + dbFile.toAbsolutePath(),
            credProvider, 1);
        storageService = new DatabaseStorageService(core, dbConfig);
        storageService.initialize();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (storageService != null) {
            storageService.shutdown();
        }
        if (dataSource != null) {
            dataSource.close();
        }
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @Test
    public void testInsertAndRetrieveEntry() throws IOException {
        EntryType banType = typeManager.getType("ban");
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();

        EntrySnapshot snapshot = new EntrySnapshot(banType, target, executor, null, "Test ban", null);
        Addition added = storageService.insertRecord(snapshot, now, null);

        assertNotNull(added);
        assertTrue(added.getId() > 0);
        assertTrue(added.getRel_id() > 0);

        Optional<EntryPersisted> retrieved = storageService.getRecord(added.getId());
        assertTrue(retrieved.isPresent());

        EntryPersisted entry = retrieved.get();
        assertEquals(added.getId(), entry.getId());
        assertEquals(target, entry.getTarget());
        assertEquals(executor, entry.getExecutor());
        assertEquals("Test ban", entry.getReason());
        assertNull(entry.getExpiryTime());
    }

    @Test
    public void testQueryByTarget() throws IOException {
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();
        EntryType banType = typeManager.getType("ban");

        EntrySnapshot snapshot1 = new EntrySnapshot(banType, target, executor, null, "Ban 1", null);
        storageService.insertRecord(snapshot1, now, null);

        EntrySnapshot snapshot2 = new EntrySnapshot(banType, target, executor, null, "Ban 2", null);
        storageService.insertRecord(snapshot2, now, null);

        List<EntryPersisted> results = storageService.queryByTarget(target);
        assertEquals(2, results.size());
    }

    @Test
    public void testQueryByTypeAndRelId() throws IOException {
        EntryType banType = typeManager.getType("ban");
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();

        EntrySnapshot snapshot = new EntrySnapshot(banType, target, executor, null, "Test", null);
        Addition added = storageService.insertRecord(snapshot, now, null);

        List<EntryPersisted> results = storageService.queryByTypeAndRelId(banType.getIdentifier(), added.getRel_id());
        assertEquals(1, results.size());
        assertEquals(added.getId(), results.get(0).getId());
    }

    @Test
    public void testDeleteEntry() throws IOException {
        EntryType banType = typeManager.getType("ban");
        UUID target = UUID.randomUUID();
        UUID executor = UUID.randomUUID();
        Date now = new Date();

        EntrySnapshot snapshot = new EntrySnapshot(banType, target, executor, null, "Ban", null);
        Addition added = storageService.insertRecord(snapshot, now, null);

        assertTrue(storageService.getRecord(added.getId()).isPresent());

        storageService.deleteRecord(added.getId());

        assertFalse(storageService.getRecord(added.getId()).isPresent());
    }

    @Test
    public void testQueryAll() throws IOException {
        EntryType banType = typeManager.getType("ban");
        Date now = new Date();

        for (int i = 0; i < 5; i++) {
            UUID target = UUID.randomUUID();
            UUID executor = UUID.randomUUID();
            EntrySnapshot snapshot = new EntrySnapshot(banType, target, executor, null, "Ban " + i, null);
            storageService.insertRecord(snapshot, now, null);
        }

        List<EntryPersisted> all = storageService.queryAll();
        assertEquals(5, all.size());
    }
}
