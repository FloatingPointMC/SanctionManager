package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PlayerRepository.
 * <p>
 * Data is lost on server restart. This is intentional for RAM mode.
 * <p>
 * Thread-safe using ConcurrentHashMap.
 */
public class MemoryPlayerRepository implements PlayerRepository {
    private final ConcurrentHashMap<UUID, String> uuidToName;
    private final ConcurrentHashMap<String, UUID> nameToUuid;

    public MemoryPlayerRepository() {
        this.uuidToName = new ConcurrentHashMap<>();
        this.nameToUuid = new ConcurrentHashMap<>();
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

        // Update mappings
        uuidToName.put(uuid, name);
        nameToUuid.put(normalizedName, uuid);
    }

    @Override
    public void close() {
        // No resources to release for memory storage
        uuidToName.clear();
        nameToUuid.clear();
    }

    /**
     * Normalize name for case-insensitive lookup.
     * Minecraft names are case-insensitive.
     */
    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
