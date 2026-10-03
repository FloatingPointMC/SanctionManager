package io.github.floatingpointmc.sanctionmanager.minecraft.service;

import io.github.floatingpointmc.sanctionmanager.minecraft.operation.*;
import org.jetbrains.annotations.NotNull;

/**
 * Service layer for executing sanction operations.
 * <p>
 * All operations should be executed asynchronously to avoid blocking the Minecraft main thread.
 */
public interface SanctionService {
    @NotNull OperationResult executeBan(@NotNull BanOperation operation);

    @NotNull OperationResult executeUnban(@NotNull UnbanOperation operation);

    @NotNull OperationResult executeMute(@NotNull MuteOperation operation);

    @NotNull OperationResult executeUnmute(@NotNull UnmuteOperation operation);

    @NotNull OperationResult executeWarn(@NotNull WarnOperation operation);

    @NotNull OperationResult executeUnwarn(@NotNull UnwarnOperation operation);

    /**
     * Shutdown the service and release resources.
     */
    default void shutdown() {
        // Default implementation does nothing
    }
}
