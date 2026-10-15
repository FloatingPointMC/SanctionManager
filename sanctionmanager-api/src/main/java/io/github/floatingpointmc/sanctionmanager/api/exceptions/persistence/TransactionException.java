package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when a database transaction cannot be completed.
 * <p>
 * This exception indicates failures during transaction initiation, commitment, or rollback,
 * or when the outcome of a transaction cannot be determined (e.g., connection lost during commit).
 * </p>
 * <p>
 * When a transaction outcome is uncertain, the original operation exception is preserved as the cause,
 * and any rollback exceptions are added as suppressed exceptions. This allows callers to investigate
 * what occurred during both the operation and any recovery attempt.
 * </p>
 */
public class TransactionException extends PersistenceException {
    /**
     * Constructs a TransactionException with the given message.
     *
     * @param message the detail message
     */
    public TransactionException(String message) {
        super(message);
    }

    /**
     * Constructs a TransactionException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
    }
}
