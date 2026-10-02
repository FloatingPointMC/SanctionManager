package io.github.floatingpointmc.sanctionmanager.minecraft.service;

import io.github.floatingpointmc.sanctionmanager.minecraft.operation.BanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.MuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnbanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnmuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.WarnOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnwarnOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Service layer for executing sanction operations.
 * <p>
 * Abstracts the execution mode (standalone vs bridge) from commands.
 * Commands create Operation objects and pass them to this service,
 * which handles execution or forwarding as appropriate.
 */
public interface SanctionService {

    /**
     * Execute a ban operation.
     *
     * @param operation the ban operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeBan(@NotNull BanOperation operation);

    /**
     * Execute an unban operation.
     *
     * @param operation the unban operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeUnban(@NotNull UnbanOperation operation);

    /**
     * Execute a mute operation.
     *
     * @param operation the mute operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeMute(@NotNull MuteOperation operation);

    /**
     * Execute an unmute operation.
     *
     * @param operation the unmute operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeUnmute(@NotNull UnmuteOperation operation);

    /**
     * Execute a warn operation.
     *
     * @param operation the warn operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeWarn(@NotNull WarnOperation operation);

    /**
     * Execute an unwarn operation.
     *
     * @param operation the unwarn operation to execute
     * @return the result of the operation
     */
    @NotNull OperationResult executeUnwarn(@NotNull UnwarnOperation operation);
}
