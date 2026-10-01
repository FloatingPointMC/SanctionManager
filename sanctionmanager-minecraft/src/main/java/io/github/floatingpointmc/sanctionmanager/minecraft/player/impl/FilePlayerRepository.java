package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based implementation of PlayerRepository.
 * <p>
 * Stores player data in: {dataDir}/minecraft/players/players.dat
 * <p>
 * Format: Simple text file with "uuid:name" per line.
 * <p>
 * Uses in-memory cache for fast lookups, persists to file on save.
 */
public class FilePlayerRepository implements PlayerRepository {
    private final Path dataFile;
    private final ConcurrentHashMap<UUID, String> uuidToName;
    private final ConcurrentHashMap<String, UUID> nameToUuid;

    public FilePlayerRepository(@NotNull String dataDir) {
        Path minecraftDir = Paths.get(dataDir, "minecraft", "players");
        try {
            Files.createDirectories(minecraftDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create minecraft player data directory: " + minecraftDir, e);
        }

        this.dataFile = minecraftDir.resolve("players.dat");
        this.uuidToName = new ConcurrentHashMap<>();
        this.nameToUuid = new ConcurrentHashMap<>();

        load();
    }

    @Override
    public @Nullable UUID findUuidByName(@NotNull String name) {
        return nameToUuid.get(normalizeName(name));
    }

    @Override
    public @Nullable String findNameByUuid(@NotNull UUID uuid) {
        return uuidToName.get(uuid);
    }

    @Override
    public void save(@NotNull UUID uuid, @NotNull String name) {
        String normalizedName = normalizeName(name);

        // Remove old name mapping if UUID had different name
        String oldName = uuidToName.get(uuid);
        if (oldName != null && !oldName.equalsIgnoreCase(name)) {
            nameToUuid.remove(normalizeName(oldName));
        }

        // Update in-memory maps
        uuidToName.put(uuid, name);
        nameToUuid.put(normalizedName, uuid);

        // Persist to file
        persist();
    }

    @Override
    public void close() {
        persist();
        uuidToName.clear();
        nameToUuid.clear();
    }

    private void load() {
        if (!Files.exists(dataFile)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(dataFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }

                try {
                    UUID uuid = UUID.fromString(parts[0]);
                    String name = parts[1];
                    uuidToName.put(uuid, name);
                    nameToUuid.put(normalizeName(name), uuid);
                } catch (IllegalArgumentException e) {
                    // Skip invalid UUID
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load player data from: " + dataFile, e);
        }
    }

    private void persist() {
        try (BufferedWriter writer = Files.newBufferedWriter(dataFile)) {
            writer.write("# SanctionManager Player Data (UUID:Name)\n");
            for (Map.Entry<UUID, String> entry : uuidToName.entrySet()) {
                writer.write(entry.getKey().toString());
                writer.write(":");
                writer.write(entry.getValue());
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist player data to: " + dataFile, e);
        }
    }

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
