package io.github.floatingpointmc.sanctionmanager.minecraft;

import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationContext;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationFormatter;
import io.github.floatingpointmc.sanctionmanager.minecraft.config.TranslationConfig;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinecraftModuleTest {

    private TranslationContext.EntryMessage message(long id, java.util.Date expiry, String targetName, String operator) {
        io.github.floatingpointmc.sanctionmanager.api.model.EntrySnapshot entry =
                new io.github.floatingpointmc.sanctionmanager.api.model.EntrySnapshot(id, MinecraftEntryTypes.BAN,
                        UUID.fromString("00000000-0000-0000-0000-000000000001"), null,
                        new java.util.Date(1000), expiry, "Hacking", false, null, null, 0, new com.google.gson.JsonObject());
        return new TranslationContext("SanctionManager", "1.0").forEntry(entry, targetName, operator);
    }

    @Test
    void testMessageFormatterReadsEntrySnapshot() {
        TranslationContext.EntryMessage context = message(42, null, "TestPlayer", "Admin");
        assertEquals("Player TestPlayer banned for Hacking by Admin", TranslationFormatter.replaceVariables(
                "Player %name% banned for %reason% by %operator%", context));
        assertEquals("Entry #42", TranslationFormatter.replaceVariables("Entry #%id%", context));
    }

    @Test
    void testMessageFormatterPreservesLongEntryId() {
        TranslationContext.EntryMessage context = message(4294967296L, null, "TestPlayer", null);
        assertEquals("4294967296", TranslationFormatter.replaceVariables("%id%", context));
    }

    @Test
    void testMessageFormatterUsesSnapshotWhenNamesAreUnavailable() {
        TranslationContext.EntryMessage context = message(1, null, null, null);
        assertEquals("00000000-0000-0000-0000-000000000001 [Console] permanent", TranslationFormatter.replaceVariables(
                "%name% %operator% %duration%", context));
    }

    @Test
    void testMessageFormatterFormatLines() {
        TranslationContext context = TranslationContext.builder()
                .pluginName("SanctionManager")
                .pluginVersion("2.0")
                .build();

        java.util.List<String> lines = Arrays.asList("Plugin: %plugin%", "Version: %version%");
        java.util.List<String> result = TranslationFormatter.formatLines(lines, context);

        assertEquals(2, result.size());
        assertEquals("Plugin: SanctionManager", result.get(0));
        assertEquals("Version: 2.0", result.get(1));
    }

    @Test
    void testMessageFormatterIsTemporary() {
        assertFalse(TranslationFormatter.isTemporary(message(1, null, "Player", null)));
        assertTrue(TranslationFormatter.isTemporary(message(2, new java.util.Date(2000), "Player", null)));
    }

    @Test
    void testTranslationConfigDefaults() {
        TranslationConfig defaults = TranslationConfig.defaults();
        assertNotNull(defaults);
        assertTrue(defaults.getStringList("description").isEmpty());
    }

    @Test
    void testTranslationConfigGetPath() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> command = new HashMap<>();
        Map<String, Object> ban = new HashMap<>();
        ban.put("usage", "/ban <Player> [Duration] [Reason]");
        ban.put("desc", "Ban a specific player.");
        Map<String, Object> arg = new HashMap<>();
        arg.put("player", "The player to ban.");
        arg.put("duration", "The duration of the ban, default permanent.");
        arg.put("reason", "The reason for the ban.");
        ban.put("arg", arg);
        command.put("ban", ban);
        data.put("command", command);

        TranslationConfig config = new TranslationConfig(data);

        assertEquals("/ban <Player> [Duration] [Reason]", config.get("command.ban.usage"));
        assertEquals("Ban a specific player.", config.get("command.ban.desc"));
        assertEquals("The player to ban.", config.get("command.ban.arg.player"));
        assertEquals("The duration of the ban, default permanent.", config.get("command.ban.arg.duration"));
        assertEquals("The reason for the ban.", config.get("command.ban.arg.reason"));
    }

    @Test
    void testTranslationConfigCommandConvenienceMethods() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> command = new HashMap<>();
        Map<String, Object> ban = new HashMap<>();
        ban.put("usage", "/ban <Player> [Duration] [Reason]");
        ban.put("desc", "Ban a specific player.");
        Map<String, Object> arg = new HashMap<>();
        arg.put("player", "The player to ban.");
        arg.put("duration", "The duration of the ban, default permanent.");
        ban.put("arg", arg);
        command.put("ban", ban);
        data.put("command", command);

        TranslationConfig config = new TranslationConfig(data);

        assertEquals("/ban <Player> [Duration] [Reason]", config.getCommandUsage("ban"));
        assertEquals("Ban a specific player.", config.getCommandDescription("ban"));
        assertEquals("The player to ban.", config.getCommandArgumentDescription("ban", "player"));
        assertEquals("The duration of the ban, default permanent.", config.getCommandArgumentDescription("ban", "duration"));
    }

    @Test
    void testTranslationConfigMissingPathReturnsPath() {
        TranslationConfig config = new TranslationConfig(new HashMap<>());
        assertEquals("command.mute.usage", config.get("command.mute.usage"));
    }

    @Test
    void testTranslationConfigMissingPathWithDefault() {
        TranslationConfig config = new TranslationConfig(new HashMap<>());
        assertEquals("Default value", config.get("command.mute.usage", "Default value"));
    }

    @Test
    void testTranslationConfigCommandNameIsDynamic() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> command = new HashMap<>();
        Map<String, Object> mute = new HashMap<>();
        mute.put("usage", "/mute <Player> [Duration] [Reason]");
        mute.put("desc", "Mute a specific player.");
        command.put("mute", mute);
        data.put("command", command);

        TranslationConfig config = new TranslationConfig(data);

        assertEquals("/mute <Player> [Duration] [Reason]", config.getCommandUsage("mute"));
        assertEquals("Mute a specific player.", config.getCommandDescription("mute"));
    }

    @Test
    void testTranslationConfigStringList() {
        Map<String, Object> banData = new HashMap<>();
        banData.put("permanent", Arrays.asList("Line 1", "Line 2"));
        banData.put("temporary", Arrays.asList("Temp 1", "Temp 2"));
        Map<String, Object> data = new HashMap<>();
        data.put("ban", banData);

        TranslationConfig config = new TranslationConfig(data);

        assertEquals(Arrays.asList("Line 1", "Line 2"), config.getStringList("ban.permanent"));
        assertEquals(Arrays.asList("Temp 1", "Temp 2"), config.getStringList("ban.temporary"));
    }

    @Test
    void testMessageFormatterFormatDuration() {
        assertEquals("permanent", TranslationFormatter.formatDuration(null));
        assertEquals("1d", TranslationFormatter.formatDuration(java.time.Duration.ofDays(1)));
        assertEquals("1h", TranslationFormatter.formatDuration(java.time.Duration.ofHours(1)));
        assertEquals("30s", TranslationFormatter.formatDuration(java.time.Duration.ofSeconds(30)));
    }
}