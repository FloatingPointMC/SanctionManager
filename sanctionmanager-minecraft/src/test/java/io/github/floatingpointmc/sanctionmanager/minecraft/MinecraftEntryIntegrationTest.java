package io.github.floatingpointmc.sanctionmanager.minecraft;

import com.google.gson.JsonObject;
import io.github.floatingpointmc.sanctionmanager.api.model.Entry;
import io.github.floatingpointmc.sanctionmanager.api.service.EntryService;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.StorageConfig;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftEntryIntegrationTest {
    @Test void platformUsesCommittedEntriesAndPersistsWithdrawal(@TempDir Path directory) {
        StorageConfig config = StorageConfig.builder()
                .databaseEnabled(false).redisEnabled(false).binaryDataDir(directory.toString()).build();
        MinecraftSanctionManager manager = new MinecraftSanctionManager(config);
        UUID target = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        long id;
        EntryService service = manager.getEntryService();
        try {
            assertTrue(manager.getCore().getTypeRegistry().snapshot().contains(MinecraftEntryTypes.BAN));
            assertTrue(manager.getCore().getTypeRegistry().snapshot().contains(MinecraftEntryTypes.MUTE));
            Entry entry = service.create(target, MinecraftEntryTypes.BAN, actor, null, "reason", new JsonObject());
            id = entry.getId();
            assertEquals(id, service.queryLatestActive(target, MinecraftEntryTypes.BAN).get().getId());
            TranslationContext.EntryMessage message = new TranslationContext("SanctionManager", "test")
                    .forEntry(entry.snapshot(), "Target", "Operator");
            assertEquals("Entry #" + id + ": reason", TranslationFormatter.replaceVariables("Entry #%id%: %reason%", message));
            entry.withdraw(actor);
            assertFalse(service.queryLatestActive(target, MinecraftEntryTypes.BAN).isPresent());
            assertEquals(1, service.findByTarget(target, MinecraftEntryTypes.BAN).size());
        } finally {
            manager.shutdown();
        }
        assertThrows(IllegalStateException.class, () -> service.queryLatestActive(target, MinecraftEntryTypes.BAN));
        MinecraftSanctionManager reopened = new MinecraftSanctionManager(config);
        try {
            Entry stored = reopened.getEntryService().find(id).get();
            assertTrue(stored.isWithdrawn());
            assertEquals(actor, stored.getWithdrawnBy());
        } finally {
            reopened.shutdown();
        }
    }
}

