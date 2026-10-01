package io.github.floatingpointmc.sanctionmanager.minecraft.player;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Repository for managing player UUID ↔ Name mappings.
 * <p>
 * This repository is owned by the Minecraft layer and stores Minecraft-specific
 * player data. It does NOT use Core's storage infrastructure to maintain
 * clear domain boundaries.
 */
public interface PlayerRepository {

    /**
     * Find UUID by player name.
     * <p>
     * This is the most important query for offline player resolution.
     * Names are case-insensitive.
     *
     * @param name Player name (case-insensitive)
     * @return Player UUID, or null if not found
     */
    @Nullable UUID findUuidByName(@NotNull String name);

    /**
     * Find current player name by UUID.
     *
     * @param uuid Player UUID
     * @return Current player name, or null if not found
     */
    @Nullable String findNameByUuid(@NotNull UUID uuid);

    /**
     * Save or update player UUID ↔ Name mapping.
     * <p>
     * If the UUID already exists with a different name, the name is updated.
     * This handles player name changes.
     *
     * @param uuid Player UUID
     * @param name Player name
     */
    void save(@NotNull UUID uuid, @NotNull String name);

    /**
     * Close repository and release resources.
     */
    void close();
}
