package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Player service that orchestrates Cache + Repository (Persistence).
 * <p>
 * Data flow:
 * <pre>
 * Query:
 *   1. Check Cache (RAM/Redis)
 *   2. If cache miss → Query Repository (Database/File)
 *   3. Update Cache with result
 *   4. Return result
 *
 * Write:
 *   1. Save to Repository (Database/File) - PERSISTENCE FIRST
 *   2. Update Cache
 * </pre>
 * <p>
 * This ensures:
 * - Fast reads through caching
 * - Durability through repository persistence
 * - Cache consistency with repository
 */
public class PlayerService {
    private final PlayerRepository repository;
    private final PlayerCache cache;

    public PlayerService(@NotNull PlayerRepository repository, @NotNull PlayerCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    /**
     * Find UUID by player name.
     * <p>
     * Flow: Cache → Repository (on miss) → Update cache
     *
     * @param name Player name (case-insensitive)
     * @return UUID if found, null otherwise
     */
    public @Nullable UUID findUuidByName(@NotNull String name) {
        // 1. Try cache first
        UUID cached = cache.get(name);
        if (cached != null) {
            return cached;
        }

        // 2. Cache miss - query repository (persistence)
        UUID uuid = repository.findUuidByName(name);

        // 3. Update cache if found
        if (uuid != null) {
            String actualName = repository.findNameByUuid(uuid);
            if (actualName != null) {
                cache.put(uuid, actualName);
            }
        }

        return uuid;
    }

    /**
     * Find player name by UUID.
     * <p>
     * Flow: Cache → Repository (on miss) → Update cache
     *
     * @param uuid Player UUID
     * @return Name if found, null otherwise
     */
    public @Nullable String findNameByUuid(@NotNull UUID uuid) {
        // 1. Try cache first
        String cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // 2. Cache miss - query repository (persistence)
        String name = repository.findNameByUuid(uuid);

        // 3. Update cache if found
        if (name != null) {
            cache.put(uuid, name);
        }

        return name;
    }

    /**
     * Save player mapping.
     * <p>
     * Flow: Repository (persistence) → Update cache
     * <p>
     * IMPORTANT: Repository is written FIRST to ensure durability.
     * Cache is updated AFTER successful persistence.
     *
     * @param uuid Player UUID
     * @param name Player name
     */
    public void save(@NotNull UUID uuid, @NotNull String name) {
        // 1. PERSIST FIRST - this is the source of truth
        repository.save(uuid, name);

        // 2. Update cache after successful persistence
        cache.put(uuid, name);
    }

    /**
     * Close service and release resources.
     */
    public void close() {
        repository.close();
        cache.close();
    }
}
