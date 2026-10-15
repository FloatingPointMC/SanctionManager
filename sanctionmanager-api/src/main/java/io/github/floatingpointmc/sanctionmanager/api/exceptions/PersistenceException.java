package io.github.floatingpointmc.sanctionmanager.api.exceptions;

/**
 * Abstract base class for all persistence-related exceptions.
 * <p>
 * This exception hierarchy is unchecked (extends {@code RuntimeException}). All concrete persistence
 * exceptions inherit from this class.
 * </p>
 * <p>
 * Persistence exceptions indicate failures in database operations, transaction handling, connectivity,
 * schema compatibility, or configuration issues. They may wrap the original database or SQL exception
 * as a cause.
 * </p>
 */
public abstract class PersistenceException extends RuntimeException {
    /**
     * Constructs a PersistenceException with the given message.
     *
     * @param message the detail message
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * Constructs a PersistenceException with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a PersistenceException from the given cause.
     *
     * @param cause the underlying cause
     */
    public PersistenceException(Throwable cause) {
        super(cause);
    }
}
