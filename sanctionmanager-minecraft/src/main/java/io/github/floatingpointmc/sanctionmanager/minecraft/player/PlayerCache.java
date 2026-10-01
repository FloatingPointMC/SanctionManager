package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Cache layer for player UUID ↔ Name mappings.
 * <p>
 * This is NOT persistence. Cache is used to accelerate lookups
 * and reduce load on the underlying Repository (persistence layer).
 * <p>
 * Implementations:
 * - MemoryPlayerCache: In-memory cache (RAM)
 * - RedisPlayerCache: Redis-based distributed cache
 */
public interface PlayerCache {

    /**
     * Get UUID from cache by player name.
     *
     * @param name Player name (case-insensitive)
     * @return UUID if cached, null if cache miss
     */
    @Nullable UUID get(@NotNull String name);

    /**
     * Get name from cache by UUID.
     *
     * @param uuid Player UUID
     * @return Name if cached, null if cache miss
     */
    @Nullable String get(@NotNull UUID uuid);

    /**
     * Cache a player mapping.
     *
     * @param uuid Player UUID
     * @param name Player name
     */
    void put(@NotNull UUID uuid, @NotNull String name);

    /**
     * Invalidate cache entry by UUID.
     *
     * @param uuid Player UUID
     */
    void invalidate(@NotNull UUID uuid);

    /**
     * Invalidate cache entry by name.
     *
     * @param name Player name
     */
    void invalidate(@NotNull String name);

    /**
     * Clear all cached entries.
     */
    void clear();

    /**
     * Close cache and release resources.
     */
    void close();
}
