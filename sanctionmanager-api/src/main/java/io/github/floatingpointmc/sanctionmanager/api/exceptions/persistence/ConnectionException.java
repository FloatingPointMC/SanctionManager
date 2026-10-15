package io.github.floatingpointmc.sanctionmanager.api.exceptions.persistence;

import io.github.floatingpointmc.sanctionmanager.api.exceptions.PersistenceException;

/**
 * Thrown when a database connection cannot be established or maintained.
 * <p>
 * This exception indicates connectivity issues between the application and the database,
 * such as network failures, authentication problems, or the database being unavailable.
 * </p>
 */
public class ConnectionException extends PersistenceException {
    /**
     * Constructs a ConnectionException with the given message.
     *
     * @param message the detail message
     */
    public ConnectionException(String message) {
        super(message);
    }

    /**
     * Constructs a ConnectionException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
