package io.github.floatingpointmc.sanctionmanager.minecraft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.UUID;

public interface MinecraftProvider {
    @Unmodifiable
    @NotNull
    Collection<String> getPlayerNames();

    @Unmodifiable
    @NotNull
    Collection<UUID> getPlayerUUIDs();

    @Nullable SanctionPlayer getPlayer(@NotNull UUID uuid);

    @Nullable SanctionPlayer getPlayer(@NotNull String name);

    /**
     * Schedule a task to run on the Minecraft main thread.
     * <p>
     * This method is used for operations that require Minecraft API access,
     * such as kicking players or sending messages.
     *
     * @param task the task to run
     */
    void schedule(@NotNull Runnable task);
}