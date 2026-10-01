package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.impl.MemoryPlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PlayerRepository implementations.
 * <p>
 * Verifies that UUID <-> Name mapping works correctly,
 * including case-insensitive name lookup and name changes.
 */
class PlayerRepositoryTest {

    private PlayerRepository repository;
    private UUID testUuid;
    private String testName;

    @BeforeEach
    void setUp() {
        repository = new MemoryPlayerRepository();
        testUuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        testName = "Steve";
    }

    @Test
    void testSaveAndFindByName() {
        // Save player data
        repository.save(testUuid, testName);

        // Find by name
        UUID foundUuid = repository.findUuidByName(testName);
        assertNotNull(foundUuid, "UUID should be found");
        assertEquals(testUuid, foundUuid, "UUID should match");
    }

    @Test
    void testSaveAndFindByUuid() {
        // Save player data
        repository.save(testUuid, testName);

        // Find by UUID
        String foundName = repository.findNameByUuid(testUuid);
        assertNotNull(foundName, "Name should be found");
        assertEquals(testName, foundName, "Name should match");
    }

    @Test
    void testCaseInsensitiveNameLookup() {
        // Save player data
        repository.save(testUuid, testName);

        // Test various case combinations
        assertEquals(testUuid, repository.findUuidByName("Steve"));
        assertEquals(testUuid, repository.findUuidByName("steve"));
        assertEquals(testUuid, repository.findUuidByName("STEVE"));
        assertEquals(testUuid, repository.findUuidByName("StEvE"));
    }

    @Test
    void testNameChange() {
        // Save initial name
        repository.save(testUuid, "OldName");
        assertEquals("OldName", repository.findNameByUuid(testUuid));

        // Change name
        repository.save(testUuid, "NewName");

        // Verify new name is found
        assertEquals("NewName", repository.findNameByUuid(testUuid));
        assertEquals(testUuid, repository.findUuidByName("NewName"));

        // Old name should no longer resolve to this UUID
        assertNull(repository.findUuidByName("OldName"),
            "Old name should not resolve after name change");
    }

    @Test
    void testUnknownPlayer() {
        // Try to find player that doesn't exist
        assertNull(repository.findUuidByName("UnknownPlayer"));
        assertNull(repository.findNameByUuid(UUID.randomUUID()));
    }

    @Test
    void testMultiplePlayers() {
        UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID uuid2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        UUID uuid3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");

        repository.save(uuid1, "Steve");
        repository.save(uuid2, "Alex");
        repository.save(uuid3, "Notch");

        // Verify all players can be found
        assertEquals(uuid1, repository.findUuidByName("Steve"));
        assertEquals(uuid2, repository.findUuidByName("Alex"));
        assertEquals(uuid3, repository.findUuidByName("Notch"));

        assertEquals("Steve", repository.findNameByUuid(uuid1));
        assertEquals("Alex", repository.findNameByUuid(uuid2));
        assertEquals("Notch", repository.findNameByUuid(uuid3));
    }

    @Test
    void testNamePreservation() {
        // Save with specific casing
        repository.save(testUuid, "StEvE");

        // Name should be preserved exactly as saved
        assertEquals("StEvE", repository.findNameByUuid(testUuid));

        // But lookup should be case-insensitive
        assertEquals(testUuid, repository.findUuidByName("steve"));
        assertEquals(testUuid, repository.findUuidByName("STEVE"));
    }

    @Test
    void testClose() {
        repository.save(testUuid, testName);

        // Close should not throw
        assertDoesNotThrow(() -> repository.close());

        // After close, data should still be accessible in memory implementation
        // (close() for MemoryPlayerRepository just clears the maps)
        repository.close();
        assertNull(repository.findUuidByName(testName));
        assertNull(repository.findNameByUuid(testUuid));
    }
}
