package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.FilePlayerRepository;
import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.MemoryPlayerCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerService (Repository + Cache architecture).
 * <p>
 * Verifies that UUID <-> Name mapping works correctly through the cache layer,
 * including case-insensitive name lookup, name changes, and cache consistency.
 */
class PlayerRepositoryTest {

    private PlayerService service;
    private UUID testUuid;
    private String testName;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Use File repository (persistent) + Memory cache for testing
        PlayerRepository repository = new FilePlayerRepository(tempDir.toString());
        PlayerCache cache = new MemoryPlayerCache();
        service = new PlayerService(repository, cache);

        testUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testName = "Steve";
    }

    @Test
    void testSaveAndFindByName() {
        // Save player data
        service.save(testUuid, testName);

        // Find by name
        UUID foundUuid = service.findUuidByName(testName);
        assertNotNull(foundUuid, "UUID should be found");
        assertEquals(testUuid, foundUuid, "UUID should match");
    }

    @Test
    void testSaveAndFindByUuid() {
        // Save player data
        service.save(testUuid, testName);

        // Find by UUID
        String foundName = service.findNameByUuid(testUuid);
        assertNotNull(foundName, "Name should be found");
        assertEquals(testName, foundName, "Name should match");
    }

    @Test
    void testCaseInsensitiveNameLookup() {
        // Save player data
        service.save(testUuid, testName);

        // Test various case combinations
        assertEquals(testUuid, service.findUuidByName("Steve"));
        assertEquals(testUuid, service.findUuidByName("steve"));
        assertEquals(testUuid, service.findUuidByName("STEVE"));
        assertEquals(testUuid, service.findUuidByName("StEvE"));
    }

    @Test
    void testNameChange() {
        // Save initial name
        service.save(testUuid, "OldName");
        assertEquals("OldName", service.findNameByUuid(testUuid));

        // Change name
        service.save(testUuid, "NewName");

        // Verify new name is found
        assertEquals("NewName", service.findNameByUuid(testUuid));
        assertEquals(testUuid, service.findUuidByName("NewName"));

        // Old name should no longer resolve to this UUID
        assertNull(service.findUuidByName("OldName"),
            "Old name should not resolve after name change");
    }

    @Test
    void testUnknownPlayer() {
        // Try to find player that doesn't exist
        assertNull(service.findUuidByName("UnknownPlayer"));
        assertNull(service.findNameByUuid(UUID.randomUUID()));
    }

    @Test
    void testMultiplePlayers() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        UUID uuid3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

        service.save(uuid1, "Steve");
        service.save(uuid2, "Alex");
        service.save(uuid3, "Notch");

        // Verify all players can be found
        assertEquals(uuid1, service.findUuidByName("Steve"));
        assertEquals(uuid2, service.findUuidByName("Alex"));
        assertEquals(uuid3, service.findUuidByName("Notch"));

        assertEquals("Steve", service.findNameByUuid(uuid1));
        assertEquals("Alex", service.findNameByUuid(uuid2));
        assertEquals("Notch", service.findNameByUuid(uuid3));
    }

    @Test
    void testNamePreservation() {
        // Save with specific casing
        service.save(testUuid, "StEvE");

        // Name should be preserved exactly as saved
        assertEquals("StEvE", service.findNameByUuid(testUuid));

        // But lookup should be case-insensitive
        assertEquals(testUuid, service.findUuidByName("steve"));
        assertEquals(testUuid, service.findUuidByName("STEVE"));
    }

    @Test
    void testCacheBehavior() {
        // First save
        service.save(testUuid, testName);

        // First lookup - should populate cache
        UUID foundUuid = service.findUuidByName(testName);
        assertEquals(testUuid, foundUuid);

        // Second lookup - should hit cache (faster)
        UUID cachedUuid = service.findUuidByName(testName);
        assertEquals(testUuid, cachedUuid);

        // Verify cache consistency with name change
        service.save(testUuid, "NewName");
        assertEquals("NewName", service.findNameByUuid(testUuid));
        assertNull(service.findUuidByName(testName), "Old name should be cleared from cache");
    }

    @Test
    void testClose() {
        service.save(testUuid, testName);

        // Close should not throw
        assertDoesNotThrow(() -> service.close());
    }
}
