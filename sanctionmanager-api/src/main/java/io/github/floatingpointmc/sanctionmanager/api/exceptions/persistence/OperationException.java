package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when a database operation (query, insert, update, delete) fails.
 * <p>
 * This exception covers SQL errors, constraint violations, and other database-level failures
 * that occur during statement execution.
 * </p>
 */
public class OperationException extends PersistenceException {
    /**
     * Constructs an OperationException with the given message.
     *
     * @param message the detail message
     */
    public OperationException(String message) {
        super(message);
    }

    /**
     * Constructs an OperationException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public OperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
