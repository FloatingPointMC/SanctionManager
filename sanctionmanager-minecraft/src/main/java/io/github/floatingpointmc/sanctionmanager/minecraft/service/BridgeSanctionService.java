package io.github.floatingpointmc.sanctionmanager.minecraft.service;

import io.github.floatingpointmc.sanctionmanager.minecraft.operation.BanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.MuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnbanOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnmuteOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.WarnOperation;
import io.github.floatingpointmc.sanctionmanager.minecraft.operation.UnwarnOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Bridge implementation of SanctionService.
 * <p>
 * In bridge mode, operations are serialized and forwarded to the proxy server
 * for execution. This implementation does not execute operations locally.
 * <p>
 * TODO: Implement actual Bridge transport mechanism.
 * Current implementation is a placeholder that returns ERROR for all operations.
 */
public class BridgeSanctionService implements SanctionService {

    @Override
    public @NotNull OperationResult executeBan(@NotNull BanOperation operation) {
        // TODO: Serialize and send BanOperation to proxy
        // For now, return ERROR as bridge transport is not yet implemented
        return OperationResult.ERROR;
    }

    @Override
    public @NotNull OperationResult executeUnban(@NotNull UnbanOperation operation) {
        // TODO: Serialize and send UnbanOperation to proxy
        return OperationResult.ERROR;
    }

    @Override
    public @NotNull OperationResult executeMute(@NotNull MuteOperation operation) {
        // TODO: Serialize and send MuteOperation to proxy
        return OperationResult.ERROR;
    }

    @Override
    public @NotNull OperationResult executeUnmute(@NotNull UnmuteOperation operation) {
        // TODO: Serialize and send UnmuteOperation to proxy
        return OperationResult.ERROR;
    }

    @Override
    public @NotNull OperationResult executeWarn(@NotNull WarnOperation operation) {
        // TODO: Serialize and send WarnOperation to proxy
        return OperationResult.ERROR;
    }

    @Override
    public @NotNull OperationResult executeUnwarn(@NotNull UnwarnOperation operation) {
        // TODO: Serialize and send UnwarnOperation to proxy
        return OperationResult.ERROR;
    }
}
