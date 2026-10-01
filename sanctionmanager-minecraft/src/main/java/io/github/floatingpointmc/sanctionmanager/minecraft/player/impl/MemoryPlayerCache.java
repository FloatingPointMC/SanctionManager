package io.github.floatingpointmc.sanctionmanager.minecraft.player.impl;

import io.github.floatingpointmc.sanctionmanager.minecraft.player.PlayerCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory (RAM) implementation of PlayerCache.
 * <p>
 * Fast but NOT persistent - data is lost on restart.
 * Use this only as a cache, never as storage.
 */
public class MemoryPlayerCache implements PlayerCache {
    private final ConcurrentHashMap<UUID, String> uuidToName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> nameToUuid = new ConcurrentHashMap<>();

    @Override
    public @Nullable UUID get(@NotNull String name) {
        return nameToUuid.get(normalizeName(name));
    }

    @Override
    public @Nullable String get(@NotNull UUID uuid) {
        return uuidToName.get(uuid);
    }

    @Override
    public void put(@NotNull UUID uuid, @NotNull String name) {
        String normalizedName = normalizeName(name);

        // Remove old name mapping if UUID had different name
        String oldName = uuidToName.get(uuid);
        if (oldName != null && !oldName.equalsIgnoreCase(name)) {
            nameToUuid.remove(normalizeName(oldName));
        }

        uuidToName.put(uuid, name);
        nameToUuid.put(normalizedName, uuid);
    }

    @Override
    public void invalidate(@NotNull UUID uuid) {
        String name = uuidToName.remove(uuid);
        if (name != null) {
            nameToUuid.remove(normalizeName(name));
        }
    }

    @Override
    public void invalidate(@NotNull String name) {
        UUID uuid = nameToUuid.remove(normalizeName(name));
        if (uuid != null) {
            uuidToName.remove(uuid);
        }
    }

    @Override
    public void clear() {
        uuidToName.clear();
        nameToUuid.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private String normalizeName(@NotNull String name) {
        return name.toLowerCase();
    }
}
