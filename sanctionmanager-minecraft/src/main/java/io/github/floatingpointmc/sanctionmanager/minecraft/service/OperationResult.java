package io.github.floatingpointmc.sanctionmanager.minecraft.service;

/**
 * Result of a sanction service operation.
 */
public enum OperationResult {
    /**
     * Operation completed successfully.
     */
    SUCCESS,

    /**
     * Target player not found (never joined).
     */
    PLAYER_NOT_FOUND,

    /**
     * No active punishment found to remove.
     */
    NO_ACTIVE_PUNISHMENT,

    /**
     * Operation failed due to an error.
     */
    ERROR
}
